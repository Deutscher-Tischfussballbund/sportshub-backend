-- noinspection SqlResolveForFile

-- Dev seed for the access domain: German Landesverbände, demo clubs, and a bootstrap
-- global admin so the first login is not stuck at /no-access.
--
-- Loaded on boot via spring.sql.init.data-locations (dev profile only), after Hibernate
-- creates the schema (defer-datasource-initialization). The dev datasource is H2 with
-- ddl-auto=create-drop, so the schema is fresh every boot and plain INSERTs are safe.
-- IDs are fixed here because the nano-id generator only runs for JPA-persisted entities.
-- NB: the id column is VARCHAR(14) (nano-id width), so fixed ids must stay <= 14 chars.
--
-- League tree (docs/09): Season -> League(+category) -> Tier -> Group(comp_group) -> Round
-- -> MatchDay. Discipline/Stage are gone (category folds into League; Stage becomes Tier);
-- Pool is renamed Group (table comp_group, no tournament_mode).

-- Landesverbände (Federation)
INSERT INTO federation (id, name)
VALUES ('fed-bw', 'Baden-Württemberg'),
       ('fed-by', 'Bayern'),
       ('fed-nrw', 'Nordrhein-Westfalen'),
       ('fed-he', 'Hessen'),
       ('fed-ni', 'Niedersachsen'),
       ('fed-be', 'Berlin'),
       ('fed-hh', 'Hamburg'),
       ('fed-sn', 'Sachsen');

-- Demo clubs (Vereine). federation_id references federation.id.
INSERT INTO club (id, name, short_name, city, active, federation_id)
VALUES ('club-tfcm', 'TFC München', 'TFCM', 'München', TRUE, 'fed-by'),
       ('club-kfa', 'Kickerfreunde Augsburg', 'KFA', 'Augsburg', TRUE, 'fed-by'),
       ('club-tsvs', 'TSV Stuttgart Kickers', 'TSVS', 'Stuttgart', TRUE, 'fed-bw'),
       ('club-ktfc', 'Karlsruher TFC', 'KTFC', 'Karlsruhe', TRUE, 'fed-bw'),
       ('club-kck', '1. KC Köln', 'KCK', 'Köln', TRUE, 'fed-nrw'),
       ('club-dtk', 'Dortmunder Tischkicker', 'DTK', 'Dortmund', TRUE, 'fed-nrw'),
       ('club-ffc', 'Frankfurt Foosball Club', 'FFC', 'Frankfurt am Main', TRUE, 'fed-he'),
       ('club-hk90', 'Hannover Kicker 1990', 'HK90', 'Hannover', TRUE, 'fed-ni'),
       ('club-bts', 'Berlin Table Soccer', 'BTS', 'Berlin', TRUE, 'fed-be'),
       ('club-hsvt', 'HSV Tischfußball', 'HSVT', 'Hamburg', TRUE, 'fed-hh'),
       ('club-lek', 'Leipzig Kickers', 'LEK', 'Leipzig', FALSE, 'fed-sn'),
       ('club-mtfv', 'Mannheimer TFV', 'MTFV', 'Mannheim', TRUE, 'fed-bw');

-- Teams. team_admin below needs a TEAM to scope to, and it gives the nested-scope chain a
-- leaf: fed-by (REGION) -> club-tfcm (CLUB) -> this team (TEAM). More teams spread across
-- regions so the region-scoped pickers differ; fed-by gets Herren + Damen teams.
INSERT INTO team (id, name, club_id)
VALUES ('team-tfcm-1', 'TFC München 1', 'club-tfcm'),
       ('team-tfcm-2', 'TFC München 2', 'club-tfcm'),
       ('team-kfa-1', 'Kickerfreunde Augsburg 1', 'club-kfa'),
       ('team-kfa-2', 'Kickerfreunde Augsburg 2', 'club-kfa'),
       ('team-tfcm-d', 'TFC München Damen', 'club-tfcm'),
       ('team-kfa-d', 'Kickerfreunde Augsburg Damen', 'club-kfa'),
       ('team-tsvs-1', 'TSV Stuttgart Kickers 1', 'club-tsvs'),
       ('team-tsvs-2', 'TSV Stuttgart Kickers 2', 'club-tsvs'),
       ('team-ktfc-1', 'Karlsruher TFC 1', 'club-ktfc'),
       ('team-mtfv-1', 'Mannheimer TFV 1', 'club-mtfv'),
       ('team-kck-1', '1. KC Köln 1', 'club-kck'),
       ('team-kck-2', '1. KC Köln 2', 'club-kck'),
       ('team-dtk-1', 'Dortmunder Tischkicker 1', 'club-dtk'),
       ('team-ffc-1', 'Frankfurt Foosball Club 1', 'club-ffc');

