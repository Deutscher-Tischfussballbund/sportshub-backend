package de.dtfb.sportshub.backend.roster;

import lombok.Getter;

/**
 * Thrown when an action would violate the resolved {@link de.dtfb.sportshub.backend.leaguerules.LeagueRuleSet}'s
 * roster-size bounds (adding a player past the max, or submitting below the min). Mapped to
 * {@code 409 Conflict} with a structured body (code + limit + current count) so the frontend can
 * show a precise, localized message instead of a generic conflict — see {@link RosterSizeError}.
 */
@Getter
public class RosterSizeException extends RuntimeException {

    /** {@code ROSTER_BELOW_MIN} or {@code ROSTER_AT_MAX}. */
    private final String code;
    private final int limit;
    private final int current;

    public RosterSizeException(String code, String message, int limit, int current) {
        super(message);
        this.code = code;
        this.limit = limit;
        this.current = current;
    }
}
