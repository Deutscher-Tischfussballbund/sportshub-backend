-- Result entry by either team, confirmation by the captains (docs/17, SPO-15): the old
-- HOME_SUBMITTED state is now SUBMITTED -- a result can be entered by either side. Switched the
-- column to a plain varchar like tracker_issue.status (V6), so future states need no migration.
ALTER TABLE `match_day` MODIFY COLUMN `result_state` varchar(20) NOT NULL;
UPDATE `match_day` SET `result_state` = 'SUBMITTED' WHERE `result_state` = 'HOME_SUBMITTED';
