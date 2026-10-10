package de.dtfb.sportshub.backend.importer;

/** The uploaded file isn't in the chosen source's format. Mapped to 400. */
public class ImportFormatException extends RuntimeException {

    public ImportFormatException(String message) {
        super(message);
    }

    public ImportFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
