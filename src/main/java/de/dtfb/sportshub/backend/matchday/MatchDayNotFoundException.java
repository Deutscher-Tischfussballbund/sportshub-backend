package de.dtfb.sportshub.backend.matchday;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class MatchDayNotFoundException extends NotFoundExceptionMarker {
    public MatchDayNotFoundException(String id) {
        super("matchday", "MATCHDAY_NOT_FOUND", id);
    }
}
