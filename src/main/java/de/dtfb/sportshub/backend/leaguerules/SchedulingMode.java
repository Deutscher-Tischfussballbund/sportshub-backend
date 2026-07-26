package de.dtfb.sportshub.backend.leaguerules;

/** How generated fixtures get their real calendar date. DAY_BATCH: an admin assigns dates
 * directly, in batches, per calendar day. WINDOW: each round gets a date window and the two
 * teams of a fixture agree on the exact date within it (see docs/12-matchday-scheduling.md). */
public enum SchedulingMode {
    DAY_BATCH,
    WINDOW
}
