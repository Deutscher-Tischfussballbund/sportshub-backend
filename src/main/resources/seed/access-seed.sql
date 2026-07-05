-- noinspection SqlResolveForFile

-- Dev seed for the access domain: German Landesverbände, demo clubs, and a bootstrap
-- global admin so the first login is not stuck at /no-access.
--
-- Loaded on boot via spring.sql.init.data-locations (dev profile only), after Hibernate
-- creates the schema (defer-datasource-initialization). The dev datasource is H2 with
-- ddl-auto=create-drop, so the schema is fresh every boot and plain INSERTs are safe.
-- IDs are fixed here because the nano-id generator only runs for JPA-persisted entities.

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

-- Demo team. team_admin below needs a TEAM to scope to, and it gives the
-- nested-scope chain a leaf: fed-by (REGION) → club-tfcm (CLUB) → this team (TEAM).
INSERT INTO team (id, name, club_id)
VALUES ('team-tfcm-1', 'TFC München 1', 'club-tfcm'),
       ('team-tfcm-2', 'TFC München 2', 'club-tfcm');

-- Players. dtfb_id matches the Keycloak username (the dev login maps username → dtfb_id).
INSERT INTO player (id, dtfb_id, first_name, last_name, nationality, national_license, active)
VALUES ('player-admin', 'admin', 'DTFB', 'Administrator', 'DE', 'A', TRUE),
       ('player-test', 'test', 'Test', 'Player', 'DE', 'A', TRUE),
       ('player-region', 'region', 'Regina', 'Region', 'DE', 'A', TRUE),
       ('player-club', 'club', 'Claus', 'Club', 'DE', 'A', TRUE),
       ('player-team', 'team', 'Tom', 'Team', 'DE', 'A', TRUE);

-- Role grants. Bootstrap global admin plus one admin per scope tier of the
-- fed-by → club-tfcm → team-tfcm-1 chain, so each scope level is testable.
INSERT INTO role_assignment (id, player_id, role, scope_type, scope_id, created_at)
VALUES ('ra-admin-glob', 'player-admin', 'ADMIN', 'GLOBAL', NULL, TIMESTAMP '2024-01-01 00:00:00'),
       ('ra-region', 'player-region', 'REGION_ADMIN', 'REGION', 'fed-by', TIMESTAMP '2024-01-01 00:00:00'),
       ('ra-club', 'player-club', 'CLUB_ADMIN', 'CLUB', 'club-tfcm', TIMESTAMP '2024-01-01 00:00:00'),
       ('ra-team', 'player-team', 'TEAM_ADMIN', 'TEAM', 'team-tfcm-1', TIMESTAMP '2024-01-01 00:00:00');

-- ---------------------------------------------------------------------------
-- Demo season WITH recorded results — to exercise the guarded-delete flow in the UI:
-- deleting it must be refused (409 SEASON_HAS_RESULTS) and offer "archive instead".
-- Full spine under fed-by (Bayern): season → competition → discipline → stage → pool
-- → round → match_day, with one CONFIRMED match-day and two standings.
-- Expected 409 counts: competitions=1, matchDays=2, matchDaysWithResults=1, standings=2.
-- ---------------------------------------------------------------------------
INSERT INTO season (id, name, federation_id, start_date, end_date, registration_open)
VALUES ('season-res', 'Saison 2023 (mit Ergebnissen)', 'fed-by', DATE '2023-09-01', DATE '2024-05-31', FALSE);

INSERT INTO competition (id, season_id, name)
VALUES ('comp-res', 'season-res', 'Bayernliga 2023');

INSERT INTO discipline (id, competition_id)
VALUES ('disc-res', 'comp-res');

INSERT INTO stage (id, discipline_id, name)
VALUES ('stage-res', 'disc-res', 'Hauptrunde');

INSERT INTO pool (id, stage_id, name, pool_state, tournament_mode)
VALUES ('pool-res', 'stage-res', 'Gruppe A', 'FINISHED', 'ROUND_ROBIN');

INSERT INTO round (id, pool_id, name)
VALUES ('round-res', 'pool-res', 'Runde 1');

-- One match-day with a confirmed result (→ matchDaysWithResults = 1), one still open.
INSERT INTO match_day (id, round_id, name, start_date, result_state)
VALUES ('md-res-1', 'round-res', 'Spieltag 1', TIMESTAMP '2023-10-01 10:00:00', 'CONFIRMED'),
       ('md-res-2', 'round-res', 'Spieltag 2', TIMESTAMP '2023-10-08 10:00:00', 'OPEN');

