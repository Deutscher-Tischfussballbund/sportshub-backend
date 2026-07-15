package de.dtfb.sportshub.backend.league;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class LeagueNotFoundException extends NotFoundExceptionMarker {
    public LeagueNotFoundException(String id) {
        super("league", "LEAGUE_NOT_FOUND", id);
    }
}
