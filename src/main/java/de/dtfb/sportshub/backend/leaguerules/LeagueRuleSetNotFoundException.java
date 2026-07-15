package de.dtfb.sportshub.backend.leaguerules;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class LeagueRuleSetNotFoundException extends NotFoundExceptionMarker {
    public LeagueRuleSetNotFoundException(String id) {
        super("leagueRuleSet", "LEAGUE_RULE_SET_NOT_FOUND", id);
    }
}
