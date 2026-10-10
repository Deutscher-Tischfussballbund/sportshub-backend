-- docs/28 (SPO-46): the importer. Additive -- no wipe.
-- "Complete to play, not complete to exist": old Sports Manager records may lack birth year or gender,
-- so they become optional again (partly reverses V11); roster add/submit refuses incomplete players.
ALTER TABLE `player`
  MODIFY COLUMN `birth_year` int DEFAULT NULL,
  MODIFY COLUMN `gender` varchar(20) DEFAULT NULL;

-- Player numbers, current and old (aliases). Filled from player.national_id on startup
-- (PlayerNumberBackfill).
CREATE TABLE `player_number` (
  `id` varchar(14) NOT NULL,
  `player_id` varchar(14) NOT NULL,
  `number` varchar(32) NOT NULL,
  `kind` varchar(16) NOT NULL,
  `valid_from` datetime(6) NOT NULL,
  `valid_to` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_player_number_number` (`number`),
  CONSTRAINT `FK_player_number_player` FOREIGN KEY (`player_id`) REFERENCES `player` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `player_number_sequence` (
  `prefix` varchar(8) NOT NULL,
  `next_value` bigint NOT NULL,
  PRIMARY KEY (`prefix`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Which Sports Hub entity a source record became; entity_id is not a FK (several entity types).
CREATE TABLE `external_reference` (
  `id` varchar(14) NOT NULL,
  `source` varchar(32) NOT NULL,
  `instance` varchar(32) NOT NULL,
  `entity_type` varchar(32) NOT NULL,
  `external_id` varchar(64) NOT NULL,
  `entity_id` varchar(14) NOT NULL,
  `last_imported_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_external_reference_source` (`source`, `instance`, `entity_type`, `external_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `import_run` (
  `id` varchar(14) NOT NULL,
  `source` varchar(32) NOT NULL,
  `instance` varchar(32) NOT NULL,
  `filename` varchar(255) DEFAULT NULL,
  `target_federation_id` varchar(14) NOT NULL,
  `exported_at` datetime(6) DEFAULT NULL,
  `format_version` int NOT NULL,
  `anonymized` bit(1) NOT NULL,
  `status` varchar(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by_dtfb_id` varchar(255) DEFAULT NULL,
  `finished_at` datetime(6) DEFAULT NULL,
  `finished_by_dtfb_id` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `import_item` (
  `id` varchar(14) NOT NULL,
  `run_id` varchar(14) NOT NULL,
  `position` int NOT NULL,
  `record_type` varchar(32) NOT NULL,
  `external_id` varchar(140) NOT NULL,
  `label` varchar(255) DEFAULT NULL,
  `action` varchar(16) NOT NULL,
  `target_entity_id` varchar(14) DEFAULT NULL,
  `manual_match_id` varchar(14) DEFAULT NULL,
  `diff` text,
  `issues` text,
  `payload` text NOT NULL,
  PRIMARY KEY (`id`),
  KEY `IX_import_item_run` (`run_id`, `position`),
  CONSTRAINT `FK_import_item_run` FOREIGN KEY (`run_id`) REFERENCES `import_run` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
