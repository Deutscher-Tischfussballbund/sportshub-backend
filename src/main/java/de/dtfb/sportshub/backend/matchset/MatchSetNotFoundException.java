package de.dtfb.sportshub.backend.matchset;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class MatchSetNotFoundException extends NotFoundExceptionMarker {
    public final String errorCode = "MATCHSET_NOT_FOUND";

    public MatchSetNotFoundException(String uuid) {
        super("Could not find match set with uuid " + uuid);
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }
}
