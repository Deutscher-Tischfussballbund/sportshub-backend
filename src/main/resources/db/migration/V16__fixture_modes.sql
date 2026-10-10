-- docs/22-fixture-modes.md (SPO-58): a rule set's profile (fixture_mode) with the Race to N settings
-- and a confirmation deadline; fixtures against the bye and the moment a result became decided.
-- Additive only -- no wipe. Existing rule sets keep fixture_mode NULL, which behaves as GAMES. The
-- two DTFB Race to 42 blueprints are created by the application on startup (RaceBlueprintSeeder),
-- the same way in dev and prod.
ALTER TABLE `league_rule_set`
  ADD COLUMN `fixture_mode` varchar(20) DEFAULT NULL,
  ADD COLUMN `race_target` int DEFAULT NULL,
  ADD COLUMN `race_step` int DEFAULT NULL,
  ADD COLUMN `race_end_rule` varchar(20) DEFAULT NULL,
  ADD COLUMN `race_bye_score_winner` int DEFAULT NULL,
  ADD COLUMN `race_bye_score_loser` int DEFAULT NULL,
  ADD COLUMN `confirmation_minutes` int DEFAULT NULL;
ALTER TABLE `match_day`
  ADD COLUMN `bye` bit(1) NOT NULL DEFAULT b'0',
  ADD COLUMN `decided_at` datetime(6) DEFAULT NULL;
