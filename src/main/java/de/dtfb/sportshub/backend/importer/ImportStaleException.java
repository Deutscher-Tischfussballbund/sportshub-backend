package de.dtfb.sportshub.backend.importer;

/** The data changed since the preview, so applying would do something else than shown. Mapped to 409 IMPORT_STALE. */
public class ImportStaleException extends RuntimeException {
    public ImportStaleException() {
        super("The data changed since the preview -- upload the file again");
    }
}
