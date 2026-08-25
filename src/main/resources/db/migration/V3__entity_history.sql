-- Generic field-change history for entities that don't duplicate a row per season (Player, Club).
-- Reconstructing "the value as of a point in time" from this log replaces the season-copy
-- approach Team still uses: see docs/14-team-player-versioning.md.

CREATE TABLE `entity_history` (
  `id` varchar(14) NOT NULL,
  `entity_type` varchar(32) NOT NULL,
  `entity_id` varchar(14) NOT NULL,
  `field_name` varchar(255) NOT NULL,
  `old_value` text,
  `new_value` text,
  `changed_at` datetime NOT NULL,
  `changed_by_dtfb_id` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `IX_entity_history_entity` (`entity_type`, `entity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
