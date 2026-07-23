package de.dtfb.sportshub.backend.teamparticipation;

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
import de.dtfb.sportshub.backend.team.TeamNotFoundException;
import de.dtfb.sportshub.backend.team.TeamRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
public class TeamParticipationService {
    private final TeamParticipationRepository repository;
    private final TeamParticipationMapper mapper;
    private final TeamRepository teamRepository;
    private final LeagueRepository leagueRepository;
    private final GroupRepository groupRepository;
    private final MatchDayRepository matchDayRepository;
    private final StandingRepository standingRepository;

    public TeamParticipationService(TeamParticipationRepository repository, TeamParticipationMapper mapper,
                                    TeamRepository teamRepository, LeagueRepository leagueRepository,
                                    GroupRepository groupRepository, MatchDayRepository matchDayRepository,
                                    StandingRepository standingRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.teamRepository = teamRepository;
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

    /** Rosters awaiting a region admin's confirmation: SUBMITTED participations in the federation. */
    @Transactional(readOnly = true)
    public List<TeamParticipationDto> getPendingApprovals(String federationId) {
        return mapper.toDtoList(
            repository.findVisibleByFederationIdAndRosterStatus(federationId, RosterStatus.SUBMITTED));
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

    /** Resolve the team + league (required) and group (optional) referenced by the dto. */
    private void applyRelations(TeamParticipationDto dto, TeamParticipation participation) {
        participation.setTeam(getTeam(dto.getTeamId()));
        participation.setLeague(getLeague(dto.getLeagueId()));
        participation.setGroup(dto.getGroupId() == null ? null : getGroup(dto.getGroupId()));
    }

    /**
     * A team may not register for a league whose season has already ended. Only checked on
     * create (a fresh registration) — not update, which region admins use for placement edits
     * (promote/relegate) that should stay unrestricted regardless of season timing. Uses the
     * season's actual endDate rather than the registrationOpen flag, which is a manually-toggled
     * setting that can go stale (still true long after the season is over).
     */
    private void requireSeasonNotEnded(League league) {
        Season season = league.getSeason();
        LocalDate endDate = season == null ? null : season.getEndDate();
        if (endDate != null && endDate.isBefore(LocalDate.now())) {
            throw new SeasonEndedException(
                "Cannot register for a season that has already ended (ended " + endDate + ")",
                endDate.toString());
        }
    }

    private @NonNull TeamParticipation getParticipation(String id) {
        return repository.findById(id).orElseThrow(() -> new TeamParticipationNotFoundException(id));
    }

    private @NonNull Team getTeam(String teamId) {
        return teamRepository.findById(teamId).orElseThrow(() -> new TeamNotFoundException(teamId));
    }

    private @NonNull League getLeague(String leagueId) {
        return leagueRepository.findById(leagueId)
            .orElseThrow(() -> new LeagueNotFoundException(leagueId));
    }

    private @NonNull Group getGroup(String groupId) {
        return groupRepository.findById(groupId).orElseThrow(() -> new GroupNotFoundException(groupId));
    }
}
