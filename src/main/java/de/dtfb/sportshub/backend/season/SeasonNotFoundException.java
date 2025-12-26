package de.dtfb.sportshub.backend.season;

import de.dtfb.sportshub.backend.NotFoundExceptionMarker;

public class SeasonNotFoundException extends NotFoundExceptionMarker {
    public final String errorCode = "SEASON_NOT_FOUND";

    public SeasonNotFoundException(String uuid) {
        super("Could not find season with uuid " + uuid);
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }
}
