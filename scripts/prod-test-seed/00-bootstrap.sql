-- noinspection SqlResolveForFile

-- One-time bootstrap for the VPS test-system rollout: 2 global admins + 5 region admins (one
-- per real DTFB Landesverband), each region admin also getting a team-admin account for a demo
-- team in their region. Raw SQL because these specific rows have no other path: Federation
-- (needs a fixed id), Club (no create endpoint exists at all in the API), app_user (must exist
-- BEFORE the account's first Keycloak login — no API creates one), RoleAssignment (the real
-- grant endpoint needs an already-admin JWT, the same chicken-and-egg BootstrapAdminInitializer
-- already solves once). Everything downstream (season/league/tier/group/participation/roster)
-- is built for real via seed-region.sh calling the actual REST API instead — see README.md.
--
-- Run this ONCE against the VPS's real MySQL, after the schema exists (Flyway V2__user_player_split
-- applied) and BEFORE any tester's first login. IDs are fixed <=14 chars (nano-id column width);
-- collision odds against the app's own random nano-ids are negligible (35^14 keyspace).
--
-- Usernames == dtfb_id claim, verbatim (Keycloak's `dtfb_id` protocol mapper maps username ->
-- claim 1:1) — the Keycloak user's username MUST exactly match the dtfb_id values below, or the
-- account logs in against a different (new, roleless) app_user row instead of this seeded one.
--
-- User/Player split: `app_user` is the permanent, season-independent Keycloak login identity
-- (RoleAssignment now points here, not at `player`). `team` is season-scoped, copied fresh into
-- every season by CopyForwardService/TeamService -- but these specific bootstrap rows are
-- deliberately created WITHOUT a season (season_id left NULL): they exist before any season does,
-- and get resolved into whichever season actually needs them the first time a placement/roster
-- API call references them (mirrors how the app's own copy-forward flow always resolve-or-creates
-- a season's copy on demand, never assuming one up front). `player` and `club` are NOT
-- season-scoped -- a rename is tracked via `entity_history` instead of duplicating the row.
--
-- Forces the session charset regardless of the invoking mysql client's own default — without
-- this, a client defaulting to e.g. latin1 double-encodes every umlaut below on the way in
-- (bit the first real rollout; see README.md's "Fixing already-corrupted data" for the repair).
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- Global admins — no region scope.
-- ---------------------------------------------------------------------------
INSERT INTO app_user (id, dtfb_id, first_name, last_name)
VALUES ('user-flock', 'flock', 'Marvin', 'Flock'),
       ('user-wedemn', 'wedemann', 'Tim', 'Wedemann');

INSERT INTO role_assignment (id, user_id, role, scope_type, scope_id, created_at)
VALUES ('ra-flock', 'user-flock', 'ADMIN', 'GLOBAL', NULL, NOW()),
       ('ra-wedemann', 'user-wedemn', 'ADMIN', 'GLOBAL', NULL, NOW());

-- ---------------------------------------------------------------------------
-- Shared reference data (not per-region): one category and one global-fallback rule set, so
-- every region's league has something to attach to without needing a bespoke rule set each.
-- ---------------------------------------------------------------------------
INSERT INTO category (id, name, short_name)
VALUES ('cat-herren', 'Herren', 'H');

INSERT INTO league_rule_set (id, federation_id, name, play_system, points_win, points_draw, points_loss,
                             sets_per_game, points_to_win_set, matchday_decision, side_switch_allowed)
VALUES ('rs-dtfb-std', NULL, 'DTFB Standard', 'ROUND_ROBIN', 3, 1, 0, 3, 7, 'ALL_GAMES', TRUE);

-- ---------------------------------------------------------------------------
-- Shared pool of filler players for team rosters (not tied to any Keycloak account) — reused
-- across regions/teams; RosterEntry has no uniqueness constraint stopping the same player from
-- appearing on multiple teams, and for demo data that's a fine tradeoff for a smaller seed. Not
-- season-scoped -- the same row is reused across every season it's rostered in.
-- ---------------------------------------------------------------------------
INSERT INTO player (id, first_name, last_name, nationality, birth_year, gender, national_license, active)
VALUES ('player-f1', 'Lukas', 'Bauer', 'DE', 1991, 'man', 'A', TRUE),
       ('player-f2', 'Jonas', 'Wagner', 'DE', 1988, 'man', 'A', TRUE),
       ('player-f3', 'Felix', 'Schneider', 'DE', 1995, 'man', 'B', TRUE),
       ('player-f4', 'Tim', 'Fischer', 'DE', 1993, 'man', 'B', TRUE),
       ('player-f5', 'Niklas', 'Weber', 'DE', 1990, 'man', 'C', TRUE),
       ('player-f6', 'Paul', 'Hoffmann', 'DE', 1997, 'man', 'C', TRUE),
       ('player-f7', 'Anna', 'Schulz', 'DE', 1994, 'woman', 'A', TRUE),
       ('player-f8', 'Laura', 'Koch', 'DE', 1996, 'woman', 'B', TRUE),
       ('player-f9', 'Moritz', 'Richter', 'DE', 1992, 'man', 'B', TRUE),
       ('player-f10', 'Julia', 'Klein', 'DE', 1998, 'woman', 'C', TRUE),
       ('player-f11', 'David', 'Wolf', 'DE', 1989, 'man', 'A', TRUE),
       ('player-f12', 'Sophie', 'Neumann', 'DE', 1999, 'woman', 'C', TRUE);

-- ---------------------------------------------------------------------------
-- Hamburg — TFVHH (Tischfussballverband Hamburg). Helmut Poppen.
-- ---------------------------------------------------------------------------
INSERT INTO federation (id, name) VALUES ('fed-tfvhh', 'Tischfussballverband Hamburg');
INSERT INTO club (id, name, short_name, city, active, federation_id)
VALUES ('club-tfvhh', 'Test-Verein Hamburg', 'TVHH', 'Hamburg', TRUE, 'fed-tfvhh');
-- team_identity_id = own id: these are each the only (so far) season-less copy of their identity;
-- a future copy-forward carries the same identity id onto whatever season-scoped row comes next.
INSERT INTO team (id, name, club_id, team_identity_id)
VALUES ('team-tfvhh-1', 'Test-Verein Hamburg 1', 'club-tfvhh', 'team-tfvhh-1'),
       ('team-tfvhh-2', 'Test-Verein Hamburg 2', 'club-tfvhh', 'team-tfvhh-2'),
       ('team-tfvhh-3', 'Test-Verein Hamburg 3', 'club-tfvhh', 'team-tfvhh-3');
INSERT INTO app_user (id, dtfb_id, first_name, last_name)
VALUES ('user-poppen', 'poppen', 'Helmut', 'Poppen'),
       ('user-poppent', 'poppen-team', 'Helmut', 'Poppen (Team)');
-- TEAM scope is keyed by team_identity_id (here, same value as the row's own id — see above).
INSERT INTO role_assignment (id, user_id, role, scope_type, scope_id, created_at)
VALUES ('ra-poppen-reg', 'user-poppen', 'REGION_ADMIN', 'REGION', 'fed-tfvhh', NOW()),
       ('ra-poppen-team', 'user-poppent', 'TEAM_ADMIN', 'TEAM', 'team-tfvhh-1', NOW());

-- ---------------------------------------------------------------------------
-- Mitteldeutschland — MTFV (Mitteldeutscher Tischfussballverband e.V.). Daniel Görlich.
-- ---------------------------------------------------------------------------
INSERT INTO federation (id, name) VALUES ('fed-mtfv', 'Mitteldeutscher Tischfussballverband e.V.');
INSERT INTO club (id, name, short_name, city, active, federation_id)
VALUES ('club-mtfv', 'Test-Verein Mitteldeutschland', 'TVMD', 'Leipzig', TRUE, 'fed-mtfv');
INSERT INTO team (id, name, club_id, team_identity_id)
VALUES ('team-mtfv-1', 'Test-Verein Mitteldeutschland 1', 'club-mtfv', 'team-mtfv-1'),
       ('team-mtfv-2', 'Test-Verein Mitteldeutschland 2', 'club-mtfv', 'team-mtfv-2'),
       ('team-mtfv-3', 'Test-Verein Mitteldeutschland 3', 'club-mtfv', 'team-mtfv-3');
INSERT INTO app_user (id, dtfb_id, first_name, last_name)
VALUES ('user-goerlic', 'goerlich', 'Daniel', 'Görlich'),
       ('user-goerlit', 'goerlich-team', 'Daniel', 'Görlich (Team)');
INSERT INTO role_assignment (id, user_id, role, scope_type, scope_id, created_at)
VALUES ('ra-goerlic-reg', 'user-goerlic', 'REGION_ADMIN', 'REGION', 'fed-mtfv', NOW()),
       ('ra-goerlich-tm', 'user-goerlit', 'TEAM_ADMIN', 'TEAM', 'team-mtfv-1', NOW());

-- ---------------------------------------------------------------------------
-- NRW — NWTFV (Nordrhein-Westfälischer Tischfussballverband). Stefan Engelhardt.
-- ---------------------------------------------------------------------------
INSERT INTO federation (id, name) VALUES ('fed-nwtfv', 'Nordrhein-Westfälischer Tischfussballverband');
INSERT INTO club (id, name, short_name, city, active, federation_id)
VALUES ('club-nwtfv', 'Test-Verein NRW', 'TVNRW', 'Köln', TRUE, 'fed-nwtfv');
INSERT INTO team (id, name, club_id, team_identity_id)
VALUES ('team-nwtfv-1', 'Test-Verein NRW 1', 'club-nwtfv', 'team-nwtfv-1'),
       ('team-nwtfv-2', 'Test-Verein NRW 2', 'club-nwtfv', 'team-nwtfv-2'),
       ('team-nwtfv-3', 'Test-Verein NRW 3', 'club-nwtfv', 'team-nwtfv-3');
INSERT INTO app_user (id, dtfb_id, first_name, last_name)
VALUES ('user-engelha', 'engelhardt', 'Stefan', 'Engelhardt'),
       ('user-engelht', 'engelhardt-team', 'Stefan', 'Engelhardt (Team)');
INSERT INTO role_assignment (id, user_id, role, scope_type, scope_id, created_at)
VALUES ('ra-engelh-reg', 'user-engelha', 'REGION_ADMIN', 'REGION', 'fed-nwtfv', NOW()),
       ('ra-engelh-team', 'user-engelht', 'TEAM_ADMIN', 'TEAM', 'team-nwtfv-1', NOW());

-- ---------------------------------------------------------------------------
-- Saarland — STFV (Saarländischer Tischfussball Verband e.V.). Jürgen Meyer.
-- ---------------------------------------------------------------------------
INSERT INTO federation (id, name) VALUES ('fed-stfv', 'Saarländischer Tischfussball Verband e.V.');
INSERT INTO club (id, name, short_name, city, active, federation_id)
VALUES ('club-stfv', 'Test-Verein Saarland', 'TVSL', 'Saarbrücken', TRUE, 'fed-stfv');
INSERT INTO team (id, name, club_id, team_identity_id)
VALUES ('team-stfv-1', 'Test-Verein Saarland 1', 'club-stfv', 'team-stfv-1'),
       ('team-stfv-2', 'Test-Verein Saarland 2', 'club-stfv', 'team-stfv-2'),
       ('team-stfv-3', 'Test-Verein Saarland 3', 'club-stfv', 'team-stfv-3');
INSERT INTO app_user (id, dtfb_id, first_name, last_name)
VALUES ('user-meyer', 'meyer', 'Jürgen', 'Meyer'),
       ('user-meyert', 'meyer-team', 'Jürgen', 'Meyer (Team)');
INSERT INTO role_assignment (id, user_id, role, scope_type, scope_id, created_at)
VALUES ('ra-meyer-reg', 'user-meyer', 'REGION_ADMIN', 'REGION', 'fed-stfv', NOW()),
       ('ra-meyer-team', 'user-meyert', 'TEAM_ADMIN', 'TEAM', 'team-stfv-1', NOW());

-- ---------------------------------------------------------------------------
-- Berlin — TFVB (Tischfussballverband Berlin e.V.). Paul Fleischanderl.
-- ---------------------------------------------------------------------------
INSERT INTO federation (id, name) VALUES ('fed-tfvb', 'Tischfussballverband Berlin e.V.');
INSERT INTO club (id, name, short_name, city, active, federation_id)
VALUES ('club-tfvb', 'Test-Verein Berlin', 'TVB', 'Berlin', TRUE, 'fed-tfvb');
INSERT INTO team (id, name, club_id, team_identity_id)
VALUES ('team-tfvb-1', 'Test-Verein Berlin 1', 'club-tfvb', 'team-tfvb-1'),
       ('team-tfvb-2', 'Test-Verein Berlin 2', 'club-tfvb', 'team-tfvb-2'),
       ('team-tfvb-3', 'Test-Verein Berlin 3', 'club-tfvb', 'team-tfvb-3');
INSERT INTO app_user (id, dtfb_id, first_name, last_name)
VALUES ('user-fleisca', 'fleischanderl', 'Paul', 'Fleischanderl'),
       ('user-fleisct', 'fleischanderl-team', 'Paul', 'Fleischanderl (Team)');
INSERT INTO role_assignment (id, user_id, role, scope_type, scope_id, created_at)
VALUES ('ra-fleisc-reg', 'user-fleisca', 'REGION_ADMIN', 'REGION', 'fed-tfvb', NOW()),
       ('ra-fleisc-team', 'user-fleisct', 'TEAM_ADMIN', 'TEAM', 'team-tfvb-1', NOW());
