package de.dtfb.sportshub.backend.matchday;

import de.dtfb.sportshub.backend.match.MatchType;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/**
 * A fixture's result as the result screen needs it (docs/17): the games with their scores, which
 * side's captain has agreed, and what the current user may do.
 */
@Getter
@Setter
public class MatchDayResultDto {
    private String matchDayId;
    private ResultState resultState;

    private String homeTeamId;
    private String homeTeamName;
    private String awayTeamId;
    private String awayTeamName;

    /** When the home side's captain agreed to the current version; null = not (yet). */
    private Instant homeAgreedAt;
    /** When the away side's captain agreed to the current version; null = not (yet). */
    private Instant awayAgreedAt;

    private List<GameResultDto> games;

    /** For the current user: may enter or edit the result now. */
    private boolean canEdit;
    /** For the current user: may confirm the result now. */
    private boolean canConfirm;
    /** For the current user: acts as a neutral admin (entry and confirmation are final at once). */
    private boolean neutralAdmin;
    /** For the current user: the side they act for as a team member; null if none or ambiguous. */
    private ResultActor.Side side;

    @Getter
    @Setter
    public static class GameResultDto {
        private String matchId;
        private Integer position;
        private MatchType type;
        private Integer homeScore;
        private Integer awayScore;
    }
}