-- Standings (→ standings = 2) — recorded results that block a hard delete.
INSERT INTO standing (id, pool_id, team_id, played, wins, draws, losses, points, sets_won, sets_lost)
VALUES ('st-res-1', 'pool-res', 'team-tfcm-1', 2, 2, 0, 0, 6, 6, 1),
       ('st-res-2', 'pool-res', 'team-tfcm-2', 2, 0, 0, 2, 0, 1, 6);

-- Team placements (TeamParticipation, L1) in the source season: both TFC München teams
-- placed in Gruppe A. Gives the placements view real rows AND gives copy-forward
-- something to clone into the empty target season below.
INSERT INTO team_participation (id, team_id, competition_id, pool_id, roster_status)
VALUES ('tp-res-1', 'team-tfcm-1', 'comp-res', 'pool-res', 'CONFIRMED'),
       ('tp-res-2', 'team-tfcm-2', 'comp-res', 'pool-res', 'CONFIRMED');

-- ---------------------------------------------------------------------------
-- Empty target season under fed-by (Bayern) — the copy-forward destination:
-- on the placements page pick "Saison 2024" and copy-forward from "Saison 2023"
-- to clone the division + both placements into here.
-- ---------------------------------------------------------------------------
INSERT INTO season (id, name, federation_id, start_date, end_date, registration_open)
VALUES ('season-2024', 'Saison 2024', 'fed-by', DATE '2024-09-01', DATE '2025-05-31', TRUE);

-- ===========================================================================
-- Richer demo data — one example per case, so the frontend has something real
-- to show for every view. Guardrails: season-res (delete-409 demo) and the
-- empty season-2024 (copy-forward target) above are left untouched.
-- ===========================================================================

-- Categories — the classification a Discipline points at (Herren/Damen/Open).
INSERT INTO category (id, name, short_name)
VALUES ('cat-herren', 'Herren', 'H'),
       ('cat-damen', 'Damen', 'D'),
       ('cat-open', 'Open', 'O');

-- More teams, spread across regions so the region-scoped team pickers differ per
-- region (team → club → region). fed-by gets Herren + Damen teams for the league below.
INSERT INTO team (id, name, club_id)
VALUES ('team-kfa-1', 'Kickerfreunde Augsburg 1', 'club-kfa'),
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

-- Archived season (fed-by) — shows up only in the "View archive" list, not the main one.
INSERT INTO season (id, name, federation_id, start_date, end_date, registration_open, archived_at)
VALUES ('season-arch', 'Saison 2019 (archiviert)', 'fed-by', DATE '2019-09-01', DATE '2020-05-31', FALSE,
        TIMESTAMP '2020-07-01 00:00:00');

-- ---------------------------------------------------------------------------
-- Rich OPEN Bayern league — the showcase for the placement board and roster
-- editor: two disciplines (Herren + Damen), Herren with two phases, multiple
-- groups incl. an empty one (count 0), placed + one unplaced team, and rosters
-- in every lifecycle state (DRAFT / SUBMITTED / CONFIRMED).
-- ---------------------------------------------------------------------------
INSERT INTO season (id, name, federation_id, start_date, end_date, registration_open)
VALUES ('season-by25', 'Saison 2024/25 (Bayern)', 'fed-by', DATE '2024-09-01', DATE '2025-05-31', TRUE);

INSERT INTO competition (id, season_id, name)
VALUES ('comp-by25', 'season-by25', 'Bayernliga 2024/25');

INSERT INTO discipline (id, competition_id, category_id)
VALUES ('disc-by25-h', 'comp-by25', 'cat-herren'),
       ('disc-by25-d', 'comp-by25', 'cat-damen');

-- NB: the id column is VARCHAR(14) (nano-id width), so fixed ids must stay ≤ 14 chars.
INSERT INTO stage (id, discipline_id, name)
VALUES ('st-by25-hh', 'disc-by25-h', 'Hauptrunde'),
       ('st-by25-hp', 'disc-by25-h', 'Playoffs'),
       ('st-by25-dh', 'disc-by25-d', 'Hauptrunde');

INSERT INTO pool (id, stage_id, name, pool_state, tournament_mode)
VALUES ('pool-by25-hA', 'st-by25-hh', '1. Bayernliga – Gruppe A', 'RUNNING', 'ROUND_ROBIN'),
       ('pool-by25-hB', 'st-by25-hh', '1. Bayernliga – Gruppe B', 'PLANNED', 'ROUND_ROBIN'),
       ('pool-by25-h2', 'st-by25-hh', '2. Bayernliga', 'PLANNED', 'ROUND_ROBIN'),
       ('pool-by25-hp', 'st-by25-hp', 'Aufstiegsrunde', 'PLANNED', 'ELIMINATION'),
       ('pool-by25-d', 'st-by25-dh', 'Damenliga', 'PLANNED', 'ROUND_ROBIN');

