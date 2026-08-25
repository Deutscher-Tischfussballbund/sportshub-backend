package de.dtfb.sportshub.backend.history;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Accumulates the field-level changes an update makes to one entity, so
 * {@link EntityHistoryService#record} can persist them in one go. Avoids reflection-based
 * diffing: the caller lists exactly the fields it considers user-editable and thus
 * history-worthy.
 */
public final class ChangeSet {

    private final HistoryEntityType entityType;
    private final String entityId;
    private final List<EntityHistoryEntry> entries = new ArrayList<>();

    private ChangeSet(HistoryEntityType entityType, String entityId) {
        this.entityType = entityType;
        this.entityId = entityId;
    }

    public static ChangeSet forEntity(HistoryEntityType entityType, String entityId) {
        return new ChangeSet(entityType, entityId);
    }

    /** Records a change for {@code field}; a no-op if {@code oldValue} and {@code newValue} are equal. */
    public ChangeSet track(String field, Object oldValue, Object newValue) {
        if (Objects.equals(oldValue, newValue)) {
            return this;
        }
        EntityHistoryEntry entry = new EntityHistoryEntry();
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setFieldName(field);
        entry.setOldValue(oldValue == null ? null : oldValue.toString());
        entry.setNewValue(newValue == null ? null : newValue.toString());
        entries.add(entry);
        return this;
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    List<EntityHistoryEntry> entries() {
        return entries;
    }
}
