package de.dtfb.sportshub.backend.matchday;

/**
 * Result lifecycle of a fixture (docs/17): no result yet, a result entered and waiting for the
 * captains' agreement, final. Which side has agreed is on {@link MatchDay#getHomeConfirmedAt()} /
 * {@link MatchDay#getAwayConfirmedAt()}.
 */
public enum ResultState {
    OPEN,
    SUBMITTED,
    CONFIRMED
}
