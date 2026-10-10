package de.dtfb.sportshub.backend.importer;

import de.dtfb.sportshub.backend.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One write of an applied run, in order (docs/28, undo): a record created, a field changed (old and new value
 * as text; an association as the target's id), or a record deleted. Undo replays them backwards.
 */
@Entity
@Table(name = "import_change")
@Getter
@Setter
public class ImportChange extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "run_id")
    private ImportRun run;

    @Column(nullable = false)
    private int seq;

    /** The entity's simple class name, e.g. {@code Player}. */
    @Column(nullable = false, length = 64)
    private String entityType;

    @Column(nullable = false, length = 64)
    private String entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ImportChangeOperation operation;

    /** UPDATE only. */
    @Column(length = 64)
    private String field;

    @Column(columnDefinition = "TEXT")
    private String oldValue;

    @Column(columnDefinition = "TEXT")
    private String newValue;
}
