package de.dtfb.sportshub.backend.importer;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class ImportRunNotFoundException extends NotFoundExceptionMarker {
    public ImportRunNotFoundException(String id) {
        super("import run", "IMPORT_RUN_NOT_FOUND", id);
    }
}
