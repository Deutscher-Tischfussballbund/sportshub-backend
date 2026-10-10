package de.dtfb.sportshub.backend.importer;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * The planner's verdict on one source record. {@link #fingerprint()} is what apply compares against the
 * stored preview: if the database changed in between so that the verdict differs, the run is stale.
 */
public record PlannedItem(ImportRecordType recordType, String externalId, String label, ImportAction action,
                          String targetEntityId, String manualMatchId, Map<String, FieldChange> diff,
                          List<ImportIssue> issues, Object payload) {

    public PlannedItem {
        diff = new TreeMap<>(diff);
        issues = List.copyOf(issues);
    }

    public boolean hasIssue(ImportIssueCode code) {
        return issues.stream().anyMatch(issue -> issue.code() == code);
    }

    public String fingerprint() {
        return String.join("|", recordType.name(), externalId, action.name(),
            Objects.toString(targetEntityId, ""), diff.toString(),
            issues.stream().map(issue -> issue.code().name()).sorted().toList().toString());
    }
}
