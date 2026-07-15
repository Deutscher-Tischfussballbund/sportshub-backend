package de.dtfb.sportshub.backend.leaguerules;

import de.dtfb.sportshub.backend.federation.Federation;
import de.dtfb.sportshub.backend.group.Group;
import de.dtfb.sportshub.backend.league.League;
import de.dtfb.sportshub.backend.season.Season;
import de.dtfb.sportshub.backend.tier.Tier;
import org.springframework.stereotype.Component;

/**
 * Resolves the {@link LeagueRuleSet} that governs a group and reads its settings with sensible
 * defaults. Resolution order (docs/09-league-model.md §3): the group's tier's own rule set, else the
 * league's default, else the owning federation's default; {@code null} if none is set anywhere up
 * the chain.
 *
 * <p>Callers that only need a single setting use the {@code pointsX} helpers, which fall back to the
 * historical defaults (2/1/0) when no rule set resolves or a field is unset — so behaviour is
 * unchanged for leagues that have not configured a rule set.
 */
@Component
public class LeagueRuleResolver {

    static final int DEFAULT_POINTS_WIN = 2;
    static final int DEFAULT_POINTS_DRAW = 1;
    static final int DEFAULT_POINTS_LOSS = 0;

    /** The effective rule set for the group, or {@code null} if none is configured up the chain. */
    public LeagueRuleSet effectiveFor(Group group) {
        if (group == null) {
            return null;
        }
        Tier tier = group.getTier();
        if (tier == null) {
            return null;
        }
        if (tier.getRuleSet() != null) {
            return tier.getRuleSet();
        }
        League league = tier.getLeague();
        if (league == null) {
            return null;
        }
        if (league.getRuleSet() != null) {
            return league.getRuleSet();
        }
        return federationDefault(league);
    }

    /** The owning federation's default rule set (via league → season → federation), or null. */
    private LeagueRuleSet federationDefault(League league) {
        Season season = league.getSeason();
        if (season == null) {
            return null;
        }
        Federation federation = season.getFederation();
        return federation == null ? null : federation.getDefaultRuleSet();
    }

    public int pointsWin(LeagueRuleSet rules) {
        return rules != null && rules.getPointsWin() != null ? rules.getPointsWin() : DEFAULT_POINTS_WIN;
    }

    public int pointsDraw(LeagueRuleSet rules) {
        return rules != null && rules.getPointsDraw() != null ? rules.getPointsDraw() : DEFAULT_POINTS_DRAW;
    }

    public int pointsLoss(LeagueRuleSet rules) {
        return rules != null && rules.getPointsLoss() != null ? rules.getPointsLoss() : DEFAULT_POINTS_LOSS;
    }
}
