package de.dtfb.sportshub.backend.group;

/**
 * How a group plays (docs/34): a round-robin table, or a knock-out stage whose fixtures are fed by
 * group places and earlier results instead of placed teams.
 */
public enum GroupKind {
    ROUND_ROBIN,
    KNOCKOUT
}
