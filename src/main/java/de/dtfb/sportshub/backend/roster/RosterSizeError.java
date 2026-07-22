package de.dtfb.sportshub.backend.roster;

/** 409 response body when addPlayer/submit is refused by the resolved rule set's roster-size bounds. */
public record RosterSizeError(String code, String message, int limit, int current) {
}
