package de.dtfb.sportshub.backend;

public abstract class NotFoundExceptionMarker extends RuntimeException {
    public NotFoundExceptionMarker(String message) {
        super(message);
    }

    public abstract String getErrorCode();
}
