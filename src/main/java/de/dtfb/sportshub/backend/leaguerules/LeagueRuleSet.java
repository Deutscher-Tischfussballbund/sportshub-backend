package de.dtfb.sportshub.backend.leaguerules;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.federation.Federation;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

/**
 * A reusable, typed league rule configuration (points system, matchday game plan, set/match
 * scoring). The SAME rule set may be referenced by several {@code Tier}s / {@code League}s; a tier
 * may (but need not) have its own — see docs/09-league-model.md §3. The game plan is held as
 * separate {@link GamePlanEntry} rows (child→parent only, matching the house convention).
 *
 * <p>Owner: {@link #federation} (region). A {@code null} federation is a DTFB-global template.
 *
 * <p>The field set is deliberately incomplete and grows as rules are added; enforcement of these
 * settings (standings points, matchday validation) lands in a later phase.
 */
@Entity
@Getter
@Setter
public class LeagueRuleSet extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "federation_id")
    private Federation federation;

    private String name;

    @Enumerated(EnumType.STRING)
    private PlaySystem playSystem;

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
}
