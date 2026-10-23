-- SPO-74 (B-2026-10-12-1): a rule-set setting "a withdrawal annuls all of the team's results". On for
-- the DTFB-wide blueprints (no federation) and the DTFB leagues' snapshots (federation fed-dtfb) -- the
-- rule of all DTFB leagues; regional rule sets keep today's behaviour until an admin switches it on.
-- Additive -- no wipe.
ALTER TABLE `league_rule_set`
  ADD COLUMN `withdrawal_annuls_results` bit(1) DEFAULT NULL;
UPDATE `league_rule_set` SET `withdrawal_annuls_results` = 1
  WHERE `federation_id` IS NULL OR `federation_id` = 'fed-dtfb';
