-- SPO-28: a league gets a cross-season identity, like team_identity_id (V2). Copy-forward carries it
-- to next season's copy, and LEAGUE_ADMIN grants point to it, so a league admin keeps their league
-- across the season change. Backfilled from each row's own id: existing LEAGUE_ADMIN grants hold
-- exactly that id, so they stay valid unchanged. Additive only -- no wipe.
ALTER TABLE `league`
  ADD COLUMN `league_identity_id` varchar(255) DEFAULT NULL;
UPDATE `league` SET `league_identity_id` = `id` WHERE `league_identity_id` IS NULL;
ALTER TABLE `league`
  MODIFY COLUMN `league_identity_id` varchar(255) NOT NULL;
