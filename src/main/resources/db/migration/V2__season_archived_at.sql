-- Soft-delete marker for seasons. Null = active; set = archived (hidden from active
-- views, all dependent data preserved). See SeasonService.archive/delete.
alter table season add column archived_at datetime(6) null;
