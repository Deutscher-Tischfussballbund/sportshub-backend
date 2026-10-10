-- SPO-119: planned maintenance windows, announced as a bar on top of the app; optionally read-only for
-- everyone but global admins while they run. Additive -- no wipe.
CREATE TABLE `maintenance_notice` (
  `id` varchar(14) NOT NULL,
  `message_de` varchar(500) NOT NULL,
  `message_en` varchar(500) NOT NULL,
  `starts_at` datetime(6) NOT NULL,
  `ends_at` datetime(6) NOT NULL,
  `announce_from` datetime(6) NOT NULL,
  `read_only` bit(1) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `IX_maintenance_notice_window` (`announce_from`, `ends_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
