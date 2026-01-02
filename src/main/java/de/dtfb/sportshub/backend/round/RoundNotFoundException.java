package de.dtfb.sportshub.backend.round;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class RoundNotFoundException extends NotFoundExceptionMarker {
    public final String errorCode = "ROUND_NOT_FOUND";

    public RoundNotFoundException(String uuid) {
        super("Could not find round with uuid " + uuid);
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }
}
