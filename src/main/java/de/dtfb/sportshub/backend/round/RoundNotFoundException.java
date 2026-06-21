package de.dtfb.sportshub.backend.round;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class RoundNotFoundException extends NotFoundExceptionMarker {
    public RoundNotFoundException(String id) {
        super("round", "ROUND_NOT_FOUND", id);
    }
}
