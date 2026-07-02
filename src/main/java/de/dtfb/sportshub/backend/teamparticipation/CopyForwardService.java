package de.dtfb.sportshub.backend.teamparticipation;

import de.dtfb.sportshub.backend.competition.Competition;
import de.dtfb.sportshub.backend.competition.CompetitionRepository;
import de.dtfb.sportshub.backend.discipline.Discipline;
import de.dtfb.sportshub.backend.discipline.DisciplineRepository;
import de.dtfb.sportshub.backend.federation.Federation;
import de.dtfb.sportshub.backend.pool.Pool;
import de.dtfb.sportshub.backend.pool.PoolRepository;
import de.dtfb.sportshub.backend.pool.PoolState;
import de.dtfb.sportshub.backend.season.Season;
import de.dtfb.sportshub.backend.season.SeasonNotFoundException;
import de.dtfb.sportshub.backend.season.SeasonRepository;
import de.dtfb.sportshub.backend.stage.Stage;
import de.dtfb.sportshub.backend.stage.StageRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Copy-forward (L1b): seeds a target season from a source season by deep-cloning the competition
 * structure (Competition → Discipline → Stage → Pool) and the team placements
 * ({@link TeamParticipation}). Cloned pools reset to {@link PoolState#PLANNED}; last season's
 * fixtures/results (Round/MatchDay/Match/Standing) are NOT carried. Each new participation records
 * its {@code copiedFromParticipationId} — the promotion/relegation audit chain the region admin then
 * edits via the placement CRUD.
 */
@Service
public class CopyForwardService {

    private final SeasonRepository seasonRepository;
    private final CompetitionRepository competitionRepository;
    private final DisciplineRepository disciplineRepository;
    private final StageRepository stageRepository;
    private final PoolRepository poolRepository;
    private final TeamParticipationRepository participationRepository;

    public CopyForwardService(SeasonRepository seasonRepository, CompetitionRepository competitionRepository,
                              DisciplineRepository disciplineRepository, StageRepository stageRepository,
                              PoolRepository poolRepository, TeamParticipationRepository participationRepository) {
        this.seasonRepository = seasonRepository;
        this.competitionRepository = competitionRepository;
        this.disciplineRepository = disciplineRepository;
        this.stageRepository = stageRepository;
        this.poolRepository = poolRepository;
        this.participationRepository = participationRepository;
    }

    @Transactional
    public CopyForwardResultDto copyForward(String targetSeasonId, String sourceSeasonId) {
        Season target = seasonRepository.findById(targetSeasonId)
            .orElseThrow(() -> new SeasonNotFoundException(targetSeasonId));
        Season source = seasonRepository.findById(sourceSeasonId)
            .orElseThrow(() -> new SeasonNotFoundException(sourceSeasonId));

        requireSameFederation(source, target);
        requireEmptyTarget(targetSeasonId);

        // old pool id -> cloned pool, so placements land in the corresponding new division
        Map<String, Pool> poolBySourceId = new HashMap<>();
        // old competition id -> cloned competition, for the participation's competition link
        Map<String, Competition> competitionBySourceId = new HashMap<>();
        int competitions = 0, disciplines = 0, stages = 0, pools = 0;

        for (Competition sourceCompetition : competitionRepository.findBySeasonId(sourceSeasonId)) {
            Competition newCompetition = new Competition();
            newCompetition.setSeason(target);
            newCompetition.setName(sourceCompetition.getName());
            newCompetition.setImportId(sourceCompetition.getImportId());
            newCompetition = competitionRepository.save(newCompetition);
            competitionBySourceId.put(sourceCompetition.getId(), newCompetition);
            competitions++;

            for (Discipline sourceDiscipline : disciplineRepository.findByCompetitionId(sourceCompetition.getId())) {
                Discipline newDiscipline = new Discipline();
                newDiscipline.setCompetition(newCompetition);
                newDiscipline.setCategory(sourceDiscipline.getCategory()); // Category is global — reused, not cloned
                newDiscipline = disciplineRepository.save(newDiscipline);
                disciplines++;

                for (Stage sourceStage : stageRepository.findByDisciplineId(sourceDiscipline.getId())) {
                    Stage newStage = new Stage();
                    newStage.setDiscipline(newDiscipline);
                    newStage.setName(sourceStage.getName());
                    newStage = stageRepository.save(newStage);
                    stages++;

                    for (Pool sourcePool : poolRepository.findByStageId(sourceStage.getId())) {
                        Pool newPool = new Pool();
                        newPool.setStage(newStage);
                        newPool.setName(sourcePool.getName());
                        newPool.setTournamentMode(sourcePool.getTournamentMode());
                        newPool.setPoolState(PoolState.PLANNED); // a fresh season starts unplayed
                        newPool = poolRepository.save(newPool);
                        poolBySourceId.put(sourcePool.getId(), newPool);
                        pools++;
                    }
                }
            }
        }

        int participations = 0;
        for (TeamParticipation source0 : participationRepository.findByCompetition_Season_Id(sourceSeasonId)) {
            Competition newCompetition = source0.getCompetition() == null
                ? null : competitionBySourceId.get(source0.getCompetition().getId());
            if (newCompetition == null) {
                continue; // participation outside the cloned tree; nothing to attach it to
            }
            TeamParticipation clone = new TeamParticipation();
            clone.setTeam(source0.getTeam());
            clone.setCompetition(newCompetition);
            clone.setPool(source0.getPool() == null ? null : poolBySourceId.get(source0.getPool().getId()));
            clone.setCopiedFromParticipationId(source0.getId());
            participationRepository.save(clone);
            participations++;
        }

        return new CopyForwardResultDto(competitions, disciplines, stages, pools, participations);
    }

    private void requireSameFederation(Season source, Season target) {
        Federation sourceRegion = source.getFederation();
        Federation targetRegion = target.getFederation();
        boolean sameRegion = sourceRegion != null && targetRegion != null
            && sourceRegion.getId().equals(targetRegion.getId());
        if (!sameRegion) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Source and target seasons must belong to the same federation");
        }
    }

    private void requireEmptyTarget(String targetSeasonId) {
        if (participationRepository.existsByCompetition_Season_Id(targetSeasonId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Target season already has participations; copy-forward only seeds an empty season");
        }
    }
}
