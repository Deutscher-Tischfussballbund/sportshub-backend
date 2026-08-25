package de.dtfb.sportshub.backend.teamparticipation;

import de.dtfb.sportshub.backend.federation.Federation;
import de.dtfb.sportshub.backend.group.Group;
import de.dtfb.sportshub.backend.group.GroupNotFoundException;
import de.dtfb.sportshub.backend.group.GroupRepository;
import de.dtfb.sportshub.backend.league.League;
import de.dtfb.sportshub.backend.league.LeagueNotFoundException;
import de.dtfb.sportshub.backend.league.LeagueRepository;
import de.dtfb.sportshub.backend.matchday.MatchDayRepository;
import de.dtfb.sportshub.backend.season.Season;
import de.dtfb.sportshub.backend.standing.StandingRepository;
import de.dtfb.sportshub.backend.team.Team;
import de.dtfb.sportshub.backend.team.TeamService;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class TeamParticipationService {
    private final TeamParticipationRepository repository;
    private final TeamParticipationMapper mapper;
    private final TeamService teamService;
    private final LeagueRepository leagueRepository;
    private final GroupRepository groupRepository;
    private final MatchDayRepository matchDayRepository;
    private final StandingRepository standingRepository;

    public TeamParticipationService(TeamParticipationRepository repository, TeamParticipationMapper mapper,
                                    TeamService teamService, LeagueRepository leagueRepository,
                                    GroupRepository groupRepository, MatchDayRepository matchDayRepository,
                                    StandingRepository standingRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.teamService = teamService;
        this.leagueRepository = leagueRepository;
        this.groupRepository = groupRepository;
        this.matchDayRepository = matchDayRepository;
        this.standingRepository = standingRepository;
    }

    /** Placements, optionally narrowed to one league (preferred), one season, or one team. */
    @Transactional(readOnly = true)
    public List<TeamParticipationDto> getAll(String seasonId, String leagueId, String teamId) {
        List<TeamParticipation> participations;
        if (leagueId != null) {
            participations = repository.findVisibleByLeagueId(leagueId);
        } else if (seasonId != null) {
            participations = repository.findVisibleBySeasonId(seasonId);
        } else if (teamId != null) {
            participations = repository.findVisibleByTeamId(teamId);
        } else {
            participations = repository.findAllVisible();
        }
        return mapper.toDtoList(participations);
    }

    /**
     * Rosters awaiting confirmation: SUBMITTED participations in the federation, optionally narrowed
     * to {@code restrictToLeagueIds} -- {@code null} for a region admin (every league), a (possibly
     * empty) list for a league_admin restricted to their own league(s).
     */
    @Transactional(readOnly = true)
    public List<TeamParticipationDto> getPendingApprovals(String federationId, List<String> restrictToLeagueIds) {
        List<TeamParticipation> pending =
            repository.findVisibleByFederationIdAndRosterStatus(federationId, RosterStatus.SUBMITTED);
        if (restrictToLeagueIds != null) {
            pending = pending.stream()
                .filter(p -> restrictToLeagueIds.contains(p.getLeague().getId()))
                .toList();
        }
        return mapper.toDtoList(pending);
    }

    @Transactional(readOnly = true)
    public TeamParticipationDto get(String id) {
        return mapper.toDto(repository.findVisibleById(id).orElseThrow(
            () -> new TeamParticipationNotFoundException(id)));
    }

    @Transactional
    public TeamParticipationDto create(TeamParticipationDto dto) {
        TeamParticipation participation = mapper.toEntity(dto);
        applyRelations(dto, participation);
        requireSeasonNotEnded(participation.getLeague());
        requireSingleRootLeagueTeamPerClub(participation.getLeague(), participation.getTeam());
        return mapper.toDto(repository.save(participation));
    }

    @Transactional
    public TeamParticipationDto update(String id, TeamParticipationDto dto) {
        TeamParticipation participation = getParticipation(id);
        mapper.updateEntityFromDto(dto, participation);
        applyRelations(dto, participation);
        return mapper.toDto(repository.save(participation));
    }

    @Transactional
    public void delete(String id) {
        TeamParticipation participation = getParticipation(id);
        requireNoRecordedMatches(participation);
        repository.delete(participation);
    }

    /**
     * A team drops out of a league by withdrawing, not deleting the row -- this preserves the
     * participation (and any recorded matches/standings) instead of removing it. Withdrawing locks
     * the roster ({@link de.dtfb.sportshub.backend.roster.RosterService}) and excludes the
     * participation from future copy-forward. Resolving the team's remaining scheduled fixtures
     * (forfeit/walkover scoring) is a separate, deferred concern -- withdrawal only flags the
     * participation.
     */
    @Transactional
    public TeamParticipationDto withdraw(String id) {
        TeamParticipation participation = getParticipation(id);
        if (participation.getStatus() == ParticipationStatus.WITHDRAWN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Team has already withdrawn from this league");
        }
        participation.setStatus(ParticipationStatus.WITHDRAWN);
        participation.setWithdrawnAt(Instant.now());
        return mapper.toDto(repository.save(participation));
    }

    /**
     * A participation with recorded matches or a standing in its league cannot be hard-deleted --
     * that would either fail with an unhandled FK issue (Match/Standing FK the Team directly, so
     * today it wouldn't even fail, it would just orphan the history) or silently erase the record
     * that the team was ever placed there. Withdraw instead.
     */
    private void requireNoRecordedMatches(TeamParticipation participation) {
        String leagueId = participation.getLeague().getId();
        String teamId = participation.getTeam().getId();
        boolean hasMatches = matchDayRepository.existsByLeagueIdAndTeamId(leagueId, teamId);
        boolean hasStanding = standingRepository.existsByLeagueIdAndTeamId(leagueId, teamId);
        if (hasMatches || hasStanding) {
            throw new ParticipationDeletionBlockedException(
                "Team has recorded matches or a standing in this league; withdraw instead of deleting");
        }
    }

    /**
     * Resolve the league (required), team (resolved-or-created into the league's season, since a
     * {@code teamId} may reference any historical row belonging to that team's identity -- see
     * {@link TeamService#resolveForSeason}), and group (optional) referenced by the dto.
     */
    private void applyRelations(TeamParticipationDto dto, TeamParticipation participation) {
        League league = getLeague(dto.getLeagueId());
        participation.setLeague(league);
        participation.setTeam(teamService.resolveForSeason(dto.getTeamId(), league.getSeason()));
        participation.setGroup(dto.getGroupId() == null ? null : getGroup(dto.getGroupId()));
    }

    /**
     * A team may not register for a league whose season has already ended. Only checked on
     * create (a fresh registration) — not update, which region admins use for placement edits
     * (promote/relegate) that should stay unrestricted regardless of season timing. Uses the
     * season's actual endDate rather than the registration window ({@code registrationOpen}) --
     * those are distinct concepts (a season could still be within its registration window after
     * its own endDate if an admin left it open-ended) and endDate is the authoritative signal that
     * the season itself is over.
     */
    private void requireSeasonNotEnded(League league) {
        Season season = league.getSeason();
        if (season != null && season.hasEnded()) {
            throw new SeasonEndedException(
                "Cannot register for a season that has already ended (ended " + season.getEndDate() + ")",
                season.getEndDate().toString());
        }
    }

    /**
     * A club may field only one team in a given ROOT-level league (e.g. it can't register two
     * teams in the same Bundesliga) -- regional leagues stay unrestricted, and a club may still
     * field one team each in several different root-level leagues (e.g. men's and women's
     * Bundesliga). See docs/16-root-federation.md.
     */
    private void requireSingleRootLeagueTeamPerClub(League league, Team team) {
        Federation federation = league.getSeason() == null ? null : league.getSeason().getFederation();
        if (federation == null || !federation.isRoot()) {
            return;
        }
        boolean alreadyRegistered = repository.existsByLeague_IdAndTeam_Club_IdAndTeam_IdNotAndStatusNot(
            league.getId(), team.getClub().getId(), team.getId(), ParticipationStatus.WITHDRAWN);
        if (alreadyRegistered) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "This club already has a team registered in this league");
        }
    }

    private @NonNull TeamParticipation getParticipation(String id) {
        return repository.findById(id).orElseThrow(() -> new TeamParticipationNotFoundException(id));
    }

    private @NonNull League getLeague(String leagueId) {
        return leagueRepository.findById(leagueId)
            .orElseThrow(() -> new LeagueNotFoundException(leagueId));
    }

    private @NonNull Group getGroup(String groupId) {
        return groupRepository.findById(groupId).orElseThrow(() -> new GroupNotFoundException(groupId));
    }
}
