package de.dtfb.sportshub.backend.leaguerules;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.federation.Federation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

/**
 * A typed league rule configuration (points system, matchday game plan, set/match scoring) in one
 * of two roles (docs/21-rule-set-blueprints.md):
 * <ul>
 *   <li><b>Blueprint</b> ({@link #snapshot} = false) — a template in a federation's library, freely
 *       editable, never referenced by a {@code League}/{@code Tier} at runtime. {@link #archived}
 *       hides it from pickers.</li>
 *   <li><b>Snapshot</b> ({@link #snapshot} = true) — a private copy owned by exactly one
 *       {@code League} (or a {@code Tier} override), created from a blueprint when the owner is
 *       created or copied forward. Editable while the owner's season runs, frozen once it has
 *       ended. {@link #sourceBlueprint} records where it came from.</li>
 * </ul>
 * The game plan is held as separate {@link GamePlanEntry} rows (child→parent only, matching the
 * house convention).
 *
 * <p>Owner: {@link #federation} (region). A {@code null} federation is a DTFB-global blueprint. A
 * snapshot carries its season's federation, so the same region admins manage it.
 */
@Entity
@Getter
@Setter
public class LeagueRuleSet extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "federation_id")
    private Federation federation;

    /** True = snapshot owned by one league/tier; false = blueprint in the federation's library. */
    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean snapshot;

    /** Blueprints only: hidden from pickers and the default list view. */
    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean archived;

    /** Snapshots only: the blueprint this snapshot was copied from (null once that is deleted). */
    @ManyToOne
    @JoinColumn(name = "source_blueprint_id")
    private LeagueRuleSet sourceBlueprint;

    private String name;

    @Enumerated(EnumType.STRING)
    private PlaySystem playSystem;

    // The rule profile (docs/22): which of the scoring fields below apply. Null behaves as GAMES.
    @Enumerated(EnumType.STRING)
    private FixtureMode fixtureMode;

    // RACE: one running score over the game plan's segments; segment k ends when a side reaches
    // k * raceStep, the last one under raceEndRule at raceTarget.
    private Integer raceTarget;
    private Integer raceStep;
    @Enumerated(EnumType.STRING)
    private RaceEndRule raceEndRule;
    // Score of a fixture against the bye (a win for the team sitting out), e.g. 42 : 30.
    private Integer raceByeScoreWinner;
    private Integer raceByeScoreLoser;

    // Minutes the captains have to confirm once a result is decided; null = no deadline (docs/22).
    private Integer confirmationMinutes;

    // Line-ups (docs/23). lineupRequired null = required. The limits are checked on submit; null =
    // unlimited. lineupBlockRule: the Race profile's rule for the first block of doubles and the rest.
    private Boolean lineupRequired;
    private Integer lineupMaxGamesPerPlayer;
    private Integer lineupMaxSinglesPerPlayer;
    private Integer lineupMaxPlayers;
    private Boolean lineupBlockRule;
    private Integer maxSubstitutions;

    // Standings points. pointsDraw null ⇒ draws are not possible in this rule set.
    private Integer pointsWin;
    private Integer pointsDraw;
    private Integer pointsLoss;

    // Set/match scoring.
    private Integer setsPerGame;    // best-of-N sets in one Match (1 = single set)
    private Integer pointsToWinSet; // goals to take a set

    // Matchday completion.
    @Enumerated(EnumType.STRING)
    private MatchdayDecision matchdayDecision;
    private Integer matchdayTarget; // the N for FIRST_TO

    private Boolean sideSwitchAllowed;

    // Roster size (L2), enforced by RosterService on submit/addPlayer. Either may be null
    // (unconstrained).
    private Integer minRosterSize;
    private Integer maxRosterSize;

    // Fixture scheduling, read by FixtureGenerationService. Null schedulingMode = no scheduling
    // convention configured yet (generation requires picking one explicitly).
    @Enumerated(EnumType.STRING)
    private SchedulingMode schedulingMode;
    private Integer schedulingWindowDays; // only meaningful when schedulingMode = WINDOW
}
