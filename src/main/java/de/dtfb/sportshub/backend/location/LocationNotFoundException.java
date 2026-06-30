package de.dtfb.sportshub.backend.location;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class LocationNotFoundException extends NotFoundExceptionMarker {
    public LocationNotFoundException(String id) {
        super("location", "LOCATION_NOT_FOUND", id);
    }
}
