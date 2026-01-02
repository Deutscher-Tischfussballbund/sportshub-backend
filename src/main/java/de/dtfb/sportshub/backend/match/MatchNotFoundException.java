package de.dtfb.sportshub.backend.match;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class MatchNotFoundException extends NotFoundExceptionMarker {
    public final String errorCode = "MATCH_NOT_FOUND";

    public MatchNotFoundException(String uuid) {
        super("Could not find match with uuid " + uuid);
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }
}
