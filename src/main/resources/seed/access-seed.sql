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
