-- noinspection SqlResolveForFile

INSERT INTO SEASON (UUID, NAME)
VALUES ('11111111-1111-1111-1111-111111111111', '2026'),
       ('22222222-2222-2222-2222-222222222222', '2025');

INSERT INTO EVENT (SEASON_ID, UUID, NAME)
VALUES (1, '65a345f5-255a-43dc-8f46-f6e33c4834e5', 'Turnier1'),
       (1, '2447275a-b705-43f2-acf1-6cda078f9ba7', 'Turnier2'),
       (1, '62165f8a-4e73-4520-ac36-02c27e3a497e', 'Turnier3'),
       (2, '889f3e6c-17d5-4fd4-9bdb-b0d6b4cee7bc', 'TurnierAlt');
