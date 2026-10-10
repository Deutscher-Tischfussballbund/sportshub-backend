package de.dtfb.sportshub.backend.importer;

import java.util.List;
import java.util.Map;

public record ImportItemDto(String id, ImportRecordType recordType, String externalId, String label,
                            ImportAction action, String targetEntityId, String manualMatchId, String linkId,
                            Map<String, FieldChange> diff, List<ImportIssue> issues) {
}
