package de.dtfb.sportshub.backend.match;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class MatchNotFoundException extends NotFoundExceptionMarker {
    public MatchNotFoundException(String id) {
        super("match", "MATCH_NOT_FOUND", id);
    }
}
