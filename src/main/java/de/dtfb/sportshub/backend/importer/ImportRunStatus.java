package de.dtfb.sportshub.backend.importer;

public enum ImportRunStatus {
    /** Planned, nothing written yet. */
    PREVIEWED,
    APPLIED,
    DISCARDED,
    /** Applied, then undone: everything it wrote is reverted. */
    UNDONE
}
