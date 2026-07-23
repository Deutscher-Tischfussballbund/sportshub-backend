package de.dtfb.sportshub.backend.teamparticipation;

/**
 * Whether a team's placement in a league is still active or the team has withdrawn. Distinct from
 * {@link RosterStatus}, which tracks the roster's own approval lifecycle. A withdrawal is a status
 * change, not a delete — it preserves the participation (and any {@code MatchDay}/{@code Standing}
 * history for the team in that league) instead of removing the row. Resolving already-played
 * remaining fixtures (forfeit/walkover scoring) is deferred; withdrawing only locks the roster and
 * excludes the participation from future copy-forward.
 */
public enum ParticipationStatus {
    ACTIVE,
    WITHDRAWN
}
