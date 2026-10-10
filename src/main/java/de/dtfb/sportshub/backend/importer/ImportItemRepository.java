package de.dtfb.sportshub.backend.importer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ImportItemRepository extends JpaRepository<ImportItem, String> {

    List<ImportItem> findByRunIdOrderByPosition(String runId);

    Optional<ImportItem> findByIdAndRunId(String id, String runId);

    @Query("""
        SELECT i FROM ImportItem i WHERE i.run.id = :runId
          AND (:action IS NULL OR i.action = :action)
          AND (:recordType IS NULL OR i.recordType = :recordType)
        ORDER BY i.position""")
    Page<ImportItem> search(@Param("runId") String runId, @Param("action") ImportAction action,
                            @Param("recordType") ImportRecordType recordType, Pageable pageable);

    /** Counts per record type and action -- the run's summary. Rows: [ImportRecordType, ImportAction, Long]. */
    @Query("SELECT i.recordType, i.action, COUNT(i) FROM ImportItem i WHERE i.run.id = :runId GROUP BY i.recordType, i.action")
    List<Object[]> countByTypeAndAction(@Param("runId") String runId);

    @Modifying
    @Query("DELETE FROM ImportItem i WHERE i.run.id = :runId")
    void deleteByRunId(@Param("runId") String runId);
}
