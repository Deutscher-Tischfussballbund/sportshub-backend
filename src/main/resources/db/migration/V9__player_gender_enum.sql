-- player.gender was free-form text ("man" | "woman" in practice); it's now the PlayerGender enum
-- (MALE / FEMALE / DIVERSE_MEN / DIVERSE_WOMEN, stored by name). Stays a plain varchar, like
-- tracker_issue.status (V6), so adding a value never needs a column migration. Known values are
-- mapped; anything else was never a valid wire value and is cleared rather than failing the enum
-- mapping on read. Old entity_history rows keep their original "man"/"woman" text (display only).
UPDATE `player`
SET `gender` = CASE `gender`
                   WHEN 'man' THEN 'MALE'
                   WHEN 'woman' THEN 'FEMALE'
                   ELSE NULL
               END;
ALTER TABLE `player` MODIFY COLUMN `gender` varchar(20) DEFAULT NULL;
