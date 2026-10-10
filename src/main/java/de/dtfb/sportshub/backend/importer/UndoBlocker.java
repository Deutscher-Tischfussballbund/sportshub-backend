package de.dtfb.sportshub.backend.importer;

/**
 * Why a run can't be undone (docs/28). {@code entityType}/{@code entityId} name the record concerned;
 * {@code detail}: the field changed since, the record type using it, or the later run.
 */
public record UndoBlocker(Code code, String entityType, String entityId, String label, String detail) {

    public enum Code {
        /** Applied before runs were journaled. */
        NO_JOURNAL,
        /** A later applied run wrote the same records -- undo that one first. */
        LATER_RUN,
        /** A field the run changed was changed again since (or the record is gone). */
        CHANGED_SINCE,
        /** A record the run created is used by data from outside the run. */
        IN_USE,
        /** The run replaced data (e.g. a fixture's games on a re-run); that can't be restored. */
        REPLACED_DATA
    }
}
