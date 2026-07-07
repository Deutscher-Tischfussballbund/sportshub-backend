package de.dtfb.sportshub.backend.teamparticipation;

import de.dtfb.sportshub.backend.group.Group;
import de.dtfb.sportshub.backend.group.GroupNotFoundException;
import de.dtfb.sportshub.backend.group.GroupRepository;
import de.dtfb.sportshub.backend.league.League;
import de.dtfb.sportshub.backend.league.LeagueNotFoundException;
import de.dtfb.sportshub.backend.league.LeagueRepository;
import de.dtfb.sportshub.backend.team.Team;
import de.dtfb.sportshub.backend.team.TeamNotFoundException;
import de.dtfb.sportshub.backend.team.TeamRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TeamParticipationService {
    private final TeamParticipationRepository repository;
    private final TeamParticipationMapper mapper;
    private final TeamRepository teamRepository;
    private final LeagueRepository leagueRepository;
    private final GroupRepository groupRepository;

    public TeamParticipationService(TeamParticipationRepository repository, TeamParticipationMapper mapper,
                                    TeamRepository teamRepository, LeagueRepository leagueRepository,
                                    GroupRepository groupRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.teamRepository = teamRepository;
        this.leagueRepository = leagueRepository;
        this.groupRepository = groupRepository;
    }

    /** Placements, optionally narrowed to one league (preferred) or one season. */
    @Transactional(readOnly = true)
    public List<TeamParticipationDto> getAll(String seasonId, String leagueId) {
        List<TeamParticipation> participations;
        if (leagueId != null) {
            participations = repository.findVisibleByLeagueId(leagueId);
        } else if (seasonId != null) {
            participations = repository.findVisibleBySeasonId(seasonId);
        } else {
            participations = repository.findAllVisible();
        }
        return mapper.toDtoList(participations);
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
        repository.delete(getParticipation(id));
    }

    /** Resolve the team + league (required) and group (optional) referenced by the dto. */
    private void applyRelations(TeamParticipationDto dto, TeamParticipation participation) {
        participation.setTeam(getTeam(dto.getTeamId()));
        participation.setLeague(getLeague(dto.getLeagueId()));
        participation.setGroup(dto.getGroupId() == null ? null : getGroup(dto.getGroupId()));
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
