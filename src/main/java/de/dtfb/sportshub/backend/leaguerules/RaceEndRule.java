package de.dtfb.sportshub.backend.leaguerules;

/**
 * How the last segment of a Race to N ends (docs/22). {@code DRAW_ALLOWED}: at the target, or as a
 * draw one below it (41 : 41 -- the Vorrunde). {@code TWO_POINT_LEAD}: past the target until one side
 * leads by two (43 : 41 -- the knock-out stage).
 */
public enum RaceEndRule {
    DRAW_ALLOWED,
    TWO_POINT_LEAD
}
