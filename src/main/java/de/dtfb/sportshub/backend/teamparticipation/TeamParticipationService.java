package de.dtfb.sportshub.backend.teamparticipation;

import de.dtfb.sportshub.backend.competition.Competition;
import de.dtfb.sportshub.backend.competition.CompetitionNotFoundException;
import de.dtfb.sportshub.backend.competition.CompetitionRepository;
import de.dtfb.sportshub.backend.pool.Pool;
import de.dtfb.sportshub.backend.pool.PoolNotFoundException;
import de.dtfb.sportshub.backend.pool.PoolRepository;
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
    private final CompetitionRepository competitionRepository;
    private final PoolRepository poolRepository;

    public TeamParticipationService(TeamParticipationRepository repository, TeamParticipationMapper mapper,
                                    TeamRepository teamRepository, CompetitionRepository competitionRepository,
                                    PoolRepository poolRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.teamRepository = teamRepository;
        this.competitionRepository = competitionRepository;
        this.poolRepository = poolRepository;
    }

    /** Placements, optionally narrowed to one competition (preferred) or one season. */
    @Transactional(readOnly = true)
    public List<TeamParticipationDto> getAll(String seasonId, String competitionId) {
        List<TeamParticipation> participations;
        if (competitionId != null) {
            participations = repository.findVisibleByCompetitionId(competitionId);
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

    /** Resolve the team + competition (required) and pool (optional) referenced by the dto. */
    private void applyRelations(TeamParticipationDto dto, TeamParticipation participation) {
        participation.setTeam(getTeam(dto.getTeamId()));
        participation.setCompetition(getCompetition(dto.getCompetitionId()));
        participation.setPool(dto.getPoolId() == null ? null : getPool(dto.getPoolId()));
    }

    private @NonNull TeamParticipation getParticipation(String id) {
        return repository.findById(id).orElseThrow(() -> new TeamParticipationNotFoundException(id));
    }

    private @NonNull Team getTeam(String teamId) {
        return teamRepository.findById(teamId).orElseThrow(() -> new TeamNotFoundException(teamId));
    }

    private @NonNull Competition getCompetition(String competitionId) {
        return competitionRepository.findById(competitionId)
            .orElseThrow(() -> new CompetitionNotFoundException(competitionId));
    }

    private @NonNull Pool getPool(String poolId) {
        return poolRepository.findById(poolId).orElseThrow(() -> new PoolNotFoundException(poolId));
    }
}
