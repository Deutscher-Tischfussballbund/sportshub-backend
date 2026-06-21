package de.dtfb.sportshub.backend.matchevent;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class MatchEventNotFoundException extends NotFoundExceptionMarker {
    public MatchEventNotFoundException(String id) {
        super("match event", "MATCHEVENT_NOT_FOUND", id);
    }
}
