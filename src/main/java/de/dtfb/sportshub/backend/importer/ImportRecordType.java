package de.dtfb.sportshub.backend.importer;

/** The kinds of records an {@link ImportBatch} carries. Teams, roster entries and results follow later. */
public enum ImportRecordType {
    FEDERATION,
    CLUB,
    PLAYER,
    CLUB_MEMBERSHIP
}
