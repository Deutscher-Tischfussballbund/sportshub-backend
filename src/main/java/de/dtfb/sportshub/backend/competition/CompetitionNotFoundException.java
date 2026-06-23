package de.dtfb.sportshub.backend.competition;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class CompetitionNotFoundException extends NotFoundExceptionMarker {
    public CompetitionNotFoundException(String id) {
        super("competition", "COMPETITION_NOT_FOUND", id);
    }
}
