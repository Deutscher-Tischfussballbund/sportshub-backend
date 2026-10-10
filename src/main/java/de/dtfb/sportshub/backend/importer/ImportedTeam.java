package de.dtfb.sportshub.backend.importer;

/**
 * A team of one past league (docs/29). {@code identityExternalId} groups the same team across seasons
 * (SM {@code teamgruppe_id}, else its own id). {@code table} is its row of the league's final table as the
 * source had it -- frozen as the official table.
 */
public record ImportedTeam(String externalId, String leagueExternalId, String clubExternalId, String identityExternalId,
                           String name, TableRow table) {

    /** Points may be fractional in the source (penalties); {@code adjustment} is already contained in {@code points}. */
    public record TableRow(Integer place, Double points, Double adjustment, int won, int drawn, int lost,
                           int gamePointsFor, int gamePointsAgainst, int goalsFor, int goalsAgainst) {
    }
}
