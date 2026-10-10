package de.dtfb.sportshub.backend.importer;

/** The run is being applied or undone right now. Mapped to 409 IMPORT_RUN_BUSY. */
public class ImportRunBusyException extends RuntimeException {
    public ImportRunBusyException(String runId) {
        super("Import run " + runId + " is being applied or undone right now");
    }
}
