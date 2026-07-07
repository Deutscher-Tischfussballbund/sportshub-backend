package de.dtfb.sportshub.backend.leaguerules;

/** How a group's fixtures are generated. SWISS is carried over from the old system and will
 * likely be removed once confirmed unused in leagues (see docs/09-league-model.md §3). */
public enum PlaySystem {
    ROUND_ROBIN,
    SWISS
}
