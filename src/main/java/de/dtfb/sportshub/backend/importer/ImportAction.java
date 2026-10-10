package de.dtfb.sportshub.backend.importer;

/** What applying a run would do with one record. */
public enum ImportAction {
    NEW,
    UPDATE,
    UNCHANGED,
    /** Changed in the Sports Hub since the last import, or its number belongs to someone else -- not written. */
    CONFLICT,
    /** Unusable (an error, or blocked by a rejected record it depends on) -- not written. */
    REJECTED
}
