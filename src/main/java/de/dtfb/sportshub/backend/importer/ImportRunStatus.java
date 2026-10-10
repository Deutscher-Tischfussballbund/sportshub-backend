package de.dtfb.sportshub.backend.importer;

public enum ImportRunStatus {
    /** Planned, nothing written yet. */
    PREVIEWED,
    /** Being applied right now -- visible to everyone while the apply's own transaction runs. */
    APPLYING,
    APPLIED,
    /** Being undone right now. */
    UNDOING,
    DISCARDED,
    /** Applied, then undone: everything it wrote is reverted. */
    UNDONE
}