-- Players. dtfb_id matches the Keycloak username (the dev login maps username -> dtfb_id).
INSERT INTO player (id, dtfb_id, first_name, last_name, nationality, national_license, active)
VALUES ('player-admin', 'admin', 'DTFB', 'Administrator', 'DE', 'A', TRUE),
       ('player-test', 'test', 'Test', 'Player', 'DE', 'A', TRUE),
       ('player-region', 'region', 'Regina', 'Region', 'DE', 'A', TRUE),
       ('player-club', 'club', 'Claus', 'Club', 'DE', 'A', TRUE),
       ('player-team', 'team', 'Tom', 'Team', 'DE', 'A', TRUE);

-- More players (with license/birth-year/gender) to fill rosters and the player search.
INSERT INTO player (id, dtfb_id, first_name, last_name, nationality, national_id, birth_year, gender, national_license, active)
VALUES ('player-p1', 'p-1001', 'Lukas', 'Bauer', 'DE', '1001', 1991, 'man', 'A', TRUE),
       ('player-p2', 'p-1002', 'Jonas', 'Wagner', 'DE', '1002', 1988, 'man', 'A', TRUE),
       ('player-p3', 'p-1003', 'Felix', 'Schneider', 'DE', '1003', 1995, 'man', 'B', TRUE),
       ('player-p4', 'p-1004', 'Tim', 'Fischer', 'DE', '1004', 1993, 'man', 'B', TRUE),
       ('player-p5', 'p-1005', 'Niklas', 'Weber', 'DE', '1005', 1990, 'man', 'C', TRUE),
       ('player-p6', 'p-1006', 'Paul', 'Hoffmann', 'DE', '1006', 1997, 'man', 'C', TRUE),
       ('player-p7', 'p-1007', 'Anna', 'Schulz', 'DE', '1007', 1994, 'woman', 'A', TRUE),
       ('player-p8', 'p-1008', 'Laura', 'Koch', 'DE', '1008', 1996, 'woman', 'B', TRUE);

-- Role grants. Bootstrap global admin plus one admin per scope tier of the
-- fed-by -> club-tfcm -> team-tfcm-1 chain, so each scope level is testable.
INSERT INTO role_assignment (id, player_id, role, scope_type, scope_id, created_at)
VALUES ('ra-admin-glob', 'player-admin', 'ADMIN', 'GLOBAL', NULL, TIMESTAMP '2024-01-01 00:00:00'),
       ('ra-region', 'player-region', 'REGION_ADMIN', 'REGION', 'fed-by', TIMESTAMP '2024-01-01 00:00:00'),
       ('ra-club', 'player-club', 'CLUB_ADMIN', 'CLUB', 'club-tfcm', TIMESTAMP '2024-01-01 00:00:00'),
       ('ra-team', 'player-team', 'TEAM_ADMIN', 'TEAM', 'team-tfcm-1', TIMESTAMP '2024-01-01 00:00:00');

-- Categories — the classification a League points at (Herren/Damen/Open). Defined before any
-- league since league.category_id references them.
INSERT INTO category (id, name, short_name)
VALUES ('cat-herren', 'Herren', 'H'),
       ('cat-damen', 'Damen', 'D'),
       ('cat-open', 'Open', 'O');

-- A reusable league rule set (fed-by), referenced by the showcase Herren league below. The
-- game plan (2 doubles + 1 single) is stored as ordered game_plan_entry rows.
INSERT INTO league_rule_set (id, federation_id, name, play_system, points_win, points_draw,
                             points_loss, sets_per_game, points_to_win_set, matchday_decision,
                             side_switch_allowed)
