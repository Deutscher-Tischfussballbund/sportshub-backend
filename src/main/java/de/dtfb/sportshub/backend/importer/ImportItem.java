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
 * One record of an {@link ImportRun} and what applying it would do. {@code payload} keeps the source
 * record (JSON) so apply can plan again without the file; {@code diff} and {@code issues} are JSON too.
 */
@Entity
@Table(name = "import_item")
@Getter
@Setter
public class ImportItem extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "run_id")
    private ImportRun run;

    /** Order within the run, as the source delivered it. */
    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ImportRecordType recordType;

    @Column(nullable = false, length = 140)
    private String externalId;

    /** A human-readable name ("Lukas Bauer (05-1234)") for the preview. */
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ImportAction action;

    /** The Sports Hub entity it matched; null for NEW and REJECTED. */
    @Column(length = 14)
    private String targetEntityId;

    /** A player the admin assigned this record to in the preview (duplicate suspicion). */
    @Column(length = 14)
    private String manualMatchId;

    /** The identity a NEW league/team joins (docs/29), see {@link PlannedItem#linkId()}. */
    @Column(length = 64)
    private String linkId;

    @Column(columnDefinition = "TEXT")
    private String diff;

    @Column(columnDefinition = "TEXT")
    private String issues;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;
}
