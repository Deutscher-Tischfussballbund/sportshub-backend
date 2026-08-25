package de.dtfb.sportshub.backend.history;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface EntityHistoryRepository extends JpaRepository<EntityHistoryEntry, String> {

    List<EntityHistoryEntry> findByEntityTypeAndEntityIdOrderByChangedAtDesc(HistoryEntityType entityType,
                                                                              String entityId);

    List<EntityHistoryEntry> findByEntityTypeAndEntityIdAndChangedAtAfterOrderByChangedAtAsc(
        HistoryEntityType entityType, String entityId, Instant changedAt);
}
