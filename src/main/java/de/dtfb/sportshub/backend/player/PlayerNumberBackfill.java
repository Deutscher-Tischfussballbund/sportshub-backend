package de.dtfb.sportshub.backend.player;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Runs {@link PlayerNumberService#backfill()} on startup: players whose number predates the
 * {@code player_number} table (seed, older data) get it recorded once. A no-op afterwards.
 */
@Component
@Order(30)
public class PlayerNumberBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlayerNumberBackfill.class);

    private final PlayerNumberService numbers;

    public PlayerNumberBackfill(PlayerNumberService numbers) {
        this.numbers = numbers;
    }

    @Override
    public void run(ApplicationArguments args) {
        int filled = numbers.backfill();
        if (filled > 0) {
            log.info("Player number backfill: recorded the number of {} players (SPO-46)", filled);
        }
    }
}
