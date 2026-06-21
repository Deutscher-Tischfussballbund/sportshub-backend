package de.dtfb.sportshub.backend.matchset;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class MatchSetNotFoundException extends NotFoundExceptionMarker {
    public MatchSetNotFoundException(String id) {
        super("match set", "MATCHSET_NOT_FOUND", id);
    }
}
