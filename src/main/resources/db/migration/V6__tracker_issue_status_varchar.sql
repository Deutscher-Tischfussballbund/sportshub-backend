-- tracker_issue.status was still the original baseline's enum('APPROVED','OPEN') -- no migration
-- ever widened it when TrackerIssueStatus#DONE was added, since tests run against an
-- auto-generated schema (from the entity, always current), not this Flyway-managed one. Writing
-- 'DONE' against the real enum column fails at the database level. Switched to a plain varchar so
-- future status values never need a migration here again -- existing 'OPEN'/'APPROVED' rows are
-- already valid strings, no data transform needed.
ALTER TABLE `tracker_issue` MODIFY COLUMN `status` varchar(20) NOT NULL;
