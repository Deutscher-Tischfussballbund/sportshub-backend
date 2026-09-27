package de.dtfb.sportshub.backend.tier;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.league.League;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSet;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

/**
 * A promotion/relegation level within a league, e.g. "1. Bayernliga" / "2. Bayernliga" — the
 * first-class tier that replaces the tier-in-{@code Pool.name} of the old single tree (see
 * docs/09-league-model.md §1). Sits between the league and its groups; a tier with several groups
 * is several sibling {@code Group}s under one Tier.
 *
 * <p>The parent is the {@code League} this tier belongs to.
 *
 * <p>{@link #ruleSet} is an optional override: a snapshot private to this tier; null ⇒ the
 * league's own rules apply — see docs/21-rule-set-blueprints.md.
 */
@Entity
@Getter
@Setter
public class Tier extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "league_id")
    private League league;

    @ManyToOne
    @JoinColumn(name = "rule_set_id")
    private LeagueRuleSet ruleSet;

    private String name;

    /**
     * Ordinal ladder position within the league: lower = higher division (1 = top tier, e.g.
     * "1. Bayernliga"). Defines the promote/relegate order between tiers so it need not be parsed
     * from {@link #name} — see docs/09-league-model.md §1.
     */
    private Integer level;
}