VALUES ('rs-by-std', 'fed-by', 'Bayern Standard 3:1', 'ROUND_ROBIN', 3, 1, 0, 3, 7, 'ALL_GAMES', TRUE);

INSERT INTO game_plan_entry (id, rule_set_id, position, game_type)
VALUES ('gp-by-1', 'rs-by-std', 1, 'DOUBLE'),
       ('gp-by-2', 'rs-by-std', 2, 'DOUBLE'),
       ('gp-by-3', 'rs-by-std', 3, 'SINGLE');

-- Bayern's federation-wide default rule set: groups whose tier/league set no rule set of their own
-- inherit this (resolution order tier ?? league ?? federation default — docs/09-league-model.md §3).
UPDATE federation SET default_rule_set_id = 'rs-by-std' WHERE id = 'fed-by';

-- ---------------------------------------------------------------------------
-- Demo season WITH recorded results — to exercise the guarded-delete flow in the UI:
-- deleting it must be refused (409 SEASON_HAS_RESULTS) and offer "archive instead".
-- Full spine under fed-by (Bayern): season -> league -> tier -> group -> round -> match_day,
-- with one CONFIRMED match-day and two standings.
-- ---------------------------------------------------------------------------
INSERT INTO season (id, name, federation_id, start_date, end_date, registration_open)
VALUES ('season-res', 'Saison 2023 (mit Ergebnissen)', 'fed-by', DATE '2023-09-01', DATE '2024-05-31', FALSE);

INSERT INTO league (id, season_id, name, category_id)
VALUES ('league-res', 'season-res', 'Bayernliga 2023', 'cat-herren');

INSERT INTO tier (id, league_id, name, level)
VALUES ('tier-res', 'league-res', '1. Bayernliga', 1);

INSERT INTO comp_group (id, tier_id, name, group_state)
VALUES ('group-res', 'tier-res', 'Gruppe A', 'FINISHED');

INSERT INTO round (id, group_id, name)
VALUES ('round-res', 'group-res', 'Runde 1');

-- One match-day with a confirmed result (-> matchDaysWithResults = 1), one still open.
INSERT INTO match_day (id, round_id, name, start_date, result_state)
VALUES ('md-res-1', 'round-res', 'Spieltag 1', TIMESTAMP '2023-10-01 10:00:00', 'CONFIRMED'),
       ('md-res-2', 'round-res', 'Spieltag 2', TIMESTAMP '2023-10-08 10:00:00', 'OPEN');

-- Standings (-> standings = 2) — recorded results that block a hard delete.
INSERT INTO standing (id, group_id, team_id, played, wins, draws, losses, points, sets_won, sets_lost)
VALUES ('st-res-1', 'group-res', 'team-tfcm-1', 2, 2, 0, 0, 6, 6, 1),
       ('st-res-2', 'group-res', 'team-tfcm-2', 2, 0, 0, 2, 0, 1, 6);

-- Team placements (TeamParticipation, L1) in the source season: both TFC München teams
-- placed in Gruppe A. Gives the placements view real rows AND gives copy-forward
-- something to clone into the empty target season below.
INSERT INTO team_participation (id, team_id, league_id, group_id, roster_status, status)
VALUES ('tp-res-1', 'team-tfcm-1', 'league-res', 'group-res', 'CONFIRMED', 'ACTIVE'),
       ('tp-res-2', 'team-tfcm-2', 'league-res', 'group-res', 'CONFIRMED', 'ACTIVE');

-- ---------------------------------------------------------------------------
-- Empty target season under fed-by (Bayern) — the copy-forward destination:
-- on the placements page pick "Saison 2024" and copy-forward from "Saison 2023"
-- to clone the division + both placements into here.
-- ---------------------------------------------------------------------------
INSERT INTO season (id, name, federation_id, start_date, end_date, registration_open)
VALUES ('season-2024', 'Saison 2024', 'fed-by', DATE '2024-09-01', DATE '2025-05-31', TRUE);

