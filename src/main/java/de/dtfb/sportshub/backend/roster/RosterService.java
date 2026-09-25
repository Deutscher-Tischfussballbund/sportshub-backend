package de.dtfb.sportshub.backend.roster;

import de.dtfb.sportshub.backend.category.Category;
import de.dtfb.sportshub.backend.category.CategoryEligibility;
import de.dtfb.sportshub.backend.club.Club;
import de.dtfb.sportshub.backend.clubmembership.ClubMembershipService;
import de.dtfb.sportshub.backend.history.EntityHistoryService;
import de.dtfb.sportshub.backend.history.HistoryEntityType;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleResolver;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSet;
import de.dtfb.sportshub.backend.player.Player;
import de.dtfb.sportshub.backend.player.PlayerDirectoryService;
import de.dtfb.sportshub.backend.player.PlayerDto;
import de.dtfb.sportshub.backend.player.PlayerNotFoundException;
import de.dtfb.sportshub.backend.player.PlayerRepository;
import de.dtfb.sportshub.backend.season.Season;
import de.dtfb.sportshub.backend.teamparticipation.ParticipationStatus;
import de.dtfb.sportshub.backend.teamparticipation.RosterStatus;
import de.dtfb.sportshub.backend.teamparticipation.TeamParticipation;
import de.dtfb.sportshub.backend.teamparticipation.TeamParticipationDto;
import de.dtfb.sportshub.backend.teamparticipation.TeamParticipationMapper;
import de.dtfb.sportshub.backend.teamparticipation.TeamParticipationNotFoundException;
import de.dtfb.sportshub.backend.teamparticipation.TeamParticipationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Roster management (L2): the players on a team's roster for one participation, plus the whole
 * roster's lifecycle (DRAFT → SUBMITTED → CONFIRMED). Editing is hard-gated to the DRAFT state
 * while the season's registration is open; confirm/reopen are admin lifecycle moves (authorized in
 * the controller). Most rules are hardwired settings (here: {@code Season.registrationOpen}); the
 * one exception is roster size, which comes from the resolved {@link LeagueRuleSet}
 * (min enforced on submit, max enforced on addPlayer) since it's federation/league-configurable.
 *
 * <p>The {@code registrationOpen} gate applies to a self-editing {@code team_admin} only -- an
 * admin above the team (club/region/global) may add/remove/submit regardless, e.g. to correct a
 * copy-forwarded roster before registration opens. The caller resolves this via
 * {@code @authz.canConfirmRoster} (the controller passes it in as {@code actingAsAdmin}) rather
 * than this service depending on {@code AuthorizationService} directly.
 */
@Service
public class RosterService {

    private final RosterEntryRepository rosterRepository;
    private final RosterEntryMapper rosterMapper;
    private final TeamParticipationRepository participationRepository;
    private final TeamParticipationMapper participationMapper;
    private final PlayerRepository playerRepository;
    private final PlayerDirectoryService playerDirectoryService;
    private final EntityHistoryService historyService;
    private final ClubMembershipService clubMembershipService;
    private final LeagueRuleResolver ruleResolver;
    private final CategoryEligibility categoryEligibility;

    public RosterService(RosterEntryRepository rosterRepository, RosterEntryMapper rosterMapper,
                         TeamParticipationRepository participationRepository,
                         TeamParticipationMapper participationMapper, PlayerRepository playerRepository,
                         PlayerDirectoryService playerDirectoryService, EntityHistoryService historyService,
                         ClubMembershipService clubMembershipService, LeagueRuleResolver ruleResolver,
                         CategoryEligibility categoryEligibility) {
        this.rosterRepository = rosterRepository;
        this.rosterMapper = rosterMapper;
        this.participationRepository = participationRepository;
        this.participationMapper = participationMapper;
        this.playerRepository = playerRepository;
        this.playerDirectoryService = playerDirectoryService;
        this.historyService = historyService;
        this.clubMembershipService = clubMembershipService;
        this.ruleResolver = ruleResolver;
        this.categoryEligibility = categoryEligibility;
    }

    @Transactional(readOnly = true)
    public List<RosterEntryDto> getRoster(String participationId) {
        getParticipation(participationId); // 404 if the participation is unknown
        return rosterRepository.findByParticipationIdAndRemovedAtIsNull(participationId).stream()
            .map(this::toDtoAsOfAdded)
            .toList();
    }