-- Placements: pool-by25-h2 and pool-by25-hPlay stay empty (count 0); team-kfa-2 is
-- registered but unplaced (null pool). Roster states span the whole lifecycle.
INSERT INTO team_participation (id, team_id, competition_id, pool_id, roster_status)
VALUES ('tp-by25-1', 'team-tfcm-1', 'comp-by25', 'pool-by25-hA', 'CONFIRMED'),
       ('tp-by25-2', 'team-kfa-1', 'comp-by25', 'pool-by25-hA', 'SUBMITTED'),
       ('tp-by25-3', 'team-tfcm-2', 'comp-by25', 'pool-by25-hB', 'DRAFT'),
       ('tp-by25-4', 'team-kfa-2', 'comp-by25', NULL, 'DRAFT'),
       ('tp-by25-5', 'team-tfcm-d', 'comp-by25', 'pool-by25-d', 'CONFIRMED'),
       ('tp-by25-6', 'team-kfa-d', 'comp-by25', 'pool-by25-d', 'DRAFT');

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

INSERT INTO competition (id, season_id, name)
VALUES ('comp-bw25', 'season-bw25', 'Baden-Württemberg-Liga 2024/25');

INSERT INTO discipline (id, competition_id, category_id)
VALUES ('disc-bw25-h', 'comp-bw25', 'cat-herren');

INSERT INTO stage (id, discipline_id, name)
VALUES ('stage-bw25-h', 'disc-bw25-h', 'Hauptrunde');

INSERT INTO pool (id, stage_id, name, pool_state, tournament_mode)
VALUES ('pool-bw25', 'stage-bw25-h', 'Oberliga BW', 'RUNNING', 'ROUND_ROBIN');

INSERT INTO team_participation (id, team_id, competition_id, pool_id, roster_status)
VALUES ('tp-bw25-1', 'team-tsvs-1', 'comp-bw25', 'pool-bw25', 'CONFIRMED'),
       ('tp-bw25-2', 'team-tsvs-2', 'comp-bw25', 'pool-bw25', 'DRAFT'),
       ('tp-bw25-3', 'team-ktfc-1', 'comp-bw25', 'pool-bw25', 'SUBMITTED'),
       ('tp-bw25-4', 'team-mtfv-1', 'comp-bw25', NULL, 'DRAFT');

-- ---------------------------------------------------------------------------
-- A single tournament (Bayern-Pokal) — the tournament shape: a group stage with
-- two ROUND_ROBIN groups and a DOUBLE_ELIMINATION knockout stage (still PLANNED).
-- ---------------------------------------------------------------------------
INSERT INTO season (id, name, federation_id, start_date, end_date, registration_open)
VALUES ('season-cup', 'Bayern-Pokal 2024', 'fed-by', DATE '2024-06-01', DATE '2024-06-30', TRUE);

INSERT INTO competition (id, season_id, name)
VALUES ('comp-cup', 'season-cup', 'Bayern-Pokal 2024');

INSERT INTO discipline (id, competition_id, category_id)
VALUES ('disc-cup', 'comp-cup', 'cat-open');

INSERT INTO stage (id, discipline_id, name)
VALUES ('stage-cup-grp', 'disc-cup', 'Gruppenphase'),
       ('stage-cup-fin', 'disc-cup', 'Finalrunde');

INSERT INTO pool (id, stage_id, name, pool_state, tournament_mode)
VALUES ('pool-cup-a', 'stage-cup-grp', 'Gruppe A', 'FINISHED', 'ROUND_ROBIN'),
       ('pool-cup-b', 'stage-cup-grp', 'Gruppe B', 'FINISHED', 'ROUND_ROBIN'),
       ('pool-cup-ko', 'stage-cup-fin', 'K.-o.-Runde', 'PLANNED', 'DOUBLE_ELIMINATION');

INSERT INTO team_participation (id, team_id, competition_id, pool_id, roster_status)
VALUES ('tp-cup-1', 'team-tfcm-1', 'comp-cup', 'pool-cup-a', 'CONFIRMED'),
       ('tp-cup-2', 'team-kfa-1', 'comp-cup', 'pool-cup-a', 'CONFIRMED'),
       ('tp-cup-3', 'team-tfcm-2', 'comp-cup', 'pool-cup-b', 'CONFIRMED'),
       ('tp-cup-4', 'team-kfa-2', 'comp-cup', 'pool-cup-b', 'CONFIRMED');
