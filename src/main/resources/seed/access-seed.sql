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
--
-- User/Player split: `app_user` is the permanent, season-independent Keycloak login identity
-- (dtfb_id/email) that RoleAssignment now points at. `team` is season-scoped, copied fresh into
-- every season (like league/tier/comp_group) -- so a real team referenced across multiple demo
-- seasons below gets one row PER SEASON, all sharing a stable team_identity_id, exactly what
-- CopyForwardService/TeamService#resolveForSeason does at runtime. `player` and `club` are NOT
-- season-scoped -- a single row is shared across every season it's referenced from (e.g.
-- player-p1 below sits on rosters in both season-by25 and season-2026); a rename is tracked via
-- `entity_history` instead of duplicating the row. `club_membership` is the precondition for a
-- roster_entry: RosterService#addPlayer refuses to roster a player onto a team unless they're
-- already an active member of that team's club, so every player below gets a membership row
-- before its roster_entry rows.

-- Root federation (DTFB) plus the Landesverbände, each pointing at it via parent_federation_id --
-- a general self-referencing tree (Federation#isRoot), not hardcoded to two levels; today's real
-- data just happens to be exactly two.
INSERT INTO federation (id, name)
VALUES ('fed-dtfb', 'DTFB');

INSERT INTO federation (id, name, parent_federation_id)
VALUES ('fed-bw', 'Baden-Württemberg', 'fed-dtfb'),
       ('fed-by', 'Bayern', 'fed-dtfb'),
       ('fed-nrw', 'Nordrhein-Westfalen', 'fed-dtfb'),
       ('fed-he', 'Hessen', 'fed-dtfb'),
       ('fed-ni', 'Niedersachsen', 'fed-dtfb'),
       ('fed-be', 'Berlin', 'fed-dtfb'),
       ('fed-hh', 'Hamburg', 'fed-dtfb'),
       ('fed-sn', 'Sachsen', 'fed-dtfb');

-- Demo clubs (Vereine). federation_id references federation.id. Not season-scoped -- a single
-- row is shared by every team/season that references it; a rename is tracked via `entity_history`
-- instead of duplicating the row.
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

-- ---------------------------------------------------------------------------
-- Seasons, up front: team/player are now season-scoped (FK required), so every season a demo
-- team/player row attaches to must exist before those INSERTs run.
-- ---------------------------------------------------------------------------
-- Never opened (both bounds null) -- a fully played-out old season, registration long since moot.
INSERT INTO season (id, name, federation_id, start_date, end_date, registration_opens_at, registration_closes_at)
VALUES ('season-res', 'Saison 2023 (mit Ergebnissen)', 'fed-by', DATE '2023-09-01', DATE '2024-05-31', NULL, NULL);

-- Open indefinitely (opened long ago, no close date) -- stays open regardless of "today".
INSERT INTO season (id, name, federation_id, start_date, end_date, registration_opens_at)
VALUES ('season-2024', 'Saison 2024', 'fed-by', DATE '2024-09-01', DATE '2025-05-31', DATE '2024-01-01');

-- Archived season (fed-by) — shows up only in the "View archive" list, not the main one.
INSERT INTO season (id, name, federation_id, start_date, end_date, registration_opens_at, registration_closes_at, archived_at)
VALUES ('season-arch', 'Saison 2019 (archiviert)', 'fed-by', DATE '2019-09-01', DATE '2020-05-31', NULL, NULL,
        TIMESTAMP '2020-07-01 00:00:00');

INSERT INTO season (id, name, federation_id, start_date, end_date, registration_opens_at)
VALUES ('season-by25', 'Saison 2024/25 (Bayern)', 'fed-by', DATE '2024-09-01', DATE '2025-05-31', DATE '2024-01-01');

INSERT INTO season (id, name, federation_id, start_date, end_date, registration_opens_at)
VALUES ('season-bw25', 'Saison 2024/25 (BW)', 'fed-bw', DATE '2024-09-01', DATE '2025-05-31', DATE '2024-01-01');

INSERT INTO season (id, name, federation_id, start_date, end_date, registration_opens_at)
VALUES ('season-cup', 'Bayern-Pokal 2024', 'fed-by', DATE '2024-06-01', DATE '2024-06-30', DATE '2024-01-01');

-- Registration window already closed (Oct-Dec 2025) -- fixed in the past, unlike the season's
-- own dates above it never needs "adjust forward".
INSERT INTO season (id, name, federation_id, start_date, end_date, registration_opens_at, registration_closes_at)
VALUES ('season-2026', 'Saison 2026', 'fed-by', DATE '2026-01-01', DATE '2026-12-31',
        DATE '2025-10-01', DATE '2025-12-31');

INSERT INTO season (id, name, federation_id, start_date, end_date, registration_opens_at)
VALUES ('season-2027', 'Saison 2027/28', 'fed-by', DATE '2027-09-01', DATE '2028-05-31', DATE '2026-01-01');

-- ---------------------------------------------------------------------------
-- Teams. Season-scoped like League/Tier/Group: a team referenced across multiple demo seasons
-- gets one row PER SEASON, all sharing team_identity_id (mirrors what CopyForwardService/
-- TeamService#resolveForSeason do at runtime). team_admin below needs a TEAM to scope to (by
-- identity, not row id), and it gives the nested-scope chain a leaf: fed-by (REGION) ->
-- club-tfcm (CLUB) -> tid-tfcm-1 (TEAM). More teams spread across regions so the region-scoped
-- pickers differ; fed-by gets Herren + Damen teams.
-- ---------------------------------------------------------------------------
INSERT INTO team (id, season_id, name, club_id, team_identity_id)
VALUES
-- TFC München 1 -- appears in five demo seasons, one row each, same identity tid-tfcm-1.
('tfcm1-res', 'season-res', 'TFC München 1', 'club-tfcm', 'tid-tfcm-1'),
('tfcm1-by25', 'season-by25', 'TFC München 1', 'club-tfcm', 'tid-tfcm-1'),
('tfcm1-cup', 'season-cup', 'TFC München 1', 'club-tfcm', 'tid-tfcm-1'),
('tfcm1-2026', 'season-2026', 'TFC München 1', 'club-tfcm', 'tid-tfcm-1'),
('tfcm1-2027', 'season-2027', 'TFC München 1', 'club-tfcm', 'tid-tfcm-1'),
-- TFC München 2 -- three demo seasons, identity tid-tfcm-2.
('tfcm2-res', 'season-res', 'TFC München 2', 'club-tfcm', 'tid-tfcm-2'),
('tfcm2-by25', 'season-by25', 'TFC München 2', 'club-tfcm', 'tid-tfcm-2'),
('tfcm2-cup', 'season-cup', 'TFC München 2', 'club-tfcm', 'tid-tfcm-2'),
-- Kickerfreunde Augsburg 1 -- two demo seasons, identity tid-kfa-1.
('kfa1-by25', 'season-by25', 'Kickerfreunde Augsburg 1', 'club-kfa', 'tid-kfa-1'),
('kfa1-cup', 'season-cup', 'Kickerfreunde Augsburg 1', 'club-kfa', 'tid-kfa-1'),
-- Kickerfreunde Augsburg 2 -- two demo seasons, identity tid-kfa-2.
('kfa2-by25', 'season-by25', 'Kickerfreunde Augsburg 2', 'club-kfa', 'tid-kfa-2'),
('kfa2-cup', 'season-cup', 'Kickerfreunde Augsburg 2', 'club-kfa', 'tid-kfa-2'),
-- Single-demo-season teams: one row each, identity = own id (no other copy exists).
('team-tfcm-d', 'season-by25', 'TFC München Damen', 'club-tfcm', 'team-tfcm-d'),
('team-kfa-d', 'season-by25', 'Kickerfreunde Augsburg Damen', 'club-kfa', 'team-kfa-d'),
('team-tsvs-1', 'season-bw25', 'TSV Stuttgart Kickers 1', 'club-tsvs', 'team-tsvs-1'),
('team-tsvs-2', 'season-bw25', 'TSV Stuttgart Kickers 2', 'club-tsvs', 'team-tsvs-2'),
('team-ktfc-1', 'season-bw25', 'Karlsruher TFC 1', 'club-ktfc', 'team-ktfc-1'),
('team-mtfv-1', 'season-bw25', 'Mannheimer TFV 1', 'club-mtfv', 'team-mtfv-1'),
('team-tfcm-3', 'season-by25', 'TFC München 3', 'club-tfcm', 'team-tfcm-3'),
('team-kfa-3', 'season-by25', 'Kickerfreunde Augsburg 3', 'club-kfa', 'team-kfa-3'),
-- Browsable-only teams: never placed in a demo league, just fill out the team pickers.
('team-kck-1', 'season-by25', '1. KC Köln 1', 'club-kck', 'team-kck-1'),
('team-kck-2', 'season-by25', '1. KC Köln 2', 'club-kck', 'team-kck-2'),
('team-dtk-1', 'season-by25', 'Dortmunder Tischkicker 1', 'club-dtk', 'team-dtk-1'),
('team-ffc-1', 'season-by25', 'Frankfurt Foosball Club 1', 'club-ffc', 'team-ffc-1');

-- ---------------------------------------------------------------------------
-- Login identities (app_user): the Keycloak-authenticated actors used across the seed/tests.
-- dtfb_id matches the Keycloak username (the dev login maps username -> dtfb_id). RoleAssignment
-- points here now, not at `player` -- a role belongs to a login identity, not a season-scoped
-- competitor record.
-- ---------------------------------------------------------------------------
INSERT INTO app_user (id, dtfb_id, first_name, last_name)
VALUES ('usr-admin', 'admin', 'DTFB', 'Administrator'),
       ('usr-test', 'test', 'Test', 'Player'),
       ('usr-region', 'region', 'Regina', 'Region'),
       ('usr-club', 'club', 'Claus', 'Club'),
       ('usr-team', 'team', 'Tom', 'Team'),
       ('usr-liga', 'liga', 'Lena', 'Liga'),
       ('usr-dtfb', 'dtfb', 'Dana', 'DTFB');

-- ---------------------------------------------------------------------------
-- Roster-fill players. Not season-scoped -- one row per person, shared across every season it's
-- rostered in (e.g. player-p1/p2/p3 sit on rosters in both season-by25 and season-2026 below).
-- `player-test`/`player-club` are also referenced by id literal in several controller tests as
-- ready-made roster fill players -- kept as real Player rows (distinct from the app_user
-- identities of the same name above; the two concepts used to be one row, now they aren't).
-- ---------------------------------------------------------------------------
INSERT INTO player (id, first_name, last_name, nationality, birth_year, gender, national_license, active)
VALUES ('player-test', 'Test', 'Player', 'DE', 1990, 'MALE', 'A', TRUE),
       ('player-club', 'Claus', 'Club', 'DE', 1985, 'MALE', 'A', TRUE);

-- More players (with license/birth-year/gender) to fill rosters and the player search.
INSERT INTO player (id, first_name, last_name, nationality, national_id, birth_year, gender, national_license, active)
VALUES ('player-p1', 'Lukas', 'Bauer', 'DE', '1001', 1991, 'MALE', 'A', TRUE),
       ('player-p2', 'Jonas', 'Wagner', 'DE', '1002', 1988, 'MALE', 'A', TRUE),
       ('player-p3', 'Felix', 'Schneider', 'DE', '1003', 1995, 'MALE', 'B', TRUE),
       ('player-p4', 'Tim', 'Fischer', 'DE', '1004', 1993, 'MALE', 'B', TRUE),
       ('player-p5', 'Niklas', 'Weber', 'DE', '1005', 1990, 'MALE', 'C', TRUE),
       ('player-p6', 'Paul', 'Hoffmann', 'DE', '1006', 1997, 'MALE', 'C', TRUE),
       ('player-p7', 'Anna', 'Schulz', 'DE', '1007', 1994, 'FEMALE', 'A', TRUE),
       ('player-p8', 'Laura', 'Koch', 'DE', '1008', 1996, 'FEMALE', 'B', TRUE),
       -- Divers, competing in women's leagues: admins see "diverse_women", everyone else "diverse".
       ('player-p9', 'Kim', 'Neumann', 'DE', '1009', 1999, 'DIVERSE_WOMEN', 'C', TRUE);

-- Role grants. Bootstrap global admin plus one admin per scope tier of the
-- fed-by -> club-tfcm -> TFC München 1 chain, so each scope level is testable. CLUB scope is
-- keyed by the club's plain row id (club-tfcm, not season-scoped); TEAM scope is keyed by
-- team_identity_id (tid-tfcm-1) -- not any one season's row id, so it stays valid across
-- copy-forward.
INSERT INTO role_assignment (id, user_id, role, scope_type, scope_id, created_at)
VALUES ('ra-admin-glob', 'usr-admin', 'ADMIN', 'GLOBAL', NULL, TIMESTAMP '2024-01-01 00:00:00'),
       ('ra-region', 'usr-region', 'REGION_ADMIN', 'REGION', 'fed-by', TIMESTAMP '2024-01-01 00:00:00'),
       ('ra-club', 'usr-club', 'CLUB_ADMIN', 'CLUB', 'club-tfcm', TIMESTAMP '2024-01-01 00:00:00'),
       ('ra-team', 'usr-team', 'TEAM_ADMIN', 'TEAM', 'tid-tfcm-1', TIMESTAMP '2024-01-01 00:00:00'),
       -- Root federation admin: narrowly-scoped cross-federation authority (AuthorizationService) --
       -- can create/manage root-level (Bundesliga) teams and rosters for ANY club, but not a
       -- sub-federation's own club profiles/membership/seasons/leagues/rule sets.
       ('ra-dtfb', 'usr-dtfb', 'REGION_ADMIN', 'REGION', 'fed-dtfb', TIMESTAMP '2024-01-01 00:00:00');

-- Categories — the classification a League points at (Herren/Damen/Open). Defined before any
-- league since league.category_id references them. eligible_side restricts roster adds/submits
-- to one side of the men's/women's split (docs/19); Open stays unrestricted.
INSERT INTO category (id, name, short_name, eligible_side)
VALUES ('cat-herren', 'Herren', 'H', 'MEN'),
       ('cat-damen', 'Damen', 'D', 'WOMEN'),
       ('cat-open', 'Open', 'O', NULL);

-- A reusable league rule set (fed-by), referenced by the showcase Herren league below. The
-- game plan (2 doubles + 1 single) is stored as ordered game_plan_entry rows. scheduling_mode
-- DAY_BATCH (docs/12) means any fixture-free group resolving to this rule set -- e.g. g-by25-1a
-- below, which already has 2 placed teams and zero Round rows -- can generate fixtures via the
-- admin day-batch flow.
INSERT INTO league_rule_set (id, federation_id, name, play_system, points_win, points_draw,
                             points_loss, sets_per_game, points_to_win_set, matchday_decision,
                             side_switch_allowed, scheduling_mode)
VALUES ('rs-by-std', 'fed-by', 'Bayern Standard 3:1', 'ROUND_ROBIN', 3, 1, 0, 3, 7, 'ALL_GAMES', TRUE,
        'DAY_BATCH');

INSERT INTO game_plan_entry (id, rule_set_id, position, game_type)
VALUES ('gp-by-1', 'rs-by-std', 1, 'DOUBLE'),
       ('gp-by-2', 'rs-by-std', 2, 'DOUBLE'),
       ('gp-by-3', 'rs-by-std', 3, 'SINGLE');

-- A second rule set using the WINDOW convention instead -- attached to a tier below (not the
-- league), so both scheduling conventions have a ready, fixture-free demo group without
-- reusing the same rule set for each.
INSERT INTO league_rule_set (id, federation_id, name, play_system, points_win, points_draw,
                             points_loss, sets_per_game, points_to_win_set, matchday_decision,
                             side_switch_allowed, scheduling_mode, scheduling_window_days)
VALUES ('rs-by-window', 'fed-by', 'Bayern Fenster-Terminierung', 'ROUND_ROBIN', 3, 1, 0, 3, 7,
        'ALL_GAMES', TRUE, 'WINDOW', 14);

-- Bayern's federation-wide default rule set: groups whose tier/league set no rule set of their own
-- inherit this (resolution order tier ?? league ?? federation default — docs/09-league-model.md §3).
UPDATE federation SET default_rule_set_id = 'rs-by-std' WHERE id = 'fed-by';

-- DTFB-global template (federation_id NULL): the resolver's last fallback for a federation with no
-- default_rule_set_id of its own, in place of a bare hardcoded constant (docs/09-league-model.md §3).
INSERT INTO league_rule_set (id, federation_id, name, play_system, points_win, points_draw, points_loss)
VALUES ('rs-dtfb-std', NULL, 'DTFB Standard', 'ROUND_ROBIN', 2, 1, 0);

-- ---------------------------------------------------------------------------
-- Demo season WITH recorded results — to exercise the guarded-delete flow in the UI:
-- deleting it must be refused (409 SEASON_HAS_RESULTS) and offer "archive instead".
-- Full spine under fed-by (Bayern): season -> league -> tier -> group -> round -> match_day,
-- with one CONFIRMED match-day and two standings.
-- ---------------------------------------------------------------------------
-- ---------------------------------------------------------------------------
-- League identity (SPO-28): league_identity_id is stable across a league's season-copies and is what
-- LEAGUE_ADMIN grants point to. The three "Bayernliga Herren" seasons (lg-by25-h, lg-2026-h,
-- lg-2027-h) share one identity (lg-by25-h), so the league admin below (ra-liga) administers all
-- three; every other league is its own identity.
-- ---------------------------------------------------------------------------
INSERT INTO league (id, league_identity_id, season_id, name, category_id)
VALUES ('league-res', 'league-res', 'season-res', 'Bayernliga 2023', 'cat-herren');

INSERT INTO tier (id, league_id, name, level)
VALUES ('tier-res', 'league-res', '1. Bayernliga', 1);

INSERT INTO comp_group (id, tier_id, name, group_state)
VALUES ('group-res', 'tier-res', 'Gruppe A', 'FINISHED');

INSERT INTO round (id, group_id, name)
VALUES ('round-res', 'group-res', 'Runde 1');

-- One match-day with a confirmed result (-> matchDaysWithResults = 1), one still open. Both
-- fixtures' dates are already settled (seeded pre-scheduling-feature), so scheduling_state is
-- CONFIRMED, not the generated-fixture DEFAULT.
-- md-res-1 is a complete final result: both line-ups (further below, once the rosters exist), all three
-- games of Bayern's default game plan scored, both captains confirmed.
INSERT INTO match_day (id, round_id, name, team_home_id, team_away_id, start_date, result_state, scheduling_state,
                       home_confirmed_at, away_confirmed_at, decided_at, first_final_at)
VALUES ('md-res-1', 'round-res', 'Spieltag 1', 'tfcm1-res', 'tfcm2-res', TIMESTAMP '2023-10-01 10:00:00', 'CONFIRMED', 'CONFIRMED',
        TIMESTAMP '2023-10-01 12:00:00', TIMESTAMP '2023-10-01 12:05:00', TIMESTAMP '2023-10-01 11:55:00', TIMESTAMP '2023-10-01 12:05:00'),
       ('md-res-2', 'round-res', 'Spieltag 2', 'tfcm2-res', 'tfcm1-res', TIMESTAMP '2023-10-08 10:00:00', 'OPEN', 'CONFIRMED',
        NULL, NULL, NULL, NULL);

INSERT INTO match_game (id, match_day_id, position, type, start_time, home_score, away_score, state, winner)
VALUES ('mg-res-1-1', 'md-res-1', 1, 'DOUBLE', TIMESTAMP '2023-10-01 10:00:00', 7, 4, 'PLAYED', 'HOME'),
       ('mg-res-1-2', 'md-res-1', 2, 'DOUBLE', TIMESTAMP '2023-10-01 10:00:00', 5, 7, 'PLAYED', 'AWAY'),
       ('mg-res-1-3', 'md-res-1', 3, 'SINGLE', TIMESTAMP '2023-10-01 10:00:00', 7, 3, 'PLAYED', 'HOME');

-- Standings (-> standings = 2) — recorded results that block a hard delete.
INSERT INTO standing (id, group_id, team_id, played, wins, draws, losses, points, sets_won, sets_lost)
VALUES ('st-res-1', 'group-res', 'tfcm1-res', 2, 2, 0, 0, 6, 6, 1),
       ('st-res-2', 'group-res', 'tfcm2-res', 2, 0, 0, 2, 0, 1, 6);

-- Team placements (TeamParticipation, L1) in the source season: both TFC München teams
-- placed in Gruppe A. Gives the placements view real rows AND gives copy-forward
-- something to clone into the empty target season below.
INSERT INTO team_participation (id, team_id, league_id, group_id, roster_status, status)
VALUES ('tp-res-1', 'tfcm1-res', 'league-res', 'group-res', 'CONFIRMED', 'ACTIVE'),
       ('tp-res-2', 'tfcm2-res', 'league-res', 'group-res', 'CONFIRMED', 'ACTIVE');

-- Rosters of the 2023 teams and md-res-1's line-ups (docs/23: no result without both line-ups).
-- TFC München 2 fields two club players of its own (player-p20/p21, joined in 2022).
INSERT INTO player (id, first_name, last_name, nationality, national_id, birth_year, gender, national_license, active)
VALUES ('player-p20', 'Stefan', 'Lang', 'DE', '1020', 1984, 'MALE', 'C', TRUE),
       ('player-p21', 'Markus', 'Huber', 'DE', '1021', 1986, 'MALE', 'C', TRUE);

INSERT INTO club_membership (id, player_id, club_id, joined_at)
VALUES ('cm-p20-tfcm', 'player-p20', 'club-tfcm', TIMESTAMP '2022-01-01 00:00:00'),
       ('cm-p21-tfcm', 'player-p21', 'club-tfcm', TIMESTAMP '2022-01-01 00:00:00');

INSERT INTO roster_entry (id, participation_id, player_id, added_at, removed_at)
VALUES ('re-res-1', 'tp-res-1', 'player-p1', TIMESTAMP '2023-09-01 10:00:00', NULL),
       ('re-res-2', 'tp-res-1', 'player-p2', TIMESTAMP '2023-09-01 10:00:00', NULL),
       ('re-res-3', 'tp-res-1', 'player-p3', TIMESTAMP '2023-09-01 10:00:00', NULL),
       ('re-res-4', 'tp-res-2', 'player-p6', TIMESTAMP '2023-09-01 10:00:00', NULL),
       ('re-res-5', 'tp-res-2', 'player-p20', TIMESTAMP '2023-09-01 10:00:00', NULL),
       ('re-res-6', 'tp-res-2', 'player-p21', TIMESTAMP '2023-09-01 10:00:00', NULL);

INSERT INTO lineup (id, match_day_id, team_id, submitted_at, submitted_by_dtfb_id)
VALUES ('lu-res-1-h', 'md-res-1', 'tfcm1-res', TIMESTAMP '2023-10-01 09:30:00', 'team'),
       ('lu-res-1-a', 'md-res-1', 'tfcm2-res', TIMESTAMP '2023-10-01 09:35:00', NULL);

INSERT INTO lineup_entry (id, lineup_id, match_id, slot, player_id)
VALUES ('le-res-1-h11', 'lu-res-1-h', 'mg-res-1-1', 1, 'player-p1'),
       ('le-res-1-h12', 'lu-res-1-h', 'mg-res-1-1', 2, 'player-p2'),
       ('le-res-1-h21', 'lu-res-1-h', 'mg-res-1-2', 1, 'player-p3'),
       ('le-res-1-h22', 'lu-res-1-h', 'mg-res-1-2', 2, 'player-p1'),
       ('le-res-1-h31', 'lu-res-1-h', 'mg-res-1-3', 1, 'player-p2'),
       ('le-res-1-a11', 'lu-res-1-a', 'mg-res-1-1', 1, 'player-p6'),
       ('le-res-1-a12', 'lu-res-1-a', 'mg-res-1-1', 2, 'player-p20'),
       ('le-res-1-a21', 'lu-res-1-a', 'mg-res-1-2', 1, 'player-p21'),
       ('le-res-1-a22', 'lu-res-1-a', 'mg-res-1-2', 2, 'player-p6'),
       ('le-res-1-a31', 'lu-res-1-a', 'mg-res-1-3', 1, 'player-p20');

-- ---------------------------------------------------------------------------
-- Empty target season under fed-by (Bayern) — the copy-forward destination:
-- on the placements page pick "Saison 2024" and copy-forward from "Saison 2023"
-- to clone the division + both placements into here.
-- ---------------------------------------------------------------------------
-- (season-2024 itself is inserted up top, alongside the other seasons.)

-- ---------------------------------------------------------------------------
-- Rich OPEN Bayern season — the showcase for the placement board and roster editor:
-- two leagues (Herren + Damen), the Herren league with multiple tiers, groups incl. an
-- empty one (count 0), placed + one unplaced team, and rosters in every lifecycle state
-- (DRAFT / SUBMITTED / CONFIRMED). The Herren league uses the shared rule set rs-by-std.
-- ---------------------------------------------------------------------------
INSERT INTO league (id, league_identity_id, season_id, name, category_id, rule_set_id)
VALUES ('lg-by25-h', 'lg-by25-h', 'season-by25', 'Bayernliga Herren 2024/25', 'cat-herren', 'rs-by-std'),
       ('lg-by25-d', 'lg-by25-d', 'season-by25', 'Bayernliga Damen 2024/25', 'cat-damen', NULL);

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

-- Placements: g-by25-2 and g-by25-p stay empty (count 0); Kickerfreunde Augsburg 2 is
-- registered but unplaced (null group). Roster states span the whole lifecycle. tp-by25-6 has
-- withdrawn (no matches recorded for it yet, so it's a clean withdrawal demo) -- roster locked,
-- excluded from copy-forward.
INSERT INTO team_participation (id, team_id, league_id, group_id, roster_status, status, withdrawn_at)
VALUES ('tp-by25-1', 'tfcm1-by25', 'lg-by25-h', 'g-by25-1a', 'CONFIRMED', 'ACTIVE', NULL),
       ('tp-by25-2', 'kfa1-by25', 'lg-by25-h', 'g-by25-1a', 'SUBMITTED', 'ACTIVE', NULL),
       ('tp-by25-3', 'tfcm2-by25', 'lg-by25-h', 'g-by25-1b', 'DRAFT', 'ACTIVE', NULL),
       ('tp-by25-4', 'kfa2-by25', 'lg-by25-h', NULL, 'DRAFT', 'ACTIVE', NULL),
       ('tp-by25-5', 'team-tfcm-d', 'lg-by25-d', 'g-by25-d', 'CONFIRMED', 'ACTIVE', NULL),
       ('tp-by25-6', 'team-kfa-d', 'lg-by25-d', 'g-by25-d', 'DRAFT', 'WITHDRAWN', TIMESTAMP '2024-10-15 09:00:00');

-- Club memberships (precondition for a roster_entry below -- RosterService#addPlayer enforces
-- active membership in the team's club): one row per distinct (player, club) pair the roster
-- entries below actually need, joined well before any of those roster_entry.added_at timestamps.
INSERT INTO club_membership (id, player_id, club_id, joined_at)
VALUES ('cm-p1-tfcm', 'player-p1', 'club-tfcm', TIMESTAMP '2022-01-01 00:00:00'),
       ('cm-p2-tfcm', 'player-p2', 'club-tfcm', TIMESTAMP '2022-01-01 00:00:00'),
       ('cm-p3-tfcm', 'player-p3', 'club-tfcm', TIMESTAMP '2022-01-01 00:00:00'),
       ('cm-p4-kfa', 'player-p4', 'club-kfa', TIMESTAMP '2024-01-01 00:00:00'),
       ('cm-p5-kfa', 'player-p5', 'club-kfa', TIMESTAMP '2024-01-01 00:00:00'),
       ('cm-p6-tfcm', 'player-p6', 'club-tfcm', TIMESTAMP '2022-01-01 00:00:00'),
       ('cm-p7-tfcm', 'player-p7', 'club-tfcm', TIMESTAMP '2024-01-01 00:00:00'),
       ('cm-p8-tfcm', 'player-p8', 'club-tfcm', TIMESTAMP '2024-01-01 00:00:00');

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
-- Fixture-free demo group (docs/12-matchday-scheduling.md): a 3rd Herren tier, ready to
-- generate but never generated -- 2 placed teams, zero Round rows. Uses its own tier-level
-- rule set (rs-by-window, WINDOW mode) so this group demos the team propose/accept flow,
-- distinct from g-by25-1a above (DAY_BATCH, via the league's rs-by-std). Closes the gap noted
-- in matchday-round-creation-gap: no fixture-free 2+-team group existed to test generation
-- against once a scheduling_mode was actually required.
-- ---------------------------------------------------------------------------
INSERT INTO tier (id, league_id, name, level, rule_set_id)
VALUES ('ti-by25-3', 'lg-by25-h', '3. Bayernliga', 4, 'rs-by-window');

INSERT INTO comp_group (id, tier_id, name, group_state)
VALUES ('g-by25-3', 'ti-by25-3', '3. Bayernliga', 'PLANNED');

INSERT INTO team_participation (id, team_id, league_id, group_id, roster_status, status)
VALUES ('tp-by25-7', 'team-tfcm-3', 'lg-by25-h', 'g-by25-3', 'CONFIRMED', 'ACTIVE'),
       ('tp-by25-8', 'team-kfa-3', 'lg-by25-h', 'g-by25-3', 'CONFIRMED', 'ACTIVE');

-- League admin (usr-liga) scoped to the Bayernliga Herren league -- its identity lg-by25-h, so every
-- season-copy of it (2024/25, 2026, 2027/28; SPO-28). Simulates a region admin delegating one
-- league's day-to-day running: usr-liga gets region-admin-equivalent authority narrowed to that one
-- league, e.g. can manage its placements/rosters but not lg-by25-d or any other fed-by league.
INSERT INTO role_assignment (id, user_id, role, scope_type, scope_id, created_at)
VALUES ('ra-liga', 'usr-liga', 'LEAGUE_ADMIN', 'LEAGUE', 'lg-by25-h',
        TIMESTAMP '2024-01-01 00:00:00');

-- ---------------------------------------------------------------------------
-- A second region's league (Baden-Württemberg) — so placements/structure aren't
-- Bayern-only and switching regions shows genuinely different data.
-- ---------------------------------------------------------------------------
INSERT INTO league (id, league_identity_id, season_id, name, category_id)
VALUES ('lg-bw25', 'lg-bw25', 'season-bw25', 'Baden-Württemberg-Liga 2024/25', 'cat-herren');

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
INSERT INTO league (id, league_identity_id, season_id, name, category_id)
VALUES ('lg-cup', 'lg-cup', 'season-cup', 'Bayern-Pokal 2024', 'cat-open');

INSERT INTO tier (id, league_id, name, level)
VALUES ('ti-cup-grp', 'lg-cup', 'Gruppenphase', 1),
       ('ti-cup-fin', 'lg-cup', 'Finalrunde', 2);

INSERT INTO comp_group (id, tier_id, name, group_state)
VALUES ('g-cup-a', 'ti-cup-grp', 'Gruppe A', 'FINISHED'),
       ('g-cup-b', 'ti-cup-grp', 'Gruppe B', 'FINISHED'),
       ('g-cup-ko', 'ti-cup-fin', 'K.-o.-Runde', 'PLANNED');

INSERT INTO team_participation (id, team_id, league_id, group_id, roster_status, status)
VALUES ('tp-cup-1', 'tfcm1-cup', 'lg-cup', 'g-cup-a', 'CONFIRMED', 'ACTIVE'),
       ('tp-cup-2', 'kfa1-cup', 'lg-cup', 'g-cup-a', 'CONFIRMED', 'ACTIVE'),
       ('tp-cup-3', 'tfcm2-cup', 'lg-cup', 'g-cup-b', 'CONFIRMED', 'ACTIVE'),
       ('tp-cup-4', 'kfa2-cup', 'lg-cup', 'g-cup-b', 'CONFIRMED', 'ACTIVE');

-- ---------------------------------------------------------------------------
-- "Current" and "upcoming" examples for TFC München 1's team-rosters page
-- (frontend TeamRostersService.seasonBadge): a season whose date range brackets
-- "now" shows CURRENT regardless of the registration window; a season that hasn't
-- started yet but has an open registration window shows UPCOMING. Season dates
-- (start_date/end_date, used for the badge) are relative to a 2026-ish "today" —
-- adjust forward if this seed is still in use once these ranges are themselves
-- in the past.
-- ---------------------------------------------------------------------------
INSERT INTO league (id, league_identity_id, season_id, name, category_id, rule_set_id)
VALUES ('lg-2026-h', 'lg-by25-h', 'season-2026', 'Bayernliga Herren 2026', 'cat-herren', 'rs-by-std');

INSERT INTO tier (id, league_id, name, level)
VALUES ('ti-2026-1', 'lg-2026-h', '1. Bayernliga', 1);

INSERT INTO comp_group (id, tier_id, name, group_state)
VALUES ('g-2026-1', 'ti-2026-1', 'Gruppe A', 'RUNNING');

-- Roster already confirmed — the season is running, so registration/roster editing
-- for it is closed; TFC München 1 is mid-season.
INSERT INTO team_participation (id, team_id, league_id, group_id, roster_status, status)
VALUES ('tp-2026-1', 'tfcm1-2026', 'lg-2026-h', 'g-2026-1', 'CONFIRMED', 'ACTIVE');

INSERT INTO roster_entry (id, participation_id, player_id, added_at, removed_at)
VALUES ('re-2026-1', 'tp-2026-1', 'player-p1', TIMESTAMP '2026-01-15 10:00:00', NULL),
       ('re-2026-2', 'tp-2026-1', 'player-p2', TIMESTAMP '2026-01-15 10:00:00', NULL),
       ('re-2026-3', 'tp-2026-1', 'player-p3', TIMESTAMP '2026-01-15 10:00:00', NULL);

-- Next season: registration already open, but it hasn't started yet — no tier/group
-- structure set up either, since placements haven't run (mirrors tp-by25-4's
-- "registered but unplaced" shape). TFC München 1 has pre-registered; its roster is
-- still an empty DRAFT since the season is still a ways off.
INSERT INTO league (id, league_identity_id, season_id, name, category_id, rule_set_id)
VALUES ('lg-2027-h', 'lg-by25-h', 'season-2027', 'Bayernliga Herren 2027/28', 'cat-herren', 'rs-by-std');

INSERT INTO team_participation (id, team_id, league_id, group_id, roster_status, status)
VALUES ('tp-2027-1', 'tfcm1-2027', 'lg-2027-h', NULL, 'DRAFT', 'ACTIVE');

-- ---------------------------------------------------------------------------
-- A played-through current season for the tables and the result flow (SPO-22, SPO-15, SPO-100):
-- three more Bayernliga teams join g-2026-1, each with a confirmed roster of four players, and a
-- single round robin (3 rounds). Rounds 1 and 2 are played the way the app requires it: both
-- line-ups submitted (docs/23), every game scored, both captains confirmed -- so the result is
-- final (docs/17). Round 3 lies ahead and is untouched: no line-ups, no scores -- log in as `team`
-- (captain of TFC München 1) to submit a line-up and enter a result. Standings aren't stored; the
-- table is computed from these fixtures.
-- ---------------------------------------------------------------------------
INSERT INTO team (id, season_id, name, club_id, team_identity_id)
VALUES ('kfa1-2026', 'season-2026', 'Kickerfreunde Augsburg 1', 'club-kfa', 'tid-kfa-1'),
       ('kck1-2026', 'season-2026', '1. KC Köln 1', 'club-kck', 'team-kck-1'),
       ('dtk1-2026', 'season-2026', 'Dortmunder Tischkicker 1', 'club-dtk', 'team-dtk-1');

INSERT INTO player (id, first_name, last_name, nationality, national_id, birth_year, gender, national_license, active)
VALUES ('player-p10', 'Moritz', 'Becker', 'DE', '1010', 1992, 'MALE', 'B', TRUE),
       ('player-p11', 'David', 'Richter', 'DE', '1011', 1989, 'MALE', 'B', TRUE),
       ('player-p12', 'Jan', 'Klein', 'DE', '1012', 1994, 'MALE', 'B', TRUE),
       ('player-p13', 'Max', 'Wolf', 'DE', '1013', 1990, 'MALE', 'B', TRUE),
       ('player-p14', 'Leon', 'Schröder', 'DE', '1014', 1998, 'MALE', 'B', TRUE),
       ('player-p15', 'Ben', 'Neumann', 'DE', '1015', 1987, 'MALE', 'B', TRUE),
       ('player-p16', 'Tobias', 'Schwarz', 'DE', '1016', 1993, 'MALE', 'B', TRUE),
       ('player-p17', 'Simon', 'Zimmermann', 'DE', '1017', 1991, 'MALE', 'B', TRUE),
       ('player-p18', 'Erik', 'Braun', 'DE', '1018', 1996, 'MALE', 'B', TRUE),
       ('player-p19', 'Florian', 'Krüger', 'DE', '1019', 1985, 'MALE', 'B', TRUE);

INSERT INTO club_membership (id, player_id, club_id, joined_at)
VALUES ('cm-p10-kfa', 'player-p10', 'club-kfa', TIMESTAMP '2025-06-01 00:00:00'),
       ('cm-p11-kfa', 'player-p11', 'club-kfa', TIMESTAMP '2025-06-01 00:00:00'),
       ('cm-p12-kck', 'player-p12', 'club-kck', TIMESTAMP '2025-06-01 00:00:00'),
       ('cm-p13-kck', 'player-p13', 'club-kck', TIMESTAMP '2025-06-01 00:00:00'),
       ('cm-p14-kck', 'player-p14', 'club-kck', TIMESTAMP '2025-06-01 00:00:00'),
       ('cm-p15-kck', 'player-p15', 'club-kck', TIMESTAMP '2025-06-01 00:00:00'),
       ('cm-p16-dtk', 'player-p16', 'club-dtk', TIMESTAMP '2025-06-01 00:00:00'),
       ('cm-p17-dtk', 'player-p17', 'club-dtk', TIMESTAMP '2025-06-01 00:00:00'),
       ('cm-p18-dtk', 'player-p18', 'club-dtk', TIMESTAMP '2025-06-01 00:00:00'),
       ('cm-p19-dtk', 'player-p19', 'club-dtk', TIMESTAMP '2025-06-01 00:00:00');

INSERT INTO team_participation (id, team_id, league_id, group_id, roster_status, status)
VALUES ('tp-2026-2', 'kfa1-2026', 'lg-2026-h', 'g-2026-1', 'CONFIRMED', 'ACTIVE'),
       ('tp-2026-3', 'kck1-2026', 'lg-2026-h', 'g-2026-1', 'CONFIRMED', 'ACTIVE'),
       ('tp-2026-4', 'dtk1-2026', 'lg-2026-h', 'g-2026-1', 'CONFIRMED', 'ACTIVE');

INSERT INTO roster_entry (id, participation_id, player_id, added_at, removed_at)
VALUES ('re-2026-4', 'tp-2026-1', 'player-p6', TIMESTAMP '2026-01-15 10:00:00', NULL),
       ('re-2026-5', 'tp-2026-2', 'player-p4', TIMESTAMP '2026-01-20 10:00:00', NULL),
       ('re-2026-6', 'tp-2026-2', 'player-p5', TIMESTAMP '2026-01-20 10:00:00', NULL),
       ('re-2026-7', 'tp-2026-2', 'player-p10', TIMESTAMP '2026-01-20 10:00:00', NULL),
       ('re-2026-8', 'tp-2026-2', 'player-p11', TIMESTAMP '2026-01-20 10:00:00', NULL),
       ('re-2026-9', 'tp-2026-3', 'player-p12', TIMESTAMP '2026-01-20 10:00:00', NULL),
       ('re-2026-10', 'tp-2026-3', 'player-p13', TIMESTAMP '2026-01-20 10:00:00', NULL),
       ('re-2026-11', 'tp-2026-3', 'player-p14', TIMESTAMP '2026-01-20 10:00:00', NULL),
       ('re-2026-12', 'tp-2026-3', 'player-p15', TIMESTAMP '2026-01-20 10:00:00', NULL),
       ('re-2026-13', 'tp-2026-4', 'player-p16', TIMESTAMP '2026-01-20 10:00:00', NULL),
       ('re-2026-14', 'tp-2026-4', 'player-p17', TIMESTAMP '2026-01-20 10:00:00', NULL),
       ('re-2026-15', 'tp-2026-4', 'player-p18', TIMESTAMP '2026-01-20 10:00:00', NULL),
       ('re-2026-16', 'tp-2026-4', 'player-p19', TIMESTAMP '2026-01-20 10:00:00', NULL);

INSERT INTO round (id, group_id, name, round_index)
VALUES ('rd-2026-1', 'g-2026-1', 'Spieltag 1', 1),
       ('rd-2026-2', 'g-2026-1', 'Spieltag 2', 2),
       ('rd-2026-3', 'g-2026-1', 'Spieltag 3', 3);

INSERT INTO match_day (id, round_id, team_home_id, team_away_id, start_date, result_state, scheduling_state,
                       submitted_by_dtfb_id, home_confirmed_at, away_confirmed_at, decided_at, first_final_at)
VALUES ('md-26-1a', 'rd-2026-1', 'tfcm1-2026', 'dtk1-2026', TIMESTAMP '2026-09-19 11:00:00', 'CONFIRMED', 'CONFIRMED', 'team', TIMESTAMP '2026-09-19 12:54:00', TIMESTAMP '2026-09-19 12:57:00', TIMESTAMP '2026-09-19 12:50:00', TIMESTAMP '2026-09-19 12:57:00'),
       ('md-26-1b', 'rd-2026-1', 'kfa1-2026', 'kck1-2026', TIMESTAMP '2026-09-19 14:00:00', 'CONFIRMED', 'CONFIRMED', NULL, TIMESTAMP '2026-09-19 15:54:00', TIMESTAMP '2026-09-19 15:57:00', TIMESTAMP '2026-09-19 15:50:00', TIMESTAMP '2026-09-19 15:57:00'),
       ('md-26-2a', 'rd-2026-2', 'kck1-2026', 'tfcm1-2026', TIMESTAMP '2026-10-03 11:00:00', 'CONFIRMED', 'CONFIRMED', 'team', TIMESTAMP '2026-10-03 12:54:00', TIMESTAMP '2026-10-03 12:57:00', TIMESTAMP '2026-10-03 12:50:00', TIMESTAMP '2026-10-03 12:57:00'),
       ('md-26-2b', 'rd-2026-2', 'dtk1-2026', 'kfa1-2026', TIMESTAMP '2026-10-03 14:00:00', 'CONFIRMED', 'CONFIRMED', NULL, TIMESTAMP '2026-10-03 15:54:00', TIMESTAMP '2026-10-03 15:57:00', TIMESTAMP '2026-10-03 15:50:00', TIMESTAMP '2026-10-03 15:57:00'),
       ('md-26-3a', 'rd-2026-3', 'tfcm1-2026', 'kfa1-2026', TIMESTAMP '2026-10-17 11:00:00', 'OPEN', 'CONFIRMED', NULL, NULL, NULL, NULL, NULL),
       ('md-26-3b', 'rd-2026-3', 'dtk1-2026', 'kck1-2026', TIMESTAMP '2026-10-17 14:00:00', 'OPEN', 'CONFIRMED', NULL, NULL, NULL, NULL, NULL);

INSERT INTO match_game (id, match_day_id, position, type, start_time, home_score, away_score, state, winner)
VALUES ('mg-26-1a-1', 'md-26-1a', 1, 'DOUBLE', TIMESTAMP '2026-09-19 11:00:00', 7, 5, 'PLAYED', 'HOME'),
       ('mg-26-1a-2', 'md-26-1a', 2, 'DOUBLE', TIMESTAMP '2026-09-19 11:00:00', 4, 7, 'PLAYED', 'AWAY'),
       ('mg-26-1a-3', 'md-26-1a', 3, 'SINGLE', TIMESTAMP '2026-09-19 11:00:00', 7, 6, 'PLAYED', 'HOME'),
       ('mg-26-1b-1', 'md-26-1b', 1, 'DOUBLE', TIMESTAMP '2026-09-19 14:00:00', 7, 3, 'PLAYED', 'HOME'),
       ('mg-26-1b-2', 'md-26-1b', 2, 'DOUBLE', TIMESTAMP '2026-09-19 14:00:00', 5, 7, 'PLAYED', 'AWAY'),
       ('mg-26-1b-3', 'md-26-1b', 3, 'SINGLE', TIMESTAMP '2026-09-19 14:00:00', 2, 7, 'PLAYED', 'AWAY'),
       ('mg-26-2a-1', 'md-26-2a', 1, 'DOUBLE', TIMESTAMP '2026-10-03 11:00:00', 7, 4, 'PLAYED', 'HOME'),
       ('mg-26-2a-2', 'md-26-2a', 2, 'DOUBLE', TIMESTAMP '2026-10-03 11:00:00', 7, 5, 'PLAYED', 'HOME'),
       ('mg-26-2a-3', 'md-26-2a', 3, 'SINGLE', TIMESTAMP '2026-10-03 11:00:00', 6, 7, 'PLAYED', 'AWAY'),
       ('mg-26-2b-1', 'md-26-2b', 1, 'DOUBLE', TIMESTAMP '2026-10-03 14:00:00', 7, 6, 'PLAYED', 'HOME'),
       ('mg-26-2b-2', 'md-26-2b', 2, 'DOUBLE', TIMESTAMP '2026-10-03 14:00:00', 7, 2, 'PLAYED', 'HOME'),
       ('mg-26-2b-3', 'md-26-2b', 3, 'SINGLE', TIMESTAMP '2026-10-03 14:00:00', 3, 7, 'PLAYED', 'AWAY'),
       ('mg-26-3a-1', 'md-26-3a', 1, 'DOUBLE', TIMESTAMP '2026-10-17 11:00:00', NULL, NULL, 'PLANNED', NULL),
       ('mg-26-3a-2', 'md-26-3a', 2, 'DOUBLE', TIMESTAMP '2026-10-17 11:00:00', NULL, NULL, 'PLANNED', NULL),
       ('mg-26-3a-3', 'md-26-3a', 3, 'SINGLE', TIMESTAMP '2026-10-17 11:00:00', NULL, NULL, 'PLANNED', NULL),
       ('mg-26-3b-1', 'md-26-3b', 1, 'DOUBLE', TIMESTAMP '2026-10-17 14:00:00', NULL, NULL, 'PLANNED', NULL),
       ('mg-26-3b-2', 'md-26-3b', 2, 'DOUBLE', TIMESTAMP '2026-10-17 14:00:00', NULL, NULL, 'PLANNED', NULL),
       ('mg-26-3b-3', 'md-26-3b', 3, 'SINGLE', TIMESTAMP '2026-10-17 14:00:00', NULL, NULL, 'PLANNED', NULL);

INSERT INTO lineup (id, match_day_id, team_id, submitted_at, submitted_by_dtfb_id)
VALUES ('lu-26-1a-h', 'md-26-1a', 'tfcm1-2026', TIMESTAMP '2026-09-19 10:30:00', 'team'),
       ('lu-26-1a-a', 'md-26-1a', 'dtk1-2026', TIMESTAMP '2026-09-19 10:30:00', NULL),
       ('lu-26-1b-h', 'md-26-1b', 'kfa1-2026', TIMESTAMP '2026-09-19 13:30:00', NULL),
       ('lu-26-1b-a', 'md-26-1b', 'kck1-2026', TIMESTAMP '2026-09-19 13:30:00', NULL),
       ('lu-26-2a-h', 'md-26-2a', 'kck1-2026', TIMESTAMP '2026-10-03 10:30:00', NULL),
       ('lu-26-2a-a', 'md-26-2a', 'tfcm1-2026', TIMESTAMP '2026-10-03 10:30:00', 'team'),
       ('lu-26-2b-h', 'md-26-2b', 'dtk1-2026', TIMESTAMP '2026-10-03 13:30:00', NULL),
       ('lu-26-2b-a', 'md-26-2b', 'kfa1-2026', TIMESTAMP '2026-10-03 13:30:00', NULL);

INSERT INTO lineup_entry (id, lineup_id, match_id, slot, player_id)
VALUES ('le-26-1a-h11', 'lu-26-1a-h', 'mg-26-1a-1', 1, 'player-p1'),
       ('le-26-1a-h12', 'lu-26-1a-h', 'mg-26-1a-1', 2, 'player-p2'),
       ('le-26-1a-h21', 'lu-26-1a-h', 'mg-26-1a-2', 1, 'player-p3'),
       ('le-26-1a-h22', 'lu-26-1a-h', 'mg-26-1a-2', 2, 'player-p6'),
       ('le-26-1a-h31', 'lu-26-1a-h', 'mg-26-1a-3', 1, 'player-p1'),
       ('le-26-1a-a11', 'lu-26-1a-a', 'mg-26-1a-1', 1, 'player-p16'),
       ('le-26-1a-a12', 'lu-26-1a-a', 'mg-26-1a-1', 2, 'player-p17'),
       ('le-26-1a-a21', 'lu-26-1a-a', 'mg-26-1a-2', 1, 'player-p18'),
       ('le-26-1a-a22', 'lu-26-1a-a', 'mg-26-1a-2', 2, 'player-p19'),
       ('le-26-1a-a31', 'lu-26-1a-a', 'mg-26-1a-3', 1, 'player-p16'),
       ('le-26-1b-h11', 'lu-26-1b-h', 'mg-26-1b-1', 1, 'player-p4'),
       ('le-26-1b-h12', 'lu-26-1b-h', 'mg-26-1b-1', 2, 'player-p5'),
       ('le-26-1b-h21', 'lu-26-1b-h', 'mg-26-1b-2', 1, 'player-p10'),
       ('le-26-1b-h22', 'lu-26-1b-h', 'mg-26-1b-2', 2, 'player-p11'),
       ('le-26-1b-h31', 'lu-26-1b-h', 'mg-26-1b-3', 1, 'player-p4'),
       ('le-26-1b-a11', 'lu-26-1b-a', 'mg-26-1b-1', 1, 'player-p12'),
       ('le-26-1b-a12', 'lu-26-1b-a', 'mg-26-1b-1', 2, 'player-p13'),
       ('le-26-1b-a21', 'lu-26-1b-a', 'mg-26-1b-2', 1, 'player-p14'),
       ('le-26-1b-a22', 'lu-26-1b-a', 'mg-26-1b-2', 2, 'player-p15'),
       ('le-26-1b-a31', 'lu-26-1b-a', 'mg-26-1b-3', 1, 'player-p12'),
       ('le-26-2a-h11', 'lu-26-2a-h', 'mg-26-2a-1', 1, 'player-p12'),
       ('le-26-2a-h12', 'lu-26-2a-h', 'mg-26-2a-1', 2, 'player-p13'),
       ('le-26-2a-h21', 'lu-26-2a-h', 'mg-26-2a-2', 1, 'player-p14'),
       ('le-26-2a-h22', 'lu-26-2a-h', 'mg-26-2a-2', 2, 'player-p15'),
       ('le-26-2a-h31', 'lu-26-2a-h', 'mg-26-2a-3', 1, 'player-p14'),
       ('le-26-2a-a11', 'lu-26-2a-a', 'mg-26-2a-1', 1, 'player-p1'),
       ('le-26-2a-a12', 'lu-26-2a-a', 'mg-26-2a-1', 2, 'player-p2'),
       ('le-26-2a-a21', 'lu-26-2a-a', 'mg-26-2a-2', 1, 'player-p3'),
       ('le-26-2a-a22', 'lu-26-2a-a', 'mg-26-2a-2', 2, 'player-p6'),
       ('le-26-2a-a31', 'lu-26-2a-a', 'mg-26-2a-3', 1, 'player-p3'),
       ('le-26-2b-h11', 'lu-26-2b-h', 'mg-26-2b-1', 1, 'player-p16'),
       ('le-26-2b-h12', 'lu-26-2b-h', 'mg-26-2b-1', 2, 'player-p17'),
       ('le-26-2b-h21', 'lu-26-2b-h', 'mg-26-2b-2', 1, 'player-p18'),
       ('le-26-2b-h22', 'lu-26-2b-h', 'mg-26-2b-2', 2, 'player-p19'),
       ('le-26-2b-h31', 'lu-26-2b-h', 'mg-26-2b-3', 1, 'player-p18'),
       ('le-26-2b-a11', 'lu-26-2b-a', 'mg-26-2b-1', 1, 'player-p4'),
       ('le-26-2b-a12', 'lu-26-2b-a', 'mg-26-2b-1', 2, 'player-p5'),
       ('le-26-2b-a21', 'lu-26-2b-a', 'mg-26-2b-2', 1, 'player-p10'),
       ('le-26-2b-a22', 'lu-26-2b-a', 'mg-26-2b-2', 2, 'player-p11'),
       ('le-26-2b-a31', 'lu-26-2b-a', 'mg-26-2b-3', 1, 'player-p10');

-- ---------------------------------------------------------------------------
-- Root-federation (DTFB) demo: a Bundesliga season/league under fed-dtfb, and a SECOND team
-- for TFC München -- same club (club-tfcm) as its regional tid-tfcm-1 team above, but its own
-- team_identity_id, own season/league. Demonstrates a club fielding independent teams across
-- two federations (docs/16-root-federation.md): they may, but don't have to, share players --
-- here the Bundesliga roster reuses player-p1 (already an active club-tfcm member) alongside a
-- player who isn't on the regional team's roster.
-- ---------------------------------------------------------------------------
INSERT INTO season (id, name, federation_id, start_date, end_date, registration_opens_at, registration_closes_at)
VALUES ('season-bl', 'Bundesliga-Saison 2026', 'fed-dtfb', DATE '2026-01-01', DATE '2026-12-31',
        DATE '2025-10-01', DATE '2025-12-31');

INSERT INTO league (id, league_identity_id, season_id, name, category_id)
VALUES ('lg-bl-h', 'lg-bl-h', 'season-bl', 'Bundesliga Herren 2026', 'cat-herren');

INSERT INTO tier (id, league_id, name, level)
VALUES ('ti-bl-1', 'lg-bl-h', 'Bundesliga', 1);

INSERT INTO comp_group (id, tier_id, name, group_state)
VALUES ('g-bl-1', 'ti-bl-1', 'Bundesliga', 'RUNNING');

INSERT INTO team (id, season_id, name, club_id, team_identity_id)
VALUES ('tfcm-bl-1', 'season-bl', 'TFC München', 'club-tfcm', 'tid-tfcm-bl');

INSERT INTO team_participation (id, team_id, league_id, group_id, roster_status, status)
VALUES ('tp-bl-1', 'tfcm-bl-1', 'lg-bl-h', 'g-bl-1', 'CONFIRMED', 'ACTIVE');

INSERT INTO roster_entry (id, participation_id, player_id, added_at, removed_at)
VALUES ('re-bl-1', 'tp-bl-1', 'player-p1', TIMESTAMP '2026-01-15 10:00:00', NULL),
       ('re-bl-2', 'tp-bl-1', 'player-p6', TIMESTAMP '2026-01-15 10:00:00', NULL);