    /**
     * Maps a roster entry, resolving its player's display name. For an ENDED season, this freezes
     * the name as it stood when the player was added to THIS roster ({@link RosterEntry#getAddedAt()})
     * -- not today's name -- since Player is a single, un-duplicated row and a later rename must not
     * rewrite a past season's historical display. A current/future season's roster is still live, so
     * it always shows today's name instead -- otherwise a rename would incorrectly freeze the
     * in-progress season at whatever name was current when the player was added, hiding the update.
     * See {@link EntityHistoryService#fieldsAsOf}.
     */
    private RosterEntryDto toDtoAsOfAdded(RosterEntry entry) {
        RosterEntryDto dto = rosterMapper.toDto(entry);
        Player player = entry.getPlayer();
        if (seasonHasEnded(entry.getParticipation())) {
            Map<String, String> historical = historyService.fieldsAsOf(
                HistoryEntityType.PLAYER, player.getId(), List.of("firstName", "lastName"), entry.getAddedAt());
            dto.setFirstName(historical.getOrDefault("firstName", player.getFirstName()));
            dto.setLastName(historical.getOrDefault("lastName", player.getLastName()));
        } else {
            dto.setFirstName(player.getFirstName());
            dto.setLastName(player.getLastName());
        }
        return dto;
    }

    private boolean seasonHasEnded(TeamParticipation participation) {
        Season season = participation.getLeague() == null ? null : participation.getLeague().getSeason();
        return season != null && season.hasEnded();
    }

    /** Athlete search for "add player", across the whole player directory. */
    @Transactional(readOnly = true)
    public List<PlayerDto> searchPlayers(String participationId, String q) {
        getParticipation(participationId); // 404 if the participation is unknown
        return playerDirectoryService.search(q);
    }

    @Transactional
    public RosterEntryDto addPlayer(String participationId, String playerId, boolean actingAsAdmin) {
        TeamParticipation participation = getParticipation(participationId);
        requireEditable(participation, actingAsAdmin);
        Player player = playerRepository.findById(playerId)
            .orElseThrow(() -> new PlayerNotFoundException(playerId));
        requireClubMember(participation, playerId);
        requireEligible(participation, List.of(player));
        rosterRepository.findByParticipationIdAndPlayerIdAndRemovedAtIsNull(participationId, playerId)
            .ifPresent(existing -> {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Player is already on the roster");
            });
        requireUnderMax(participation);

        RosterEntry entry = new RosterEntry();
        entry.setParticipation(participation);
        entry.setPlayer(player);
        entry.setAddedAt(Instant.now());
        return rosterMapper.toDto(rosterRepository.save(entry));
    }

