-- noinspection SqlResolveForFile

INSERT INTO SEASON (UUID, NAME)
VALUES ('11111111-1111-1111-1111-111111111111', '2026'),
       ('22222222-2222-2222-2222-222222222222', '2025');

INSERT INTO EVENT (SEASON_ID, UUID, NAME)
VALUES (1, '65a345f5-255a-43dc-8f46-f6e33c4834e5', 'Turnier1'),
       (1, '2447275a-b705-43f2-acf1-6cda078f9ba7', 'Turnier2'),
       (1, '62165f8a-4e73-4520-ac36-02c27e3a497e', 'Turnier3'),
       (2, '889f3e6c-17d5-4fd4-9bdb-b0d6b4cee7bc', 'TurnierAlt');

INSERT INTO DISCIPLINE (EVENT_ID, UUID, NAME, SHORT_NAME)
VALUES (1, 'cae720a0-5b7c-4330-a207-839a8fb04d68', 'Offenes Doppel', 'OD'),
       (1, '309d28bf-40e3-4505-b1b8-7700985d927a', 'Offenes Einzel', 'OE'),
       (1, 'b22e6eab-7022-41d3-9f40-66cf5bd05682', 'Damen Doppel', 'DD'),
       (2, '8b49b235-0467-42f1-beb9-f6d7e885e15d', 'Offenes Doppel', 'OD');

INSERT INTO STAGE (DISCIPLINE_ID, UUID, NAME)
VALUES (1, '24c50a86-443c-4c73-b286-3f3cd7839a3f', 'Vorrunde'),
       (1, '59dacaf3-5eda-4165-a563-a27483c3dd58', 'Hauptrunde'),
       (2, 'd6c828b5-5956-4fc9-a119-486ce4a50ae1', 'Vorrunde'),
       (2, '9157b9f4-e9bb-4637-a7a9-bca25e54f41d', 'Hauptrunde');

INSERT INTO PHASE (STAGE_ID, UUID, NAME, TOURNAMENT_MODE, PHASE_STATE)
VALUES (1, '8ddf3628-2ba2-42d0-83cd-0977aaa0ed4d', 'Phase Irgendwas?', 'SWISS', 'PLACEHOLDER'),
       (2, 'd809d2b7-7e29-4b67-940d-0d1c9bca57e6', 'Zweite Phase', 'DOUBLE_ELIMINATION', 'PLACEHOLDER');

INSERT INTO LOCATION (UUID, NAME, ADDRESS)
VALUES ('46611dbc-79f4-4fe8-aaa9-abf49896f9b4', 'Tante Käthe', 'Bernauer Str. 63-64, 13355 Berlin-Prenzlauer Berg'),
       ('e1c8113d-a0a6-4ab8-8612-054e5e91462b', 'Kaffeehaus', 'Angelburger Str. 20, 24937 Flensburg'),
       ('7a5e1bfa-d4da-4c75-a189-a88816af6bb5', 'FC St. Pauli', 'Harald-Stender-Platz 1, 20359 Hamburg');
