-- docs/23-lineups-and-substitutions.md (SPO-100): a line-up per fixture and team with one entry per
-- player and game; substitutions as match events (SUBSTITUTION, player_in / player_out); player_id on
-- match_event becomes a real reference; the line-up rules on the rule set. Additive -- no wipe.
CREATE TABLE `lineup` (
  `id` varchar(14) NOT NULL,
  `match_day_id` varchar(14) NOT NULL,
  `team_id` varchar(14) NOT NULL,
  `submitted_at` datetime(6) DEFAULT NULL,
  `submitted_by_dtfb_id` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_lineup_match_day_team` (`match_day_id`, `team_id`),
  CONSTRAINT `FK_lineup_match_day` FOREIGN KEY (`match_day_id`) REFERENCES `match_day` (`id`),
  CONSTRAINT `FK_lineup_team` FOREIGN KEY (`team_id`) REFERENCES `team` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `lineup_entry` (
  `id` varchar(14) NOT NULL,
  `lineup_id` varchar(14) NOT NULL,
  `match_id` varchar(14) NOT NULL,
  `slot` int NOT NULL,
  `player_id` varchar(14) NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `FK_lineup_entry_lineup` FOREIGN KEY (`lineup_id`) REFERENCES `lineup` (`id`),
  CONSTRAINT `FK_lineup_entry_match` FOREIGN KEY (`match_id`) REFERENCES `match_game` (`id`),
  CONSTRAINT `FK_lineup_entry_player` FOREIGN KEY (`player_id`) REFERENCES `player` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- match_event.player_id held free text; nothing writes it, but clear anything that isn't a player.
UPDATE `match_event` SET `player_id` = NULL WHERE `player_id` IS NOT NULL
  AND `player_id` NOT IN (SELECT `id` FROM `player`);
ALTER TABLE `match_event`
  MODIFY COLUMN `player_id` varchar(14) DEFAULT NULL,
  MODIFY COLUMN `type` varchar(20) DEFAULT NULL,
  ADD COLUMN `player_in_id` varchar(14) DEFAULT NULL,
  ADD COLUMN `player_out_id` varchar(14) DEFAULT NULL,
  ADD CONSTRAINT `FK_match_event_player` FOREIGN KEY (`player_id`) REFERENCES `player` (`id`),
  ADD CONSTRAINT `FK_match_event_player_in` FOREIGN KEY (`player_in_id`) REFERENCES `player` (`id`),
  ADD CONSTRAINT `FK_match_event_player_out` FOREIGN KEY (`player_out_id`) REFERENCES `player` (`id`);

ALTER TABLE `league_rule_set`
  ADD COLUMN `lineup_required` bit(1) DEFAULT NULL,
  ADD COLUMN `lineup_max_games_per_player` int DEFAULT NULL,
  ADD COLUMN `lineup_max_singles_per_player` int DEFAULT NULL,
  ADD COLUMN `lineup_max_players` int DEFAULT NULL,
  ADD COLUMN `lineup_block_rule` bit(1) DEFAULT NULL,
  ADD COLUMN `max_substitutions` int DEFAULT NULL;
