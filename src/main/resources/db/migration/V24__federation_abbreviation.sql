-- SPO-125: a federation's abbreviation (e.g. TFVSH). Optional and unique (case-insensitive by the
-- column's collation). The importer maps Sports Manager organisers by it (docs/28); reusable elsewhere.
-- Additive -- no wipe.
ALTER TABLE `federation`
  ADD COLUMN `abbreviation` varchar(32) DEFAULT NULL,
  ADD UNIQUE KEY `UK_federation_abbreviation` (`abbreviation`);
UPDATE `federation` SET `abbreviation` = 'DTFB' WHERE `id` = 'fed-dtfb';
