package de.dtfb.sportshub.backend.leaguerules;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.match.MatchType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

/**
 * One slot in a {@link LeagueRuleSet}'s matchday game plan: "the game at {@link #position} is a
 * {@link #gameType}". A matchday instantiates its {@code Match} rows from these, in order. The
 * game type reuses {@link MatchType} (SINGLE/DOUBLE/GOALIE) so a plan slot and the Match it
 * produces share one vocabulary.
 */
@Entity
@Getter
@Setter
public class GamePlanEntry extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "rule_set_id")
    private LeagueRuleSet ruleSet;

    private Integer position;

    @Enumerated(EnumType.STRING)
    private MatchType gameType;
}
