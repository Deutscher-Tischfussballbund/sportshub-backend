package de.dtfb.sportshub.backend.matchevent;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class MatchEventNotFoundException extends NotFoundExceptionMarker {
    public final String errorCode = "MATCHEVENT_NOT_FOUND";

    public MatchEventNotFoundException(String uuid) {
        super("Could not find match event with uuid " + uuid);
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }
}
