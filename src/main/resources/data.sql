-- noinspection SqlResolveForFile

INSERT INTO SEASON (ID, NAME)
VALUES ('11111111-1111-1111-1111-111111111111', '2026'),
       ('22222222-2222-2222-2222-222222222222', '2025');

INSERT INTO EVENT (ID, SEASON_ID, NAME)
VALUES ('65a345f5-255a-43dc-8f46-f6e33c4834e5', '11111111-1111-1111-1111-111111111111', 'Turnier1'),
       ('2447275a-b705-43f2-acf1-6cda078f9ba7', '11111111-1111-1111-1111-111111111111', 'Turnier2'),
       ('62165f8a-4e73-4520-ac36-02c27e3a497e', '11111111-1111-1111-1111-111111111111', 'Turnier3'),
       ('889f3e6c-17d5-4fd4-9bdb-b0d6b4cee7bc', '22222222-2222-2222-2222-222222222222', 'TurnierAlt');

INSERT INTO DISCIPLINE (ID, EVENT_ID, NAME, SHORT_NAME)
VALUES ('cae720a0-5b7c-4330-a207-839a8fb04d68', '65a345f5-255a-43dc-8f46-f6e33c4834e5', 'Offenes Doppel', 'OD'),
       ('309d28bf-40e3-4505-b1b8-7700985d927a', '65a345f5-255a-43dc-8f46-f6e33c4834e5', 'Offenes Einzel', 'OE'),
       ('b22e6eab-7022-41d3-9f40-66cf5bd05682', '65a345f5-255a-43dc-8f46-f6e33c4834e5', 'Damen Doppel', 'DD'),
       ('8b49b235-0467-42f1-beb9-f6d7e885e15d', '2447275a-b705-43f2-acf1-6cda078f9ba7', 'Offenes Doppel', 'OD');

INSERT INTO STAGE (ID, DISCIPLINE_ID, NAME)
VALUES ('24c50a86-443c-4c73-b286-3f3cd7839a3f', 'cae720a0-5b7c-4330-a207-839a8fb04d68', 'Vorrunde'),
       ('59dacaf3-5eda-4165-a563-a27483c3dd58', 'cae720a0-5b7c-4330-a207-839a8fb04d68', 'Hauptrunde'),
       ('d6c828b5-5956-4fc9-a119-486ce4a50ae1', '309d28bf-40e3-4505-b1b8-7700985d927a', 'Vorrunde'),
       ('9157b9f4-e9bb-4637-a7a9-bca25e54f41d', '309d28bf-40e3-4505-b1b8-7700985d927a', 'Hauptrunde');

INSERT INTO POOL (ID, STAGE_ID, NAME, TOURNAMENT_MODE, POOL_STATE)
VALUES ('8ddf3628-2ba2-42d0-83cd-0977aaa0ed4d', '24c50a86-443c-4c73-b286-3f3cd7839a3f', 'Profi', 'SWISS', 'READY'),
       ('d809d2b7-7e29-4b67-940d-0d1c9bca57e6', '59dacaf3-5eda-4165-a563-a27483c3dd58', 'Amateur', 'DOUBLE_ELIMINATION',
        'RUNNING');

INSERT INTO ROUND (ID, POOL_ID, NAME, INDEX)
VALUES ('086ce72b-496f-4941-b9a3-4fedacc77f32', '8ddf3628-2ba2-42d0-83cd-0977aaa0ed4d', 'Runde1', 1),
       ('d8554ce1-c315-4c31-b466-5bb700c1c88d', '8ddf3628-2ba2-42d0-83cd-0977aaa0ed4d', 'Runde2', 2);

INSERT INTO LOCATION (ID, NAME, ADDRESS)
VALUES ('46611dbc-79f4-4fe8-aaa9-abf49896f9b4', 'Tante Käthe', 'Bernauer Str. 63-64, 13355 Berlin-Prenzlauer Berg'),
       ('e1c8113d-a0a6-4ab8-8612-054e5e91462b', 'Kaffeehaus', 'Angelburger Str. 20, 24937 Flensburg'),
       ('7a5e1bfa-d4da-4c75-a189-a88816af6bb5', 'FC St. Pauli', 'Harald-Stender-Platz 1, 20359 Hamburg');

INSERT INTO TEAM (ID, NAME)
VALUES ('150de2b8-2e2b-42f8-ad6b-7a0bb690d124', 'Monsterblock'),
       ('fc95d590-b476-435b-a3d2-426c5aa51d0f', 'Foos and Furious'),
       ('e7385cff-3d4b-4bfa-8013-eaf62eb7b088', 'Pin Diesel');

