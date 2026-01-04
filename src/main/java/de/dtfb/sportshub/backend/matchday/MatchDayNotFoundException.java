package de.dtfb.sportshub.backend.matchday;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class MatchDayNotFoundException extends NotFoundExceptionMarker {
    public final String errorCode = "MATCHDAY_NOT_FOUND";

    public MatchDayNotFoundException(String uuid) {
        super("Could not find matchday with uuid " + uuid);
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }
}
