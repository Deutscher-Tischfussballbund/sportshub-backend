package de.dtfb.sportshub.backend.importer;

import java.time.Instant;
import java.util.List;

/**
 * A fixture of a past league with its games (docs/29). Scores are the source's: {@code homeScore}/
 * {@code awayScore} the goals (or sets), {@code homeGamePoints}/{@code awayGamePoints} the games won.
 * No scores = not played; {@code unconfirmed} = the source had a result nobody confirmed.
 */
public record ImportedFixture(String externalId, String homeTeamExternalId, String awayTeamExternalId, Integer matchday,
                              String matchdayTitle, Instant kickOff, Integer homeScore, Integer awayScore,
                              Integer homeGamePoints, Integer awayGamePoints, boolean unconfirmed,
                              List<ImportedGame> games) {

    public ImportedFixture {
        games = games == null ? List.of() : List.copyOf(games);
    }

    /** Played: a result exists and isn't 0:0, which the SM uses for "not played". */
    public boolean played() {
        return homeScore != null && awayScore != null && (homeScore != 0 || awayScore != 0);
    }

    /** One game; players are source ids, null = unknown. {@code sets} as home/away pairs, may be empty. */
    public record ImportedGame(int number, String homePlayer1, String homePlayer2, String awayPlayer1,
                               String awayPlayer2, Integer homeScore, Integer awayScore, Integer homeGamePoints,
                               Integer awayGamePoints, List<int[]> sets) {

        public ImportedGame {
            sets = sets == null ? List.of() : List.copyOf(sets);
        }
    }
}
