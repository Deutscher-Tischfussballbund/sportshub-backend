-- Federations become a general self-referencing tree (see Federation#isRoot javadoc): DTFB sits at
-- the top with no parent, every existing federation becomes its direct child. The root federation's
-- admin gets narrowly-scoped cross-federation authority over root-level teams/rosters only -- see
-- AuthorizationService and docs/16-root-federation.md.

ALTER TABLE `federation` ADD COLUMN `parent_federation_id` varchar(14) DEFAULT NULL;
ALTER TABLE `federation` ADD CONSTRAINT `fk_federation_parent`
    FOREIGN KEY (`parent_federation_id`) REFERENCES `federation` (`id`);

INSERT INTO `federation` (`id`, `name`) VALUES ('fed-dtfb', 'DTFB');
UPDATE `federation` SET `parent_federation_id` = 'fed-dtfb' WHERE `id` != 'fed-dtfb';
