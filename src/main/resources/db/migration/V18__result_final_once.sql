-- docs/17 (SPO-15): once a result has been final, it stays with the neutral admins (league,
-- federation, global) -- even when an admin's correction makes it undecided again, the teams can no
-- longer enter or confirm. Additive only -- no wipe. Results that are final today count as final
-- since they became decided (or now, for older ones without that moment).
ALTER TABLE `match_day`
  ADD COLUMN `first_final_at` datetime(6) DEFAULT NULL;
UPDATE `match_day` SET `first_final_at` = COALESCE(`decided_at`, CURRENT_TIMESTAMP(6))
  WHERE `result_state` = 'CONFIRMED';
