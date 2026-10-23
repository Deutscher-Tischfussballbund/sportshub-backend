-- SPO-99, docs/34: knock-out stages. A group is a round robin or a knock-out stage and may carry its
-- own rules override (a snapshot like tier.rule_set_id). Additive -- no wipe.
ALTER TABLE `comp_group`
  ADD COLUMN `kind` varchar(20) NOT NULL DEFAULT 'ROUND_ROBIN',
  ADD COLUMN `rule_set_id` varchar(14) DEFAULT NULL,
  ADD CONSTRAINT `FK_comp_group_rule_set` FOREIGN KEY (`rule_set_id`) REFERENCES `league_rule_set` (`id`);

-- A knock-out fixture names a source per side (a group place, or the winner/loser of another fixture);
-- its team is filled from it, unless an admin set it by hand. Winner/loser places feed the final ranking.
ALTER TABLE `match_day`
  ADD COLUMN `home_source_type` varchar(20) DEFAULT NULL,
  ADD COLUMN `home_source_group_id` varchar(14) DEFAULT NULL,
  ADD COLUMN `home_source_place` int DEFAULT NULL,
  ADD COLUMN `home_source_match_day_id` varchar(14) DEFAULT NULL,
  ADD COLUMN `away_source_type` varchar(20) DEFAULT NULL,
  ADD COLUMN `away_source_group_id` varchar(14) DEFAULT NULL,
  ADD COLUMN `away_source_place` int DEFAULT NULL,
  ADD COLUMN `away_source_match_day_id` varchar(14) DEFAULT NULL,
  ADD COLUMN `home_set_by_hand` bit(1) NOT NULL DEFAULT b'0',
  ADD COLUMN `away_set_by_hand` bit(1) NOT NULL DEFAULT b'0',
  ADD COLUMN `winner_place` int DEFAULT NULL,
  ADD COLUMN `loser_place` int DEFAULT NULL,
  ADD CONSTRAINT `FK_match_day_home_source_group` FOREIGN KEY (`home_source_group_id`) REFERENCES `comp_group` (`id`),
  ADD CONSTRAINT `FK_match_day_home_source_match_day` FOREIGN KEY (`home_source_match_day_id`) REFERENCES `match_day` (`id`),
  ADD CONSTRAINT `FK_match_day_away_source_group` FOREIGN KEY (`away_source_group_id`) REFERENCES `comp_group` (`id`),
  ADD CONSTRAINT `FK_match_day_away_source_match_day` FOREIGN KEY (`away_source_match_day_id`) REFERENCES `match_day` (`id`);
