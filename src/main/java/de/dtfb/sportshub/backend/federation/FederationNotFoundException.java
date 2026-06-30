package de.dtfb.sportshub.backend.federation;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class FederationNotFoundException extends NotFoundExceptionMarker {
    public FederationNotFoundException(String id) {
        super("federation", "FEDERATION_NOT_FOUND", id);
    }
}
