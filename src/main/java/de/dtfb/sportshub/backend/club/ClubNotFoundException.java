package de.dtfb.sportshub.backend.club;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class ClubNotFoundException extends NotFoundExceptionMarker {
    public ClubNotFoundException(String id) {
        super("club", "CLUB_NOT_FOUND", id);
    }
}
