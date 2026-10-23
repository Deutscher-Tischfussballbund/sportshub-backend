package de.dtfb.sportshub.backend.matchday;

/** Where one side of a knock-out fixture gets its team from (docs/34). */
public enum FixtureSourceType {
    /** The team on a place of a group's final table, once every fixture of that group is final. */
    GROUP_PLACE,
    /** The winner of another fixture, once it is final. */
    WINNER,
    /** The loser of another fixture, once it is final. */
    LOSER
}