INSERT INTO MATCH_DAY (ID, NAME, ROUND_ID, LOCATION_ID, TEAM_HOME_ID, TEAM_AWAY_ID, START_DATE, END_DATE)
VALUES ('edcdd1a7-7194-4481-a7f5-edd03d7f53d8', 'Matchday 1', '086ce72b-496f-4941-b9a3-4fedacc77f32',
        '46611dbc-79f4-4fe8-aaa9-abf49896f9b4', '150de2b8-2e2b-42f8-ad6b-7a0bb690d124',
        'fc95d590-b476-435b-a3d2-426c5aa51d0f',
        '2026-01-02T20:10:44.793855800Z',
        '2026-01-02T20:11:29.371065800Z'),
       ('b91b214f-812d-488a-b868-6ab10b7a49f8', 'Matchday 2', '086ce72b-496f-4941-b9a3-4fedacc77f32',
        '46611dbc-79f4-4fe8-aaa9-abf49896f9b4', 'fc95d590-b476-435b-a3d2-426c5aa51d0f',
        'e7385cff-3d4b-4bfa-8013-eaf62eb7b088',
        '2026-01-02T20:11:29.371065800Z',
        '2026-01-02T20:11:29.371065800Z'),
       ('b8b6eaa0-700f-4540-bec9-fc2d628f2bfc', 'Matchday 3', 'd8554ce1-c315-4c31-b466-5bb700c1c88d',
        '7a5e1bfa-d4da-4c75-a189-a88816af6bb5', 'e7385cff-3d4b-4bfa-8013-eaf62eb7b088',
        '150de2b8-2e2b-42f8-ad6b-7a0bb690d124',
        '2026-01-02T20:11:29.371065800Z',
        NULL);

INSERT INTO MATCH (ID, MATCH_DAY_ID, HOME_SCORE, AWAY_SCORE, STATE, TYPE, START_TIME, END_TIME)
VALUES ('33866ba1-9611-479c-9915-da475c6c905a', 'edcdd1a7-7194-4481-a7f5-edd03d7f53d8', 2, 0, 'PLAYED', 'DOUBLE',
        '2026-01-02T22:02:20.200061800Z',
        '2026-01-02T22:02:20.200061800Z'),
       ('b7d321a2-b09d-40b7-93fa-c9b3f173e999', 'b91b214f-812d-488a-b868-6ab10b7a49f8', 1, 1, 'PLAYED', 'DOUBLE',
        '2026-01-02T22:29:28.550246Z',
        '2026-01-02T22:29:28.550246Z'),
       ('b2df1422-da18-491c-b960-cdf679fd345e', 'b8b6eaa0-700f-4540-bec9-fc2d628f2bfc', 0, 0, 'RUNNING', 'DOUBLE',
        '2026-01-02T22:29:28.550246Z',
        NULL);

INSERT INTO MATCH_SET (ID, MATCH_ID, SET_NUMBER, HOME_SCORE, AWAY_SCORE)
VALUES ('0cc33cf5-49a5-47f7-b222-ee35a0aa7d52', '33866ba1-9611-479c-9915-da475c6c905a', 1, 5, 2),
       ('332f34ae-1c99-40f3-b3b4-585e1c6a645e', '33866ba1-9611-479c-9915-da475c6c905a', 2, 5, 0),
       ('8f666e6d-d1d7-4c3c-b833-901464e9c49d', 'b7d321a2-b09d-40b7-93fa-c9b3f173e999', 1, 5, 4),
       ('76501db6-015f-4723-90c1-9b73df2bb921', 'b7d321a2-b09d-40b7-93fa-c9b3f173e999', 2, 1, 5),
       ('9bde9ec9-6b2a-4f58-88f8-59ca8e32f7d1', 'b2df1422-da18-491c-b960-cdf679fd345e', 1, 0, 3);

INSERT INTO MATCH_EVENT (ID, MATCH_ID, TEAM_ID, HOME_SCORE, AWAY_SCORE, PLAYER_UUID, TIMESTAMP, TYPE, JSON)
VALUES ('0c64d5d2-e66b-442a-8ff2-b8986a67aa08', '33866ba1-9611-479c-9915-da475c6c905a',
        '150de2b8-2e2b-42f8-ad6b-7a0bb690d124', 5, 2, 'eab5db11-831c-488b-9eaa-87684ad0ed28',
        '2026-01-04T17:53:17.963088100Z', 'GOAL', '{
        "name": "test"
    }'),
       ('e8140c21-1099-442e-a4c2-f5df4d8e8f6c', '33866ba1-9611-479c-9915-da475c6c905a',
        'fc95d590-b476-435b-a3d2-426c5aa51d0f', 5, 0, '36d8195b-f9f8-4fa0-84ee-056d14fdc14e',
        '2026-01-04T17:53:17.963088100Z', 'OWN_GOAL', '{
           "name": "test"
       }'),
       ('6bdbd954-8532-445f-b386-313794d37853', 'b2df1422-da18-491c-b960-cdf679fd345e',
        '150de2b8-2e2b-42f8-ad6b-7a0bb690d124', 0, 3, '93eceefe-6fd8-4472-a652-8e2944bb3ad3',
        '2026-01-04T17:53:17.963088100Z', 'START', '{
           "name": "test"
       }');
