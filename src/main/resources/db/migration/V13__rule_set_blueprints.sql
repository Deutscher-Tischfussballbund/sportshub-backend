-- SPO-28 / docs/21-rule-set-blueprints.md: a league_rule_set row is either a blueprint (a template
-- in a federation's library) or a snapshot (the private rules of exactly one league or tier
-- override, frozen once its season has ended). Existing rows all start as blueprints; the
-- application's startup backfill (RuleSetSnapshotBackfill) then gives every league/tier its own
-- snapshot copy, so the shared rows stay behind as plain blueprints. Additive only -- no wipe.
ALTER TABLE `league_rule_set`
  ADD COLUMN `snapshot` bit(1) NOT NULL DEFAULT b'0',
  ADD COLUMN `archived` bit(1) NOT NULL DEFAULT b'0',
  ADD COLUMN `source_blueprint_id` varchar(14) DEFAULT NULL,
  ADD CONSTRAINT `FK_league_rule_set_source_blueprint` FOREIGN KEY (`source_blueprint_id`)
    REFERENCES `league_rule_set` (`id`);
