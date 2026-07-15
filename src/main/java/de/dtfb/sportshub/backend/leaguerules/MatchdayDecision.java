package de.dtfb.sportshub.backend.leaguerules;

/** When a matchday is decided: once all planned games are played, or once a team reaches a
 * target number of game wins ({@code matchdayTarget}). */
public enum MatchdayDecision {
    ALL_GAMES,
    FIRST_TO
}
