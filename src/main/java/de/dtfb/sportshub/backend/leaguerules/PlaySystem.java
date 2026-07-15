package de.dtfb.sportshub.backend.leaguerules;

/** How a group's fixtures are generated. SWISS is retained — it is the most common mode in table
 * soccer and belongs primarily to the (parked) tournament aggregate; it is not a removal
 * candidate (see docs/09-league-model.md §3). */
public enum PlaySystem {
    ROUND_ROBIN,
    SWISS
}
