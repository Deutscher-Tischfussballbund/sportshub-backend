package de.dtfb.sportshub.backend.importer;

/** The run was already applied or discarded. Mapped to 409 IMPORT_RUN_CLOSED. */
public class ImportRunClosedException extends RuntimeException {
    public ImportRunClosedException(String runId) {
        super("Import run " + runId + " was already applied or discarded");
    }
}