-- Archived season (fed-by) — shows up only in the "View archive" list, not the main one.
INSERT INTO season (id, name, federation_id, start_date, end_date, registration_open, archived_at)
VALUES ('season-arch', 'Saison 2019 (archiviert)', 'fed-by', DATE '2019-09-01', DATE '2020-05-31', FALSE,
        TIMESTAMP '2020-07-01 00:00:00');

-- ---------------------------------------------------------------------------
-- Rich OPEN Bayern season — the showcase for the placement board and roster editor:
-- two leagues (Herren + Damen), the Herren league with multiple tiers, groups incl. an
-- empty one (count 0), placed + one unplaced team, and rosters in every lifecycle state
-- (DRAFT / SUBMITTED / CONFIRMED). The Herren league uses the shared rule set rs-by-std.
-- ---------------------------------------------------------------------------
INSERT INTO season (id, name, federation_id, start_date, end_date, registration_open)
VALUES ('season-by25', 'Saison 2024/25 (Bayern)', 'fed-by', DATE '2024-09-01', DATE '2025-05-31', TRUE);

INSERT INTO league (id, season_id, name, category_id, rule_set_id)
VALUES ('lg-by25-h', 'season-by25', 'Bayernliga Herren 2024/25', 'cat-herren', 'rs-by-std'),
       ('lg-by25-d', 'season-by25', 'Bayernliga Damen 2024/25', 'cat-damen', NULL);

-- Herren tiers: 1. Bayernliga (two groups), 2. Bayernliga (one group), plus a Playoffs tier.
INSERT INTO tier (id, league_id, name, level)
VALUES ('ti-by25-1', 'lg-by25-h', '1. Bayernliga', 1),
       ('ti-by25-2', 'lg-by25-h', '2. Bayernliga', 2),
       ('ti-by25-p', 'lg-by25-h', 'Playoffs', 3),
       ('ti-by25-d', 'lg-by25-d', 'Damenliga', 1);

INSERT INTO comp_group (id, tier_id, name, group_state)
VALUES ('g-by25-1a', 'ti-by25-1', 'Gruppe A', 'RUNNING'),
       ('g-by25-1b', 'ti-by25-1', 'Gruppe B', 'PLANNED'),
       ('g-by25-2', 'ti-by25-2', '2. Bayernliga', 'PLANNED'),
       ('g-by25-p', 'ti-by25-p', 'Aufstiegsrunde', 'PLANNED'),
       ('g-by25-d', 'ti-by25-d', 'Damenliga', 'PLANNED');

