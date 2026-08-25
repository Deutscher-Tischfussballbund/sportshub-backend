-- User/Player split + Team season-scoping (see docs/09-league-model.md, git history around the
-- CopyForwardService Team-cloning work).
--
-- `app_user` is the new permanent, season-independent Keycloak login identity (dtfb_id/email);
-- `role_assignment` now points there instead of `player` -- a role belongs to a login identity,
-- not the competitor record itself. `team` becomes season-scoped (a fresh row per season, like
-- league/tier/comp_group), linked across a team's season-copies by `team_identity_id`. `player`
-- stays a single, un-duplicated row per person -- a rename is tracked via `entity_history`
-- instead.
--
-- This migration is schema-shape only, not a data-preserving backfill: per the VPS test-deployment
-- decision (only ~7 testers, no real production history yet), the existing test data is reseeded
-- from scratch (00-bootstrap.sql + seed-region.sh) after this migration runs, not carried forward
-- row-by-row. The one exception is `team_identity_id`, backfilled from each row's own id below so
-- the NOT NULL constraint can be added safely regardless of whether the table is already empty.

-- ---------------------------------------------------------------------------
-- app_user: the new login-identity table. Not named `user` -- a reserved word in most SQL dialects.
-- ---------------------------------------------------------------------------
CREATE TABLE `app_user` (
  `id` varchar(14) NOT NULL,
  `dtfb_id` varchar(255) NOT NULL,
  `email` varchar(255) DEFAULT NULL,
  `first_name` varchar(255) DEFAULT NULL,
  `last_name` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_app_user_dtfb_id` (`dtfb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- role_assignment: player_id -> user_id, FK retargeted to app_user.
-- ---------------------------------------------------------------------------
ALTER TABLE `role_assignment`
  DROP FOREIGN KEY `FKiuagbx71ubywl5uvru90u5t8w`;

ALTER TABLE `role_assignment`
  CHANGE COLUMN `player_id` `user_id` varchar(14) NOT NULL;

ALTER TABLE `role_assignment`
  ADD CONSTRAINT `FK_role_assignment_user` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`);

-- ---------------------------------------------------------------------------
-- team: gains season_id (nullable -- a team can exist before being placed into any season) and
-- team_identity_id (not null, stable across a team's season-copies; backfilled from the row's own
-- id since every existing row is, by definition, its own only copy so far).
-- ---------------------------------------------------------------------------
ALTER TABLE `team`
  ADD COLUMN `season_id` varchar(14) DEFAULT NULL,
  ADD COLUMN `copied_from_team_id` varchar(255) DEFAULT NULL,
  ADD COLUMN `team_identity_id` varchar(255) DEFAULT NULL;

UPDATE `team` SET `team_identity_id` = `id` WHERE `team_identity_id` IS NULL;

ALTER TABLE `team`
  MODIFY COLUMN `team_identity_id` varchar(255) NOT NULL;

ALTER TABLE `team`
  ADD CONSTRAINT `FK_team_season` FOREIGN KEY (`season_id`) REFERENCES `season` (`id`);

-- ---------------------------------------------------------------------------
-- player: drop dtfb_id/email (moved to app_user), gain user_id (nullable -- a captain-entered
-- athlete may never log in themselves). Not season-scoped -- a rename is tracked via
-- entity_history instead of duplicating the row (see V3__entity_history.sql).
-- ---------------------------------------------------------------------------
ALTER TABLE `player`
  DROP INDEX `UKmudwa1n9gtkvobjk5ccc97vtl`;

ALTER TABLE `player`
  DROP COLUMN `dtfb_id`,
  DROP COLUMN `email`,
  ADD COLUMN `user_id` varchar(14) DEFAULT NULL;

ALTER TABLE `player`
  ADD CONSTRAINT `FK_player_user` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`);
