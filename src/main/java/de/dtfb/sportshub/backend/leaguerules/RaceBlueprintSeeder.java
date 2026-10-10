package de.dtfb.sportshub.backend.leaguerules;

import de.dtfb.sportshub.backend.match.MatchType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Creates the two DTFB-wide Race to 42 blueprints on startup if they're missing (docs/22): the
 * Vorrunde profile (draw at 41 : 41 allowed) and the knock-out profile (two-point lead). Seven segments
 * D1, D2, D3, S1, D4, S2, D5, step 6, bye 42 : 30, 15 minutes to confirm, line-ups with at most 2
 * games / 1 single per player, 10 players, the block rule and 4 substitutions (docs/23) -- the
 * Regionalliga Damen 2026 mode. Well-known ids, so a renamed blueprint is still recognized; one that was deleted comes back
 * on the next start (archive it to hide it). Skipped when another DTFB-wide template already has the
 * name. Runs before the snapshot backfill.
 */
@Component
@Order(5)
public class RaceBlueprintSeeder implements ApplicationRunner {

    static final String RACE_42_ID = "rs-race42";
    static final String RACE_42_KO_ID = "rs-race42-ko";

    private static final Logger log = LoggerFactory.getLogger(RaceBlueprintSeeder.class);
    private static final List<MatchType> SEGMENTS = List.of(
        MatchType.DOUBLE, MatchType.DOUBLE, MatchType.DOUBLE, MatchType.SINGLE,
        MatchType.DOUBLE, MatchType.SINGLE, MatchType.DOUBLE);

    private final LeagueRuleSetRepository repository;
    private final GamePlanEntryRepository gamePlanRepository;

    public RaceBlueprintSeeder(LeagueRuleSetRepository repository, GamePlanEntryRepository gamePlanRepository) {
        this.repository = repository;
        this.gamePlanRepository = gamePlanRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed(RACE_42_ID, "Race to 42 (DTFB)", RaceEndRule.DRAW_ALLOWED);
        seed(RACE_42_KO_ID, "Race to 42 K.O. (DTFB)", RaceEndRule.TWO_POINT_LEAD);
    }

    private void seed(String id, String name, RaceEndRule endRule) {
        if (repository.existsById(id) || !repository.findTemplatesByOwnerAndName(null, name).isEmpty()) {
            return;
        }
        LeagueRuleSet ruleSet = new LeagueRuleSet();
        ruleSet.setId(id);
        ruleSet.setName(name);
        ruleSet.setSnapshot(false);
        ruleSet.setPlaySystem(PlaySystem.ROUND_ROBIN);
        ruleSet.setFixtureMode(FixtureMode.RACE);
        ruleSet.setRaceTarget(42);
        ruleSet.setRaceStep(6);
        ruleSet.setRaceEndRule(endRule);
        ruleSet.setRaceByeScoreWinner(42);
        ruleSet.setRaceByeScoreLoser(30);
        ruleSet.setConfirmationMinutes(15);
        ruleSet.setLineupRequired(true);
        ruleSet.setLineupMaxGamesPerPlayer(2);
        ruleSet.setLineupMaxSinglesPerPlayer(1);
        ruleSet.setLineupMaxPlayers(10);
        ruleSet.setLineupBlockRule(true);
        ruleSet.setMaxSubstitutions(4);
        ruleSet.setPointsWin(2);
        ruleSet.setPointsDraw(endRule == RaceEndRule.DRAW_ALLOWED ? 1 : null);
        ruleSet.setPointsLoss(0);
        ruleSet.setSchedulingMode(SchedulingMode.DAY_BATCH);
        LeagueRuleSet saved = repository.save(ruleSet);
        for (int i = 0; i < SEGMENTS.size(); i++) {
            GamePlanEntry entry = new GamePlanEntry();
            entry.setRuleSet(saved);
            entry.setPosition(i + 1);
            entry.setGameType(SEGMENTS.get(i));
            gamePlanRepository.save(entry);
        }
        log.info("Created the DTFB blueprint '{}' (docs/22)", name);
    }
}
