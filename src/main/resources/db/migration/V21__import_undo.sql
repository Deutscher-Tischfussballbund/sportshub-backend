-- docs/28 (SPO-46): undo an applied import -- every write of an apply is journaled. Additive -- no wipe.
CREATE TABLE `import_change` (
  `id` varchar(14) NOT NULL,
  `run_id` varchar(14) NOT NULL,
  `seq` int NOT NULL,
  `entity_type` varchar(64) NOT NULL,
  `entity_id` varchar(64) NOT NULL,
  `operation` varchar(16) NOT NULL,
  `field` varchar(64) DEFAULT NULL,
  `old_value` text,
  `new_value` text,
  PRIMARY KEY (`id`),
  KEY `IX_import_change_run` (`run_id`, `seq`),
  KEY `IX_import_change_entity` (`entity_id`),
  CONSTRAINT `FK_import_change_run` FOREIGN KEY (`run_id`) REFERENCES `import_run` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE `import_run`
  ADD COLUMN `undone_at` datetime(6) DEFAULT NULL,
  ADD COLUMN `undone_by_dtfb_id` varchar(255) DEFAULT NULL;
