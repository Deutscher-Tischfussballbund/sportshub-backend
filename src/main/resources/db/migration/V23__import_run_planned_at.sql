-- SPO-102: when a preview was last planned. Open previews are planned again after another run was applied or
-- undone, so the page showing one can tell it changed. Additive -- no wipe.
ALTER TABLE `import_run` ADD COLUMN `planned_at` datetime(6) NULL;
UPDATE `import_run` SET `planned_at` = `created_at`;
