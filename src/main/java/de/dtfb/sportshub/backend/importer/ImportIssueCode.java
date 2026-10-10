package de.dtfb.sportshub.backend.importer;

/**
 * Why a record is rejected, conflicting or flagged (docs/28). Errors keep a record out; warnings are
 * shown but the record is written. The frontend translates the code.
 */
public enum ImportIssueCode {
    // errors
    MISSING_SOURCE_ID(true),
    MISSING_NAME(true),
    UNKNOWN_GENDER(true),
    DUPLICATE_NUMBER_IN_EXPORT(true),
    UNKNOWN_PLAYER(true),
    UNKNOWN_CLUB(true),
    BLOCKED_BY_REJECTED_RECORD(true),
    SEASON_NOT_ENDED(true),
    NO_TEAM_LEAGUES(true),
    CUP_OR_KNOCKOUT(true),
    UNKNOWN_TEAM(true),
    NO_OPPONENT(true),
    // conflicts (written as errors: nothing is applied)
    CHANGED_LOCALLY(true),
    NUMBER_BELONGS_TO_OTHER_PLAYER(true),
    // warnings
    MISSING_BIRTH_YEAR(false),
    MISSING_GENDER(false),
    IMPLAUSIBLE_BIRTH_YEAR(false),
    INVALID_NUMBER_FORMAT(false),
    NUMBER_WILL_BE_ISSUED(false),
    MATCHED_BY_NUMBER(false),
    MATCHED_MANUALLY(false),
    DUPLICATE_SUSPECT(false),
    FEDERATION_FALLBACK(false),
    // past seasons (docs/29)
    LINKED_BY_NAME(false),
    NO_GAME_PLAN(false),
    NON_INTEGER_POINTS(false),
    TABLE_DIFFERS(false),
    NOT_PLAYED(false),
    UNCONFIRMED_RESULT(false),
    UNKNOWN_LINEUP_PLAYER(false),
    NO_KICKOFF(false),
    /** The source has the team without a club (hobby/pub teams); imported without one. */
    TEAM_WITHOUT_CLUB(false);

    private final boolean error;

    ImportIssueCode(boolean error) {
        this.error = error;
    }

    public boolean isError() {
        return error;
    }
}
