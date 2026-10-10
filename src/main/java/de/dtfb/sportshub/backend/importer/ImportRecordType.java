package de.dtfb.sportshub.backend.importer;

/**
 * The kinds of records an {@link ImportBatch} carries: master data (docs/28) and past seasons (docs/29).
 * {@code LEAGUE_IDENTITY} and {@code TEAM_IDENTITY} are no records of their own -- they only key the
 * {@link ExternalReference}s that keep a league or team the same across imported seasons.
 */
public enum ImportRecordType {
    FEDERATION,
    CLUB,
    PLAYER,
    CLUB_MEMBERSHIP,
    SEASON,
    LEAGUE,
    TEAM,
    ROSTER_ENTRY,
    FIXTURE,
    LEAGUE_IDENTITY,
    TEAM_IDENTITY
}
