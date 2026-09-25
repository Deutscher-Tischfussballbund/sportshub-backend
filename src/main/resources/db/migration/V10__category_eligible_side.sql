-- A category can restrict its leagues to one side of the men's/women's split (PlayerGender's
-- league side), enforced on roster add and submit. Stored by name (MEN / WOMEN) in a plain varchar
-- like player.gender (V9). Existing categories stay open (NULL) -- no behaviour change until an
-- admin restricts one.
ALTER TABLE `category` ADD COLUMN `eligible_side` varchar(10) DEFAULT NULL;
