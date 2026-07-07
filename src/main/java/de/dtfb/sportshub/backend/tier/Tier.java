package de.dtfb.sportshub.backend.tier;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.competition.Competition;
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
 * <p>The parent is currently {@code Competition} and becomes {@code League} in the rename step of
 * the migration.
 *
 * <p>{@link #ruleSet} is optional: null ⇒ inherit the league's (or federation) default — see the
 * resolution order in docs/09-league-model.md §3.
 */
@Entity
@Getter
@Setter
public class Tier extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "competition_id")
    private Competition competition;

    @ManyToOne
    @JoinColumn(name = "rule_set_id")
    private LeagueRuleSet ruleSet;

    private String name;
}
