package de.dtfb.sportshub.backend.matchday;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Filters of {@code GET /v1/matchdays} (SPO-120), combined with AND. At least one of {@link #SCOPES} is
 * required -- there is no paging, so an unscoped request for every fixture is refused.
 */
public record MatchDayFilter(String groupId, String roundId, String leagueId, String seasonId, String teamId,
                             Instant from, Instant to, ResultState resultState) {

    public static final List<String> SCOPES = List.of("groupId", "roundId", "leagueId", "seasonId", "teamId");

    /** Fixtures are played in Germany: a date means that calendar day there. */
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    /** {@code from}/{@code to} are calendar days, both inclusive. */
    public static MatchDayFilter of(String groupId, String roundId, String leagueId, String seasonId, String teamId,
                                    LocalDate from, LocalDate to, ResultState resultState) {
        return new MatchDayFilter(groupId, roundId, leagueId, seasonId, teamId,
            from == null ? null : from.atStartOfDay(ZONE).toInstant(),
            to == null ? null : to.plusDays(1).atStartOfDay(ZONE).toInstant(),
            resultState);
    }
}
