package de.dtfb.sportshub.backend.match;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Runs {@link MatchPlanService#backfill()} on startup: fixtures generated before SPO-71 get their
 * games from the game plan once. Ordered after the rule-set snapshot backfill ({@code @Order(10)}),
 * which it relies on (every league has its own rules). A no-op once every open fixture has games.
 */
@Component
@Order(20)
public class MatchPlanBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MatchPlanBackfill.class);

    private final MatchPlanService matchPlan;

    public MatchPlanBackfill(MatchPlanService matchPlan) {
        this.matchPlan = matchPlan;
    }

    @Override
    public void run(ApplicationArguments args) {
        int filled = matchPlan.backfill();
        if (filled > 0) {
            log.info("Game backfill: created the games of {} fixtures from their game plan (SPO-71)", filled);
        }
    }
}
