package de.dtfb.sportshub.backend.importer;

import de.dtfb.sportshub.backend.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** One uploaded file: previewed, then applied or discarded (docs/28). Its records are {@link ImportItem}s. */
@Entity
@Table(name = "import_run")
@Getter
@Setter
public class ImportRun extends BaseEntity {

    @Column(nullable = false, length = 32)
    private String source;

    @Column(nullable = false, length = 32)
    private String instance;

    private String filename;

    /** Clubs whose organiser can't be mapped land here. References {@code federation.Federation#getId()}. */
    @Column(nullable = false, length = 14)
    private String targetFederationId;

    private Instant exportedAt;

    @Column(nullable = false)
    private int formatVersion;

    @Column(nullable = false)
    private boolean anonymized;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ImportRunStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    private String createdByDtfbId;

    private Instant finishedAt;

    private String finishedByDtfbId;
}
