# noinspection SqlResolveForFile

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

-- Bootstrap global admin (dtfb_id "admin" matches the Keycloak dev user).
INSERT INTO player (id, dtfb_id, first_name, last_name, nationality, national_license, active)
VALUES ('player-admin', 'admin', 'DTFB', 'Administrator', 'DE', 'A', TRUE);

INSERT INTO role_assignment (id, player_id, role, scope_type, scope_id, created_at)
VALUES ('ra-admin-glob', 'player-admin', 'ADMIN', 'GLOBAL', NULL, TIMESTAMP '2024-01-01 00:00:00');
