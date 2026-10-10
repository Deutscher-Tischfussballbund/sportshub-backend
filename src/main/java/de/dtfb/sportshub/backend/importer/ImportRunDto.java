package de.dtfb.sportshub.backend.importer;

import java.time.Instant;
import java.util.List;

public record ImportRunDto(String id, String source, String instance, String filename, String targetFederationId,
                           Instant exportedAt, boolean anonymized, ImportRunStatus status, Instant createdAt,
                           String createdByDtfbId, Instant finishedAt, String finishedByDtfbId,
                           List<ImportCountDto> counts) {
}
