package de.dtfb.sportshub.backend.competition;

import de.dtfb.sportshub.backend.discipline.Discipline;
import de.dtfb.sportshub.backend.discipline.DisciplineRepository;
import de.dtfb.sportshub.backend.pool.Pool;
import de.dtfb.sportshub.backend.pool.PoolRepository;
import de.dtfb.sportshub.backend.stage.Stage;
import de.dtfb.sportshub.backend.stage.StageRepository;
import de.dtfb.sportshub.backend.teamparticipation.TeamParticipation;
import de.dtfb.sportshub.backend.teamparticipation.TeamParticipationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Builds the {@link CompetitionStructureDto} — a competition's Discipline→Stage→Pool subtree plus a
 * participation count per pool. Kept out of {@link CompetitionService} (which owns Competition CRUD) so
 * each service stays single-purpose. One flat query per level, assembled in memory: the result is
 * bounded to a single competition, so the walk is cheap and the query count is constant.
 */
@Service
public class CompetitionStructureService {

    private final CompetitionRepository competitionRepository;
    private final DisciplineRepository disciplineRepository;
    private final StageRepository stageRepository;
    private final PoolRepository poolRepository;
    private final TeamParticipationRepository participationRepository;

    public CompetitionStructureService(
        CompetitionRepository competitionRepository,
        DisciplineRepository disciplineRepository,
        StageRepository stageRepository,
        PoolRepository poolRepository,
        TeamParticipationRepository participationRepository
    ) {
        this.competitionRepository = competitionRepository;
        this.disciplineRepository = disciplineRepository;
        this.stageRepository = stageRepository;
        this.poolRepository = poolRepository;
        this.participationRepository = participationRepository;
    }

    @Transactional(readOnly = true)
    public CompetitionStructureDto get(String competitionId) {
        Competition competition = competitionRepository.findById(competitionId)
            .orElseThrow(() -> new CompetitionNotFoundException(competitionId));

        List<Discipline> disciplines = disciplineRepository.findByCompetitionId(competitionId);
        Map<String, List<Stage>> stagesByDiscipline = stageRepository.findByDiscipline_CompetitionId(competitionId)
            .stream().collect(Collectors.groupingBy(s -> s.getDiscipline().getId()));
        Map<String, List<Pool>> poolsByStage = poolRepository.findByStage_Discipline_CompetitionId(competitionId)
            .stream().collect(Collectors.groupingBy(p -> p.getStage().getId()));

        // Placed participations only; an unplaced one (null pool) is registered but not in any pool.
        Map<String, Long> countByPool = participationRepository.findVisibleByCompetitionId(competitionId).stream()
            .map(TeamParticipation::getPool)
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(Pool::getId, Collectors.counting()));

        List<CompetitionStructureDto.DisciplineNode> disciplineNodes = disciplines.stream()
            .map(d -> new CompetitionStructureDto.DisciplineNode(
                d.getId(),
                d.getCategory() == null ? null : d.getCategory().getId(),
                stagesByDiscipline.getOrDefault(d.getId(), List.of()).stream()
                    .map(s -> new CompetitionStructureDto.StageNode(
                        s.getId(),
                        s.getName(),
                        poolsByStage.getOrDefault(s.getId(), List.of()).stream()
                            .map(p -> new CompetitionStructureDto.PoolNode(
                                p.getId(),
                                p.getName(),
                                p.getTournamentMode(),
                                p.getPoolState(),
                                countByPool.getOrDefault(p.getId(), 0L).intValue()
                            ))
                            .toList()
                    ))
                    .toList()
            ))
            .toList();

        return new CompetitionStructureDto(competition.getId(), competition.getName(), disciplineNodes);
    }
}
