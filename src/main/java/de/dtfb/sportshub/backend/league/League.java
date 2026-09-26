package de.dtfb.sportshub.backend.league;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.category.Category;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSet;
import de.dtfb.sportshub.backend.season.Season;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

/**
 * A league: one category's ladder in a federation for a season (e.g. "Bayern Herren"), the root of
 * the {@code League -> Tier -> Group} tree and the unit copy-forward clones. Renamed from the old
 * generic {@code Competition}; a Damen league is a separate League, not a second discipline.
 *
 * <p>{@link #category} is the classification the league runs under (folded up from the dropped
 * Discipline). {@link #ruleSet} is the league's own rule set: a snapshot copied from a blueprint when
 * the league is created or copied forward, private to this league and frozen once the season has
 * ended; a tier may override it with a snapshot of its own -- see docs/21-rule-set-blueprints.md.
 */
@Entity
@Getter
@Setter
public class League extends BaseEntity {
    @ManyToOne
    @JoinColumn(name = "season_id")
    private Season season;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne
    @JoinColumn(name = "rule_set_id")
    private LeagueRuleSet ruleSet;

    private String name;

    private String importId;
}
