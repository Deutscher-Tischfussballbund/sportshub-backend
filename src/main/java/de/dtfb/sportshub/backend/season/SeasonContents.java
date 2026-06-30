package de.dtfb.sportshub.backend.season;

/**
 * Counts of what hangs off a season — surfaced in the 409 body when a delete is blocked, so the
 * frontend can explain why (and how much) and steer the user to archive instead.
 */
public record SeasonContents(
    long competitions,
    long matchDays,
    long matchDaysWithResults,
    long standings
) {
    /** A season holds "real" data once any match-day has a non-OPEN result or any standing exists. */
    public boolean hasResults() {
        return matchDaysWithResults > 0 || standings > 0;
    }
}
