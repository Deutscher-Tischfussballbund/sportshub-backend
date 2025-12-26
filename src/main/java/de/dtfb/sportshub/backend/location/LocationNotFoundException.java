package de.dtfb.sportshub.backend.location;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class LocationNotFoundException extends NotFoundExceptionMarker {
    public final String errorCode = "SEASON_NOT_FOUND";

    public LocationNotFoundException(String uuid) {
        super("Could not find season with uuid " + uuid);
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }
}
