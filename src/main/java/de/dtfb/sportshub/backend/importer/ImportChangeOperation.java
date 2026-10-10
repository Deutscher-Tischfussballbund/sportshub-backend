package de.dtfb.sportshub.backend.importer;

/** What an apply did to one record (docs/28, undo). */
public enum ImportChangeOperation {
    CREATE,
    UPDATE,
    DELETE
}
