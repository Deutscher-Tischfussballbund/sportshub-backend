package de.dtfb.sportshub.backend.history;

import de.dtfb.sportshub.backend.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One recorded change of a single tracked field on a {@link HistoryEntityType} entity: the value
 * it held before ({@link #oldValue}) and after ({@link #newValue}) the change, and when it
 * happened. {@link #entityId} is a plain id, not a FK (same convention as
 * {@code Club#getCopiedFromClubId()}) -- history must survive even if the entity itself is later
 * deleted. Reused across entity types instead of one table per type so the mechanism (record a
 * change, reconstruct a value as of a point in time) is written once -- see
 * {@link EntityHistoryService}.
 */
@Entity
@Table(name = "entity_history")
@Getter
@Setter
public class EntityHistoryEntry extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HistoryEntityType entityType;

    @Column(nullable = false)
    private String entityId;

    @Column(nullable = false)
    private String fieldName;

    @Column(columnDefinition = "TEXT")
    private String oldValue;

    @Column(columnDefinition = "TEXT")
    private String newValue;

    @Column(nullable = false)
    private Instant changedAt;

    /** dtfb_id of the user who made the change, if known. */
    private String changedByDtfbId;
}
