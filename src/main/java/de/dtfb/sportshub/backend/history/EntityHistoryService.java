package de.dtfb.sportshub.backend.history;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Records field-level changes to an entity and reconstructs past values from them. This is the
 * alternative to duplicating a whole row per season (see docs): a player/club stays a single row,
 * and any display that needs "the value as it was back then" reconstructs it on read via
 * {@link #fieldsAsOf} instead of reading a season-specific copy.
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

    /**
     * For each of {@code fieldNames}, the value that was in effect at {@code asOf} -- found by
     * taking the earliest recorded change *after* {@code asOf} and returning its {@code oldValue}
     * (the value immediately before that change, i.e. still in effect at {@code asOf}). A field
     * absent from the returned map has had no change since {@code asOf} -- the caller falls back
     * to the entity's current value.
     */
    @Transactional(readOnly = true)
    public Map<String, String> fieldsAsOf(HistoryEntityType entityType, String entityId, List<String> fieldNames,
                                           Instant asOf) {
        List<EntityHistoryEntry> changesAfter = repository
            .findByEntityTypeAndEntityIdAndChangedAtAfterOrderByChangedAtAsc(entityType, entityId, asOf);
        Map<String, String> result = new HashMap<>();
        for (String field : fieldNames) {
            changesAfter.stream()
                .filter(e -> e.getFieldName().equals(field))
                .findFirst()
                .ifPresent(e -> result.put(field, e.getOldValue()));
        }
        return result;
    }
}
