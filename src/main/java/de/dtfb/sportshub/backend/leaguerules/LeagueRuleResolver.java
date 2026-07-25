package de.dtfb.sportshub.backend.leaguerules;

import de.dtfb.sportshub.backend.federation.Federation;
import de.dtfb.sportshub.backend.group.Group;
import de.dtfb.sportshub.backend.league.League;
import de.dtfb.sportshub.backend.season.Season;
import de.dtfb.sportshub.backend.teamparticipation.TeamParticipation;
import de.dtfb.sportshub.backend.tier.Tier;
import org.springframework.stereotype.Component;

/**
 * Resolves the {@link LeagueRuleSet} that governs a group and reads its settings with sensible
 * defaults. Resolution order (docs/09-league-model.md §3): the group's tier's own rule set, else the
 * league's default, else the owning federation's default, else the seeded {@value
 * #DTFB_STANDARD_ID} template row; {@code null} only if even that seeded row is missing.
 *
 * <p>Callers that only need a single setting use the {@code pointsX} helpers, which fall back to the
 * historical defaults (2/1/0) when no rule set resolves at all or a field is unset — an ultimate
 * defensive fallback for a pathological unseeded environment, not the primary path.
 */
@Component
public class LeagueRuleResolver {

    static final int DEFAULT_POINTS_WIN = 2;
    static final int DEFAULT_POINTS_DRAW = 1;
    static final int DEFAULT_POINTS_LOSS = 0;

    /** Well-known id of the seeded DTFB-global template rule set (access-seed.sql). */
    static final String DTFB_STANDARD_ID = "rs-dtfb-std";

    private final LeagueRuleSetRepository ruleSetRepository;

    public LeagueRuleResolver(LeagueRuleSetRepository ruleSetRepository) {
        this.ruleSetRepository = ruleSetRepository;
    }

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

    /**
     * The effective rule set for a team's participation: the group's tier chain if it's been
     * placed, otherwise the league's own rule set / federation default directly (a team can be
     * registered and building its roster before placement has run).
     */
    public LeagueRuleSet effectiveFor(TeamParticipation participation) {
        if (participation == null) {
            return null;
        }
        Group group = participation.getGroup();
        if (group != null) {
            return effectiveFor(group);
        }
        League league = participation.getLeague();
        if (league == null) {
            return null;
        }
        return league.getRuleSet() != null ? league.getRuleSet() : federationDefault(league);
    }

    /**
     * The owning federation's default rule set (via league → season → federation), falling through
     * to the seeded DTFB-global template when the federation has not configured one of its own.
     */
    private LeagueRuleSet federationDefault(League league) {
        Season season = league.getSeason();
        Federation federation = season == null ? null : season.getFederation();
        LeagueRuleSet federationDefault = federation == null ? null : federation.getDefaultRuleSet();
        if (federationDefault != null) {
            return federationDefault;
        }
        return ruleSetRepository.findById(DTFB_STANDARD_ID).orElse(null);
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
