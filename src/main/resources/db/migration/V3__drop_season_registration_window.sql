-- Trim the season model: registration is now a single boolean (registration_open).
-- The explicit open/close window is dropped.
alter table season drop column registration_opens_at;
alter table season drop column registration_closes_at;
