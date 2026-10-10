package de.dtfb.sportshub.backend.importer;

import de.dtfb.sportshub.backend.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Which Sports Hub entity a source record became (docs/28). The source id is only unique within one
 * source installation, hence {@code source} + {@code instance}. This is what makes imports re-runnable:
 * the next run finds the entity again here, whatever its name or number became.
 */
@Entity
@Table(name = "external_reference", uniqueConstraints = @UniqueConstraint(
    name = "UK_external_reference_source",
    columnNames = {"source", "instance", "entity_type", "external_id"}))
@Getter
@Setter
public class ExternalReference extends BaseEntity {

    @Column(nullable = false, length = 32)
    private String source;

    @Column(nullable = false, length = 32)
    private String instance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ImportRecordType entityType;

    @Column(nullable = false, length = 64)
    private String externalId;

    /** The Sports Hub id -- not a FK, the table spans several entity types (like entity_history). */
    @Column(nullable = false, length = 14)
    private String entityId;

    /** When an import last wrote or confirmed this entity; local edits after it are conflicts. */
    @Column(nullable = false)
    private Instant lastImportedAt;
}
