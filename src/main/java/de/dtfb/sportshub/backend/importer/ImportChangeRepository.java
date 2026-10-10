package de.dtfb.sportshub.backend.importer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ImportChangeRepository extends JpaRepository<ImportChange, String> {

    List<ImportChange> findByRunIdOrderBySeqAsc(String runId);

    /** Applied runs after this one that wrote a record this run wrote too -- they must be undone first. */
    @Query("""
        SELECT DISTINCT c.run FROM ImportChange c
        WHERE c.run.id <> :runId AND c.run.status = de.dtfb.sportshub.backend.importer.ImportRunStatus.APPLIED
          AND c.run.finishedAt > :finishedAt
          AND c.entityId IN (SELECT o.entityId FROM ImportChange o WHERE o.run.id = :runId)""")
    List<ImportRun> laterRunsTouching(@Param("runId") String runId, @Param("finishedAt") Instant finishedAt);
}
