package de.dtfb.sportshub.backend.team;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class TeamNotFoundException extends NotFoundExceptionMarker {
    public TeamNotFoundException(String id) {
        super("team", "TEAM_NOT_FOUND", id);
    }
}
