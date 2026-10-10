package de.dtfb.sportshub.backend.leaguerules;

/**
 * How a fixture is played and decided -- the rule set's profile (docs/22). {@code RACE}: Race to N,
 * one running score over the game plan's segments (Regionalliga, Bundesliga). {@code GAMES}: separate
 * games decided by {@code matchdayDecision} (doc 17); sets per game come later. A rule set without a
 * mode behaves as {@code GAMES}.
 */
public enum FixtureMode {
    RACE,
    GAMES
}
