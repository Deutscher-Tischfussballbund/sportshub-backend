package de.dtfb.sportshub.backend.leaguerules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Runs {@link RuleSetSnapshotService#backfill()} on startup, after Flyway (prod) or the SQL seed
 * (dev) has run. Converts data from before docs/21 -- leagues/tiers sharing one rule set -- into
 * per-owner snapshots; a no-op once everything is converted. Doing this in Java rather than in a
 * Flyway migration reuses the one snapshot code path (ids, game plan copy) for prod, the dev seed and
 * the seed scripts alike.
 */
@Component
public class RuleSetSnapshotBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RuleSetSnapshotBackfill.class);

    private final RuleSetSnapshotService snapshots;

    public RuleSetSnapshotBackfill(RuleSetSnapshotService snapshots) {
        this.snapshots = snapshots;
    }

    @Override
    public void run(ApplicationArguments args) {
        int created = snapshots.backfill();
        if (created > 0) {
            log.info("Rule-set backfill: created {} league/tier snapshots (docs/21)", created);
        }
    }
}
