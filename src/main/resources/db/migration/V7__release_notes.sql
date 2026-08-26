-- Functional "what's new" feed shown to every logged-in admin-app user (shell bell button) --
-- see docs and ReleaseNoteController. Bilingual, global-admin-authored, no data to backfill.
CREATE TABLE `release_note` (
  `id` varchar(14) NOT NULL,
  `title_de` varchar(200) NOT NULL,
  `title_en` varchar(200) NOT NULL,
  `body_de` varchar(4000) NOT NULL,
  `body_en` varchar(4000) NOT NULL,
  `published_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