    @Transactional
    public void removePlayer(String participationId, String playerId, boolean actingAsAdmin) {
        TeamParticipation participation = getParticipation(participationId);
        requireEditable(participation, actingAsAdmin);
        RosterEntry entry = rosterRepository
            .findByParticipationIdAndPlayerIdAndRemovedAtIsNull(participationId, playerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player is not on the roster"));
        entry.setRemovedAt(Instant.now()); // soft-delete: keep the row for history
        rosterRepository.save(entry);
    }

    @Transactional
    public TeamParticipationDto submit(String participationId, boolean actingAsAdmin) {
        TeamParticipation participation = getParticipation(participationId);
        requireActive(participation);
        requireStatus(participation, RosterStatus.DRAFT);
        if (!actingAsAdmin) {
            requireRegistrationOpen(participation);
        }
        requireMinRosterSize(participation);
        // Re-checked on submit (no admin bypass -- eligibility is a rule, not the registration
        // window): catches copy-forwarded entries and ones added before the category was restricted.
        requireEligible(participation, rosterRepository.findByParticipationIdAndRemovedAtIsNull(participation.getId())
            .stream().map(RosterEntry::getPlayer).toList());
        return transition(participation, RosterStatus.SUBMITTED);
    }

    @Transactional
    public TeamParticipationDto confirm(String participationId) {
        TeamParticipation participation = getParticipation(participationId);
        requireActive(participation);
        requireStatus(participation, RosterStatus.SUBMITTED);
        return transition(participation, RosterStatus.CONFIRMED);
    }

    @Transactional
    public TeamParticipationDto reopen(String participationId) {
        TeamParticipation participation = getParticipation(participationId);
        requireActive(participation);
        if (participation.getRosterStatus() == RosterStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Roster is already open (DRAFT)");
        }
        return transition(participation, RosterStatus.DRAFT);
    }

    private TeamParticipationDto transition(TeamParticipation participation, RosterStatus to) {
        participation.setRosterStatus(to);
        return participationMapper.toDto(participationRepository.save(participation));
    }

    private TeamParticipation getParticipation(String participationId) {
        return participationRepository.findById(participationId)
            .orElseThrow(() -> new TeamParticipationNotFoundException(participationId));
    }

    /**
     * Roster entries may be added/removed only while the roster is DRAFT -- and, for a self-editing
     * team_admin, only while registration is open. An admin above the team may edit regardless
     * ({@code actingAsAdmin}), e.g. to fix a copy-forwarded roster before registration opens.
     */
    private void requireEditable(TeamParticipation participation, boolean actingAsAdmin) {
        requireActive(participation);
        requireStatus(participation, RosterStatus.DRAFT);
        if (!actingAsAdmin) {
            requireRegistrationOpen(participation);
        }
    }

    /**
     * A player must be an active member of the team's club before joining its roster -- club
     * membership is the precondition, team rosters are downstream of it (see ClubMembershipService).
     */
    private void requireClubMember(TeamParticipation participation, String playerId) {
        Club club = participation.getTeam() == null ? null : participation.getTeam().getClub();
        if (club == null || !clubMembershipService.isActiveMember(playerId, club.getId())) {
            throw new PlayerNotClubMemberException(
                "Player must be an active member of the team's club before joining its roster");
        }
    }

    /**
     * Every player must satisfy the league category's eligibility profile (docs/19) -- all failing
     * players are reported together, so a submit names the whole set at once.
     */
    private void requireEligible(TeamParticipation participation, List<Player> players) {
        Category category = participation.getLeague() == null ? null : participation.getLeague().getCategory();
        List<IneligiblePlayer> ineligible = players.stream()
            .flatMap(player -> categoryEligibility.check(category, player).stream()
                .map(reason -> new IneligiblePlayer(player.getId(),
                    player.getFirstName() + " " + player.getLastName(), reason)))
            .toList();
        if (!ineligible.isEmpty()) {
            throw new PlayerNotEligibleException(
                "Player(s) not eligible for this league's category: "
                    + ineligible.stream().map(IneligiblePlayer::name).toList(), ineligible);
        }
    }

    /** A withdrawn team's roster is locked -- no more edits or lifecycle transitions. */
    private void requireActive(TeamParticipation participation) {
        if (participation.getStatus() == ParticipationStatus.WITHDRAWN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Team has withdrawn from this league");
        }
    }

    private void requireStatus(TeamParticipation participation, RosterStatus expected) {
        if (participation.getRosterStatus() != expected) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Roster must be " + expected + " for this action (is " + participation.getRosterStatus() + ")");
        }
    }

    private void requireRegistrationOpen(TeamParticipation participation) {
        Season season = participation.getLeague() == null ? null : participation.getLeague().getSeason();
        if (season == null || !season.isRegistrationOpen()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Season registration is closed");
        }
    }

    /** Adding a player must not push the active roster past the resolved rule set's max, if set. */
    private void requireUnderMax(TeamParticipation participation) {
        LeagueRuleSet rules = ruleResolver.effectiveFor(participation);
        Integer max = rules == null ? null : rules.getMaxRosterSize();
        if (max == null) {
            return;
        }
        int current = activeRosterCount(participation.getId());
        if (current + 1 > max) {
            throw new RosterSizeException("ROSTER_AT_MAX",
                "Roster already has the maximum of " + max + " players", max, current);
        }
    }

    /** Submitting requires at least the resolved rule set's min active roster entries, if set. */
    private void requireMinRosterSize(TeamParticipation participation) {
        LeagueRuleSet rules = ruleResolver.effectiveFor(participation);
        Integer min = rules == null ? null : rules.getMinRosterSize();
        if (min == null) {
            return;
        }
        int current = activeRosterCount(participation.getId());
        if (current < min) {
            throw new RosterSizeException("ROSTER_BELOW_MIN",
                "Roster needs at least " + min + " players to submit (has " + current + ")", min, current);
        }
    }

    private int activeRosterCount(String participationId) {
        return rosterRepository.findByParticipationIdAndRemovedAtIsNull(participationId).size();
    }
}
