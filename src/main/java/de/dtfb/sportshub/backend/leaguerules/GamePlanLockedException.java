package de.dtfb.sportshub.backend.leaguerules;

/**
 * Thrown when a rule change would alter the game plan of a league/tier in which a result has
 * already been entered -- from the first result on, the games of its fixtures are fixed (SPO-71,
 * docs/12-matchday-scheduling.md §5). Mapped to {@code 409 Conflict} ({@code GAME_PLAN_LOCKED}).
 * All other rule fields stay editable while the season runs.
 */
public class GamePlanLockedException extends RuntimeException {

    public GamePlanLockedException(String message) {
        super(message);
    }
}
