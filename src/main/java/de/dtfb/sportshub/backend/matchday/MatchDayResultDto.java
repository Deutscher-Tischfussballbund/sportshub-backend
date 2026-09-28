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
    private Instant startDate;
    /** Where the fixture belongs -- for overviews across leagues. */
    private String federationId;
    private String leagueId;
    private String leagueName;
    private String groupId;
    private String groupName;

    private String homeTeamId;
    private String homeTeamName;
    private String awayTeamId;
    private String awayTeamName;

    /** When the home side's captain agreed to the current version; null = not (yet). */
    private Instant homeAgreedAt;
    /** When the away side's captain agreed to the current version; null = not (yet). */
    private Instant awayAgreedAt;

    private List<GameResultDto> games;
    /** How many of the fixture's games have both scores. */
    private int gamesEntered;
    private int gamesTotal;
    /**
     * The entered games decide the fixture under the rule set's matchday decision (all games, or one
     * side reached "first to N"). Only a decided result can become final.
     */
    private boolean decided;
    /** A fixture against the bye: no result to enter (docs/22). */
    private boolean bye;

    /** The rule profile the fixture is played under (docs/22); GAMES when none is set. */
    private de.dtfb.sportshub.backend.leaguerules.FixtureMode fixtureMode;
    /** RACE only: the target (42), the step per segment (6) and how the last segment ends. */
    private Integer raceTarget;
    private Integer raceStep;
    private de.dtfb.sportshub.backend.leaguerules.RaceEndRule raceEndRule;

    /** When the result became decided -- start of the time to confirm. */
    private Instant decidedAt;
    /** Until when the captains can confirm; null without a deadline. */
    private Instant confirmDeadline;
    /** The deadline has passed: only the tournament management can confirm or change it now. */
    private boolean overdue;

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
