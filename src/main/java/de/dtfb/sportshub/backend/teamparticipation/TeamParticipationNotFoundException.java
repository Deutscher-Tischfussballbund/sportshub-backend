package de.dtfb.sportshub.backend.teamparticipation;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class TeamParticipationNotFoundException extends NotFoundExceptionMarker {
    public TeamParticipationNotFoundException(String id) {
        super("team participation", "TEAM_PARTICIPATION_NOT_FOUND", id);
    }
}
