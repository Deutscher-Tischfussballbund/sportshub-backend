package de.dtfb.sportshub.backend.leaguerules;

import de.dtfb.sportshub.backend.league.League;
import de.dtfb.sportshub.backend.league.LeagueRepository;
import de.dtfb.sportshub.backend.tier.Tier;
import de.dtfb.sportshub.backend.tier.TierRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The dev seed still wires leagues/tiers the pre-docs/21 way (shared rule sets, one league without
 * any), so the startup backfill has already converted it by the time a test runs -- which is exactly
 * what happens to the VPS database on its first boot with V13.
 */
@SpringBootTest
@Transactional
class RuleSetSnapshotBackfillTest {

    @Autowired
    RuleSetSnapshotService snapshots;

    @Autowired
    LeagueRepository leagueRepository;

    @Autowired
    TierRepository tierRepository;

    @Autowired
    LeagueRuleSetRepository ruleSetRepository;

    @Autowired
    GamePlanEntryRepository gamePlanRepository;

    @Test
    void everySeededLeague_hasItsOwnSnapshot_andTheSharedRowsStayBlueprints() {
        League herren = leagueRepository.findById("lg-by25-h").orElseThrow();
        League herren2026 = leagueRepository.findById("lg-2026-h").orElseThrow();
        assertThat(herren.getRuleSet().isSnapshot()).isTrue();
        assertThat(herren.getRuleSet().getId()).isNotEqualTo(herren2026.getRuleSet().getId());
        assertThat(herren.getRuleSet().getSourceBlueprint().getId()).isEqualTo("rs-by-std");
        assertThat(herren.getRuleSet().getPointsWin()).isEqualTo(3);
        assertThat(gamePlanRepository.findByRuleSetIdOrderByPositionAsc(herren.getRuleSet().getId())).hasSize(3);

        assertThat(ruleSetRepository.findById("rs-by-std").orElseThrow().isSnapshot()).isFalse();
        assertThat(leagueRepository.existsByRuleSetId("rs-by-std")).isFalse();
    }

    @Test
    void leagueWithoutRules_getsASnapshotOfItsFederationsDefault() {
        League damen = leagueRepository.findById("lg-by25-d").orElseThrow();
        assertThat(damen.getRuleSet()).isNotNull();
        assertThat(damen.getRuleSet().getSourceBlueprint().getId()).isEqualTo("rs-by-std");
    }

    @Test
    void tierOverride_becomesItsOwnSnapshot_andTiersWithoutOverrideStayWithout() {
        Tier third = tierRepository.findById("ti-by25-3").orElseThrow();
        assertThat(third.getRuleSet().isSnapshot()).isTrue();
        assertThat(third.getRuleSet().getSourceBlueprint().getId()).isEqualTo("rs-by-window");

        assertThat(tierRepository.findById("ti-by25-1").orElseThrow().getRuleSet()).isNull();
    }

    @Test
    void secondRun_isANoOp() {
        assertThat(snapshots.backfill()).isZero();
    }
}
