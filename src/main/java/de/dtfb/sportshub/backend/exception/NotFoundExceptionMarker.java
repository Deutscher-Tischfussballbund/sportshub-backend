package de.dtfb.sportshub.backend.exception;

import lombok.Getter;

@Getter
public abstract class NotFoundExceptionMarker extends RuntimeException {

    private final String errorCode;

    protected NotFoundExceptionMarker(String entity, String errorCode, String id) {
        super("Could not find " + entity + " with id " + id);
        this.errorCode = errorCode;
    }

}
