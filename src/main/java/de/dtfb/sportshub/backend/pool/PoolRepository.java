package de.dtfb.sportshub.backend.pool;

import de.dtfb.sportshub.backend.stage.Stage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PoolRepository extends JpaRepository<Pool, String> {
    Optional<Pool> findByStageAndName(Stage stage, String name);

    /** Pools of one stage (copy-forward source walk). */
    List<Pool> findByStageId(String stageId);

    /** All pools under a competition, across its stages/disciplines (competition structure read). */
    List<Pool> findByStage_Discipline_CompetitionId(String competitionId);

    @Query("select e from Pool e where e.stage.discipline.competition.season.archivedAt is null")
    List<Pool> findAllVisible();

    @Query("select e from Pool e where e.id = :id and e.stage.discipline.competition.season.archivedAt is null")
    Optional<Pool> findVisibleById(String id);
}
