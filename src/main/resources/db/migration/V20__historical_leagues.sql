-- docs/29 (SPO-102): historical leagues. A group's frozen official table -- served instead of the computed one when present.
-- First source: the final tables of seasons imported from the Sports Manager. Additive -- no wipe.
CREATE TABLE `official_table_entry` (
  `id` varchar(14) NOT NULL,
  `group_id` varchar(14) NOT NULL,
  `team_id` varchar(14) NOT NULL,
  `place` int DEFAULT NULL,
  `played` int NOT NULL,
  `won` int NOT NULL,
  `drawn` int NOT NULL,
  `lost` int NOT NULL,
  `goals_for` int NOT NULL,
  `goals_against` int NOT NULL,
  `points` int NOT NULL,
  `points_adjustment` int NOT NULL,
  `source` varchar(32) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_official_table_entry_group_team` (`group_id`, `team_id`),
  CONSTRAINT `FK_official_table_entry_group` FOREIGN KEY (`group_id`) REFERENCES `comp_group` (`id`),
  CONSTRAINT `FK_official_table_entry_team` FOREIGN KEY (`team_id`) REFERENCES `team` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- The identity an imported league/team joins across seasons (docs/29).
ALTER TABLE `import_item`
  ADD COLUMN `link_id` varchar(64) DEFAULT NULL;