-- Placements: g-by25-2 and g-by25-p stay empty (count 0); team-kfa-2 is registered but
-- unplaced (null group). Roster states span the whole lifecycle. tp-by25-6 has withdrawn
-- (no matches recorded for it yet, so it's a clean withdrawal demo) -- roster locked, excluded
-- from copy-forward.
INSERT INTO team_participation (id, team_id, league_id, group_id, roster_status, status, withdrawn_at)
VALUES ('tp-by25-1', 'team-tfcm-1', 'lg-by25-h', 'g-by25-1a', 'CONFIRMED', 'ACTIVE', NULL),
       ('tp-by25-2', 'team-kfa-1', 'lg-by25-h', 'g-by25-1a', 'SUBMITTED', 'ACTIVE', NULL),
       ('tp-by25-3', 'team-tfcm-2', 'lg-by25-h', 'g-by25-1b', 'DRAFT', 'ACTIVE', NULL),
       ('tp-by25-4', 'team-kfa-2', 'lg-by25-h', NULL, 'DRAFT', 'ACTIVE', NULL),
       ('tp-by25-5', 'team-tfcm-d', 'lg-by25-d', 'g-by25-d', 'CONFIRMED', 'ACTIVE', NULL),
       ('tp-by25-6', 'team-kfa-d', 'lg-by25-d', 'g-by25-d', 'DRAFT', 'WITHDRAWN', TIMESTAMP '2024-10-15 09:00:00');

-- Roster entries. tp-by25-3 (DRAFT) has an active roster plus one soft-removed player
-- (removed_at set) so the transfer-history / soft-delete case is visible.
INSERT INTO roster_entry (id, participation_id, player_id, added_at, removed_at)
VALUES ('re-1', 'tp-by25-1', 'player-p1', TIMESTAMP '2024-09-10 10:00:00', NULL),
       ('re-2', 'tp-by25-1', 'player-p2', TIMESTAMP '2024-09-10 10:00:00', NULL),
       ('re-3', 'tp-by25-1', 'player-p3', TIMESTAMP '2024-09-10 10:00:00', NULL),
       ('re-4', 'tp-by25-2', 'player-p4', TIMESTAMP '2024-09-12 10:00:00', NULL),
       ('re-5', 'tp-by25-2', 'player-p5', TIMESTAMP '2024-09-12 10:00:00', NULL),
       ('re-6', 'tp-by25-3', 'player-p6', TIMESTAMP '2024-09-15 10:00:00', NULL),
       ('re-7', 'tp-by25-3', 'player-p1', TIMESTAMP '2024-09-15 10:00:00', NULL),
       ('re-8', 'tp-by25-3', 'player-p2', TIMESTAMP '2024-09-15 10:00:00', TIMESTAMP '2024-10-01 10:00:00'),
       ('re-9', 'tp-by25-5', 'player-p7', TIMESTAMP '2024-09-11 10:00:00', NULL),
       ('re-10', 'tp-by25-5', 'player-p8', TIMESTAMP '2024-09-11 10:00:00', NULL);

-- ---------------------------------------------------------------------------
-- A second region's league (Baden-Württemberg) — so placements/structure aren't
-- Bayern-only and switching regions shows genuinely different data.
-- ---------------------------------------------------------------------------
INSERT INTO season (id, name, federation_id, start_date, end_date, registration_open)
VALUES ('season-bw25', 'Saison 2024/25 (BW)', 'fed-bw', DATE '2024-09-01', DATE '2025-05-31', TRUE);

INSERT INTO league (id, season_id, name, category_id)
VALUES ('lg-bw25', 'season-bw25', 'Baden-Württemberg-Liga 2024/25', 'cat-herren');

INSERT INTO tier (id, league_id, name, level)
VALUES ('ti-bw25', 'lg-bw25', 'Oberliga BW', 1);

INSERT INTO comp_group (id, tier_id, name, group_state)
VALUES ('g-bw25', 'ti-bw25', 'Oberliga BW', 'RUNNING');

INSERT INTO team_participation (id, team_id, league_id, group_id, roster_status, status)
VALUES ('tp-bw25-1', 'team-tsvs-1', 'lg-bw25', 'g-bw25', 'CONFIRMED', 'ACTIVE'),
       ('tp-bw25-2', 'team-tsvs-2', 'lg-bw25', 'g-bw25', 'DRAFT', 'ACTIVE'),
       ('tp-bw25-3', 'team-ktfc-1', 'lg-bw25', 'g-bw25', 'SUBMITTED', 'ACTIVE'),
       ('tp-bw25-4', 'team-mtfv-1', 'lg-bw25', NULL, 'DRAFT', 'ACTIVE');

-- ---------------------------------------------------------------------------
-- An Open-category season (Bayern-Pokal) — a second category in fed-by, modeled as a
-- league with a group phase (two groups) and a finals tier. (Tournaments proper are parked;
-- this stays valid league-shaped demo data.)
-- ---------------------------------------------------------------------------
INSERT INTO season (id, name, federation_id, start_date, end_date, registration_open)
VALUES ('season-cup', 'Bayern-Pokal 2024', 'fed-by', DATE '2024-06-01', DATE '2024-06-30', TRUE);

INSERT INTO league (id, season_id, name, category_id)
VALUES ('lg-cup', 'season-cup', 'Bayern-Pokal 2024', 'cat-open');

INSERT INTO tier (id, league_id, name, level)
VALUES ('ti-cup-grp', 'lg-cup', 'Gruppenphase', 1),
       ('ti-cup-fin', 'lg-cup', 'Finalrunde', 2);

INSERT INTO comp_group (id, tier_id, name, group_state)
VALUES ('g-cup-a', 'ti-cup-grp', 'Gruppe A', 'FINISHED'),
       ('g-cup-b', 'ti-cup-grp', 'Gruppe B', 'FINISHED'),
       ('g-cup-ko', 'ti-cup-fin', 'K.-o.-Runde', 'PLANNED');

INSERT INTO team_participation (id, team_id, league_id, group_id, roster_status, status)
VALUES ('tp-cup-1', 'team-tfcm-1', 'lg-cup', 'g-cup-a', 'CONFIRMED', 'ACTIVE'),
       ('tp-cup-2', 'team-kfa-1', 'lg-cup', 'g-cup-a', 'CONFIRMED', 'ACTIVE'),
       ('tp-cup-3', 'team-tfcm-2', 'lg-cup', 'g-cup-b', 'CONFIRMED', 'ACTIVE'),
       ('tp-cup-4', 'team-kfa-2', 'lg-cup', 'g-cup-b', 'CONFIRMED', 'ACTIVE');

-- ---------------------------------------------------------------------------
-- "Current" and "upcoming" examples for TFC München 1's team-rosters page
-- (frontend TeamRostersService.seasonBadge): a season whose date range brackets
-- "now" shows CURRENT regardless of registration_open; a season that hasn't
-- started yet but is open for registration shows UPCOMING. Both dated relative
-- to a 2026-ish "today" — adjust forward if this seed is still in use once
-- these ranges are themselves in the past.
-- ---------------------------------------------------------------------------
INSERT INTO season (id, name, federation_id, start_date, end_date, registration_open)
VALUES ('season-2026', 'Saison 2026', 'fed-by', DATE '2026-01-01', DATE '2026-12-31', FALSE);

INSERT INTO league (id, season_id, name, category_id, rule_set_id)
VALUES ('lg-2026-h', 'season-2026', 'Bayernliga Herren 2026', 'cat-herren', 'rs-by-std');

INSERT INTO tier (id, league_id, name, level)
VALUES ('ti-2026-1', 'lg-2026-h', '1. Bayernliga', 1);

INSERT INTO comp_group (id, tier_id, name, group_state)
VALUES ('g-2026-1', 'ti-2026-1', 'Gruppe A', 'RUNNING');

-- Roster already confirmed — the season is running, so registration/roster editing
-- for it is closed; team-tfcm-1 is mid-season.
INSERT INTO team_participation (id, team_id, league_id, group_id, roster_status, status)
VALUES ('tp-2026-1', 'team-tfcm-1', 'lg-2026-h', 'g-2026-1', 'CONFIRMED', 'ACTIVE');

INSERT INTO roster_entry (id, participation_id, player_id, added_at, removed_at)
VALUES ('re-2026-1', 'tp-2026-1', 'player-p1', TIMESTAMP '2026-01-15 10:00:00', NULL),
       ('re-2026-2', 'tp-2026-1', 'player-p2', TIMESTAMP '2026-01-15 10:00:00', NULL),
       ('re-2026-3', 'tp-2026-1', 'player-p3', TIMESTAMP '2026-01-15 10:00:00', NULL);

-- Next season: registration already open, but it hasn't started yet — no tier/group
-- structure set up either, since placements haven't run (mirrors tp-by25-4's
-- "registered but unplaced" shape). team-tfcm-1 has pre-registered; its roster is
-- still an empty DRAFT since the season is still a ways off.
INSERT INTO season (id, name, federation_id, start_date, end_date, registration_open)
VALUES ('season-2027', 'Saison 2027/28', 'fed-by', DATE '2027-09-01', DATE '2028-05-31', TRUE);

INSERT INTO league (id, season_id, name, category_id, rule_set_id)
VALUES ('lg-2027-h', 'season-2027', 'Bayernliga Herren 2027/28', 'cat-herren', 'rs-by-std');

INSERT INTO team_participation (id, team_id, league_id, group_id, roster_status, status)
VALUES ('tp-2027-1', 'team-tfcm-1', 'lg-2027-h', NULL, 'DRAFT', 'ACTIVE');
