-- Player: first/last name, birth year and gender become mandatory (validated in PlayerService on
-- update; players are only ever created by seed scripts, which fill all four). Every row on the VPS
-- comes from 00-bootstrap.sql's filler pool, which already sets all of them, so no backfill is
-- needed -- if this ever fails on an older database, fill the NULL rows first, don't weaken it.
ALTER TABLE `player`
  MODIFY COLUMN `first_name` varchar(255) NOT NULL,
  MODIFY COLUMN `last_name` varchar(255) NOT NULL,
  MODIFY COLUMN `birth_year` int NOT NULL,
  MODIFY COLUMN `gender` varchar(20) NOT NULL;

-- Category: name and short name mandatory, short name unique. The column's default
-- utf8mb4_0900_ai_ci collation makes the unique key case-insensitive, matching
-- CategoryService's existsByShortNameIgnoreCase check.
ALTER TABLE `category`
  MODIFY COLUMN `name` varchar(255) NOT NULL,
  MODIFY COLUMN `short_name` varchar(255) NOT NULL,
  ADD CONSTRAINT `UK_category_short_name` UNIQUE (`short_name`);
