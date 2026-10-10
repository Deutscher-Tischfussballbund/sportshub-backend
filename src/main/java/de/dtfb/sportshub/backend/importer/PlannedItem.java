package de.dtfb.sportshub.backend.importer;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * The planner's verdict on one source record. {@code linkId} (past seasons, docs/29): the league or team
 * identity a NEW league/team joins -- an existing identity, a {@code batch:} key shared by records of the
 * same file, or null for an identity of its own. {@link #fingerprint()} is what apply compares against the
 * stored preview: if the database changed in between so that the verdict differs, the run is stale.
 */
public record PlannedItem(ImportRecordType recordType, String externalId, String label, ImportAction action,
                          String targetEntityId, String manualMatchId, String linkId, Map<String, FieldChange> diff,
                          List<ImportIssue> issues, Object payload) {

    public PlannedItem {
        diff = new TreeMap<>(diff);
        issues = List.copyOf(issues);
    }

    /** Without a link -- every master-data record (docs/28). */
    public PlannedItem(ImportRecordType recordType, String externalId, String label, ImportAction action,
                       String targetEntityId, String manualMatchId, Map<String, FieldChange> diff,
                       List<ImportIssue> issues, Object payload) {
        this(recordType, externalId, label, action, targetEntityId, manualMatchId, null, diff, issues, payload);
    }

    public boolean hasIssue(ImportIssueCode code) {
        return issues.stream().anyMatch(issue -> issue.code() == code);
    }

    public String fingerprint() {
        return String.join("|", recordType.name(), externalId, action.name(),
            Objects.toString(targetEntityId, ""), Objects.toString(linkId, ""), diff.toString(),
            issues.stream().map(issue -> issue.code().name()).sorted().toList().toString());
    }
}
