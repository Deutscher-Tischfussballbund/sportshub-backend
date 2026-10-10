package de.dtfb.sportshub.backend.leaguerules;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class LeagueRuleSetDto {
    private String id;
    private String name;
    /** Owning region; null = DTFB-global template. */
    private String federationId;

    /** Read-only. True = the private rule set of one league/tier; false = a blueprint (docs/21). */
    private Boolean snapshot;
    /** Blueprints: hidden from pickers when true. Null on update = unchanged. */
    private Boolean archived;
    /** Read-only, snapshots: the blueprint this snapshot was copied from. */
    private String sourceBlueprintId;
    /** Read-only, snapshots: the owner's season has ended, so the rules can no longer change. */
    private Boolean frozen;
    /** Read-only, snapshots: a result has been entered where these rules apply, so the game plan is fixed (SPO-71). */
    private Boolean gamePlanLocked;

    private PlaySystem playSystem;

    /** The rule profile (docs/22); null behaves as GAMES. */
    private FixtureMode fixtureMode;
    private Integer raceTarget;
    private Integer raceStep;
    private RaceEndRule raceEndRule;
    private Integer raceByeScoreWinner;
    private Integer raceByeScoreLoser;
    private Integer confirmationMinutes;

    /** Line-ups (docs/23): required before result entry (null = yes), and the limits checked on submit. */
    private Boolean lineupRequired;
    private Integer lineupMaxGamesPerPlayer;
    private Integer lineupMaxSinglesPerPlayer;
    private Integer lineupMaxPlayers;
    private Boolean lineupBlockRule;
    private Integer maxSubstitutions;

    private Integer pointsWin;
    private Integer pointsDraw;
    private Integer pointsLoss;

    private Integer setsPerGame;
    private Integer pointsToWinSet;

    private MatchdayDecision matchdayDecision;
    private Integer matchdayTarget;

    private Boolean sideSwitchAllowed;

    private Integer minRosterSize;
    private Integer maxRosterSize;

    private SchedulingMode schedulingMode;
    private Integer schedulingWindowDays;

    /** Ordered matchday composition; replaced wholesale on update. */
    private List<GamePlanEntryDto> gamePlan;
}
