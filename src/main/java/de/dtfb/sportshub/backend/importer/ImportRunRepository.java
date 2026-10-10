package de.dtfb.sportshub.backend.importer;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ImportRunRepository extends JpaRepository<ImportRun, String> {

    List<ImportRun> findAllByOrderByCreatedAtDesc();

    List<ImportRun> findByStatusAndSourceAndInstanceAndIdNot(ImportRunStatus status, String source, String instance,
                                                             String id);

    /** The run with its row locked until the transaction ends -- a concurrent {@link ImportRunGate#claim} waits. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ImportRun r WHERE r.id = :id")
    Optional<ImportRun> findLocked(@Param("id") String id);
}
