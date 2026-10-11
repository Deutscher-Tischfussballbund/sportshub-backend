package de.dtfb.sportshub.backend.history;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Records field-level changes to an entity -- the alternative to duplicating a whole row per season
 * (docs/14): a player/club stays a single row. The log is for admins only; displays always show the
 * current values (B-2026-09-14-5, SPO-50 rolled back the point-in-time display of old names).
 */
@Service
public class EntityHistoryService {

    private final EntityHistoryRepository repository;
    private final EntityHistoryMapper mapper;

    public EntityHistoryService(EntityHistoryRepository repository, EntityHistoryMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /** Persists every change in {@code changes}, stamped with {@code changedByDtfbId}. No-op if empty. */
    @Transactional
    public void record(ChangeSet changes, String changedByDtfbId) {
        if (changes.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (EntityHistoryEntry entry : changes.entries()) {
            entry.setChangedAt(now);
            entry.setChangedByDtfbId(changedByDtfbId);
        }
        repository.saveAll(changes.entries());
    }

    /** The raw change log for one entity, most recent first -- for an admin-facing history view. */
    @Transactional(readOnly = true)
    public List<EntityHistoryDto> history(HistoryEntityType entityType, String entityId) {
        return mapper.toDtoList(repository.findByEntityTypeAndEntityIdOrderByChangedAtDesc(entityType, entityId));
    }
}
