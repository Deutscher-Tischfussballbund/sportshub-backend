package de.dtfb.sportshub.backend.season;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class SeasonNotFoundException extends NotFoundExceptionMarker {
    public SeasonNotFoundException(String id) {
        super("season", "SEASON_NOT_FOUND", id);
    }
}
