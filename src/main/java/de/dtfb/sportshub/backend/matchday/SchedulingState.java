package de.dtfb.sportshub.backend.matchday;

/** Whether this fixture's {@code startDate}/{@code location} are just a generated default, one
 * side's proposal, or agreed by both. Independent of {@link ResultState} — this is about *when*
 * the game is played, not its outcome. See docs/12-matchday-scheduling.md. */
public enum SchedulingState {
    DEFAULT,
    PROPOSED,
    CONFIRMED
}
