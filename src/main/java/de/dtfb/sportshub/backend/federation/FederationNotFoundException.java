package de.dtfb.sportshub.backend.federation;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class FederationNotFoundException extends NotFoundExceptionMarker {
    public final String errorCode = "EVENT_NOT_FOUND";

    public FederationNotFoundException(String uuid) {
        super("Could not find event with uuid " + uuid);
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }
}
