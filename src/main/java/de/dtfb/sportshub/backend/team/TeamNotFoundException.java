package de.dtfb.sportshub.backend.team;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class TeamNotFoundException extends NotFoundExceptionMarker {
    public final String errorCode = "TEAM_NOT_FOUND";

    public TeamNotFoundException(String uuid) {
        super("Could not find team with uuid " + uuid);
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }
}
