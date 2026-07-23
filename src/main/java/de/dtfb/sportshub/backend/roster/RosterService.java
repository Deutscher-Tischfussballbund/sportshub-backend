package de.dtfb.sportshub.backend.roster;

import de.dtfb.sportshub.backend.leaguerules.LeagueRuleResolver;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSet;
import de.dtfb.sportshub.backend.player.Player;
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

/**
 * Roster management (L2): the players on a team's roster for one participation, plus the whole
 * roster's lifecycle (DRAFT → SUBMITTED → CONFIRMED). Editing is a hard-gated to the DRAFT state
 * while the season's registration is open; confirm/reopen are admin lifecycle moves (authorized in
 * the controller). Most rules are hardwired settings (here: {@code Season.registrationOpen}); the
 * one exception is roster size, which comes from the resolved {@link LeagueRuleSet}
 * (min enforced on submit, max enforced on addPlayer) since it's federation/league-configurable.
 */
@Service
public class RosterService {

    private final RosterEntryRepository rosterRepository;
    private final RosterEntryMapper rosterMapper;
    private final TeamParticipationRepository participationRepository;
    private final TeamParticipationMapper participationMapper;
    private final PlayerRepository playerRepository;
    private final LeagueRuleResolver ruleResolver;

    public RosterService(RosterEntryRepository rosterRepository, RosterEntryMapper rosterMapper,
                         TeamParticipationRepository participationRepository,
                         TeamParticipationMapper participationMapper, PlayerRepository playerRepository,
                         LeagueRuleResolver ruleResolver) {
        this.rosterRepository = rosterRepository;
        this.rosterMapper = rosterMapper;
        this.participationRepository = participationRepository;
        this.participationMapper = participationMapper;
        this.playerRepository = playerRepository;
        this.ruleResolver = ruleResolver;
    }

    @Transactional(readOnly = true)
    public List<RosterEntryDto> getRoster(String participationId) {
        getParticipation(participationId); // 404 if the participation is unknown
        return rosterMapper.toDtoList(rosterRepository.findByParticipationIdAndRemovedAtIsNull(participationId));
    }

    @Transactional
    public RosterEntryDto addPlayer(String participationId, String playerId) {
        TeamParticipation participation = getParticipation(participationId);
        requireEditable(participation);
        Player player = playerRepository.findById(playerId)
            .orElseThrow(() -> new PlayerNotFoundException(playerId));
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
    public void removePlayer(String participationId, String playerId) {
        TeamParticipation participation = getParticipation(participationId);
        requireEditable(participation);
        RosterEntry entry = rosterRepository
            .findByParticipationIdAndPlayerIdAndRemovedAtIsNull(participationId, playerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player is not on the roster"));
        entry.setRemovedAt(Instant.now()); // soft-delete: keep the row for history
        rosterRepository.save(entry);
    }

    @Transactional
    public TeamParticipationDto submit(String participationId) {
        TeamParticipation participation = getParticipation(participationId);
        requireActive(participation);
        requireStatus(participation, RosterStatus.DRAFT);
        requireRegistrationOpen(participation);
        requireMinRosterSize(participation);
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

    /** Roster entries may be added/removed only while the roster is DRAFT and registration is open. */
    private void requireEditable(TeamParticipation participation) {
        requireActive(participation);
        requireStatus(participation, RosterStatus.DRAFT);
        requireRegistrationOpen(participation);
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
