# Fixture modes — Race to 42 as a rule profile, with a confirmation deadline

> **Decision 2026-09-29 by Marvin, to confirm with the competition management (Daniel, SPO-58).
> Built 2026-09-29, backend and frontend (§Implementation).** A rule set gets a **`fixtureMode`** that picks how a fixture is played and
> decided: **`RACE`** (Race to N — one running score over the game plan's segments, used by the
> Regionalliga and the Bundesliga; the M1 mode) or **`GAMES`** (separate games with sets — later, out
> of scope for now). The mode is a *profile*: it decides which rule fields apply and are shown. For
> `RACE`, each segment ends as soon as one side's running total reaches the next step (6, 12 … 42);
> the result page enters the running score after each segment and checks it. A rule set also gets a
> **confirmation deadline** (e.g. 15 min) that is enforced: after it, only the tournament management
> can confirm. Byes become scored fixtures (score per blueprint, e.g. 42 : 30); the table is ordered points → goal difference →
> head-to-head.
>
> See also: [17-result-entry-confirmation.md](./17-result-entry-confirmation.md) (who enters and
> confirms, the "decided" rule this doc extends), [21-rule-set-blueprints.md](./21-rule-set-blueprints.md)
> (blueprints and per-league snapshots — the profiles live there), [12-matchday-scheduling.md](./12-matchday-scheduling.md)
> (the generator that has to create bye fixtures), [09-league-model.md](./09-league-model.md) §3
> (the `LeagueRuleSet` fields), [16-root-federation.md](./16-root-federation.md) (the DTFB root
> federation, whose admins are the tournament management).
>
> Question raised: the game plan isn't the only thing shaping a result — how is a fixture actually
> played and decided in the Regionalliga, and how do different modes (best of 3/5, Race to 42) fit
> into the rule set?
> Short answer: **a `fixtureMode` profile on the rule set; `RACE` with per-segment steps first,
> `GAMES` later; plus an enforced confirmation deadline.**

## Context

Result entry (doc 17, SPO-15/57) treats a fixture as a list of games with one score each, decided
under `matchdayDecision` (`ALL_GAMES` / `FIRST_TO` N game wins). The rule set also carries
`setsPerGame` and `pointsToWinSet`, which nothing uses. That fits neither real mode:

- **Race to 42** — the Regionalliga and Bundesliga mode — isn't a set of separate games at all but
  one running score split into segments.
- **Game-based modes** (best of 3, best of 5, a fixed number of sets) need sets per game, which the
  result page doesn't have.

Sources: *Regularien der Damen- und Herren-Bundesligen 2026* (§7–§9) and *Informationen zum
Spielmodus und -ablauf, Regionalliga Damen 2026* (§2–§10), both published on dtfb.de. The upcoming
Regionalliga (M1, 20./21.03.2027) may differ slightly — hence the confirmation step below.

What the Regionalliga Damen 2026 rules say:

- A fixture is a **Race to 42** in **7 segments** of six points, order **D1, D2, D3, S1, D4, S2, D5**
  (5 doubles, 2 singles).
- **Vorrunde:** always played to the end, can end **41 : 41** (draw). **Hauptrunde / knock-out:** the
  winner needs **2 points' difference**.
- **Table points:** win 2, draw 1 (Bundesliga §9). **Bye:** 42 : 30. Segments not played or
  abandoned count "zu 0" for the opponent.
- **Table order:** points → goal difference → head-to-head → lot (placement range) / penalty
  (promotion/relegation range).
- **Confirmation:** both captains within **15 minutes** of the end, otherwise the tournament
  management confirms; no result after 15 minutes → the fixture is struck. Changes only the same day,
  before the next fixture, with the tournament management.
- **Format:** 10 teams, 2 groups of 5, 5 preliminary games each (one bye each), then playoffs
  (semi-finals, placement games, game for 3rd). The playoffs are a separate topic (SPO-99).

## Options considered

| | How | Verdict |
|---|-----|---------|
| **Mode as a profile (`fixtureMode`) on the rule set (chosen)** | One dropdown at the top of the rule-set dialog; it decides which fields apply and are shown | **Chosen.** Race to N and game-based modes need different fields; a profile keeps each dialog small and makes the seeded blueprints ("Race to 42 (DTFB)") self-explanatory |
| Stretch the existing fields | Express Race to 42 through `matchdayDecision`/`setsPerGame` | Rejected — a running score over segments isn't "N game wins", and the fields would mean different things per league |
| Name it `matchMode` | — | Rejected — in the code `Match` is a single *game* (table `match_game`); the mode describes the whole fixture (`MatchDay`) |
| **Enter the running score after each segment (chosen)** | D1 6 : 4, D2 12 : 9 … as on the paper sheet | **Chosen.** Matches the sheet and the tournament app, and the step rule can be checked directly |
| Enter the goals scored per segment | D1 6 : 4, D2 6 : 5 … | Rejected — everyone reads and writes the running score on site; per-segment goals would have to be converted in the head |
| **Scored bye fixture (chosen)** | The generator creates a fixture against the bye, scored 42 : 30 | **Chosen.** A bye counts as a win in the table; skipping it (today's generator) loses those points |
| **Deadline enforced (chosen)** | After the deadline teams can't confirm; the tournament management confirms or strikes | **Chosen** (Marvin) over "only flag it" — mirrors the rules, where the tournament management takes over after 15 minutes |

## Decision

**Rule profile.** `LeagueRuleSet.fixtureMode`: `RACE` | `GAMES`. The rule-set dialog shows a mode
dropdown at the top and only that mode's fields; switching hides the other mode's values but never
deletes them. Shared by both: points for win/draw/loss, roster size, scheduling, the game plan.

- **`RACE` fields:** target (`raceTarget`, 42), step per segment (`raceStep`, 6 — normally target ÷
  number of segments), end rule (`raceEndRule`: `DRAW_ALLOWED` — ends at the target or at a draw one
  below it, 41 : 41 — or `TWO_POINT_LEAD` — past the target until one side leads by two), **bye score**
  (`raceByeScoreWinner` / `raceByeScoreLoser`, a blueprint setting like the others — 42 : 30 is only
  the default of the seeded blueprint), minutes to confirm.
- **`GAMES` fields:** the existing `setsPerGame`, `pointsToWinSet`, `matchdayDecision` /
  `matchdayTarget`. Out of scope for now; stays as built (doc 17).
- Seeded blueprints: **"Race to 42 (DTFB)"** (7 segments D1–D5 per above, 42/6, `DRAW_ALLOWED`,
  42 : 30, 15 min) and a knock-out variant with `TWO_POINT_LEAD`. Vorrunde and knock-out stage then
  differ by tier — the tier override already exists (doc 21).

**Race segments.** The game plan's entries are the segments, in order. Segment *k* ends as soon as
one side's running total reaches *k* × step — whichever side gets there first. Example: before the
3rd segment it's 12 : 4; A needs 6 goals to reach 18, B needs 14; if A gets there first the segment
ends 18 : (4 + x), otherwise (12 + y) : 18.

- **Entry:** the running score after each segment. Checked per segment: one side is exactly at
  *k* × step, the other below it, neither total lower than after the previous segment.
- **Last segment:** ends at the target, or at a draw one below it (`DRAW_ALLOWED`), or with a
  two-point lead past the target (`TWO_POINT_LEAD`, e.g. 43 : 41).
- **Decided** (doc 17's gate for "final"): the last segment is complete under the end rule. Segments
  can be entered one at a time as the fixture runs; the live table follows.
- An admin can score a segment "zu 0" for a missed/abandoned segment (the opponent gets the step,
  the other side stays).
- The game score stored per segment is the running score; the fixture's result is the final running
  score (e.g. 42 : 38).

**Byes.** The generator creates a fixture against the bye for the team that sits out, scored with the
rule set's bye score (per blueprint; 42 : 30 in the seeded one) and final at once. It counts in the
table as a win.

**Table.** Columns: place, team (logo later — milestone M2, SPO-31/32), played, won, drawn, lost,
goals for : against, goal difference, points. Order: points → goal difference → head-to-head; a tie
beyond that (lot / penalty) is set by an admin. Every active team placed in the group has a row from
the start, all zeros until its first counted fixture (2026-10-08); the stored standing rows keep only
teams with a counted fixture, since delete guards read them as "has recorded results". (SPO-21/22, SPO-74.)

**Confirmation deadline.** Rule field "minutes to confirm" (e.g. 15).

- The countdown starts when the result becomes **decided** — the backend stores that moment; the
  deadline is derived from it.
- **Captains** see a banner at the top of the app with the time left, on both sides (the one that
  still has to confirm, and the one waiting for it).
- **After the deadline the teams can't confirm any more** (409); the result is flagged overdue and
  only a neutral admin (tournament management) can confirm, correct or strike it.
- **The tournament management is the DTFB** (Marvin, 2026-09-29): the admins of the DTFB root
  federation (doc 16). They are neutral admins wherever the league belongs to the DTFB federation;
  for a league run under a regional federation they need a `league_admin` grant on it.
- **League admins** get an overview of pending and overdue results for their league(s). "Kick-off
  passed, nothing entered" needs the fixture's expected duration and comes later.

## Implementation (backend, 2026-09-29)

- **Rule set** (`V16`): `fixtureMode` (`FixtureMode` `RACE`/`GAMES`), `raceTarget`, `raceStep`,
  `raceEndRule` (`RaceEndRule` `DRAW_ALLOWED`/`TWO_POINT_LEAD`), `raceByeScoreWinner`/`Loser`,
  `confirmationMinutes`; part of the "rules changed" check, so they freeze with the season (doc 21).
  `RaceBlueprintSeeder` creates `rs-race42` ("Race to 42 (DTFB)") and `rs-race42-ko` on startup if
  missing (dev and prod alike) — archive, don't delete, to hide one.
- **Segments** — `RaceScoring` (pure, unit-tested): the running score per segment, entered in order,
  never going down, segment k exactly at k × step with the other side below, the last one under the
  end rule. `MatchDayResultService` checks every save (400 with the reason) and uses
  `RaceScoring.decided` as doc 17's "decided".
- **Deadline** — `MatchDay.decidedAt` is set when a result first becomes decided (cleared if an edit
  undoes it); deadline = `decidedAt` + `confirmationMinutes`. After it a team's confirm or edit is
  `409`; the result view carries `decidedAt`, `confirmDeadline`, `overdue`, and `canEdit`/`canConfirm`
  turn false for teams.
- **`GET /v1/matchdays/pending-results`** — the `SUBMITTED` results the current user has to act on
  (captain of a side, or neutral admin), overdue first, then by deadline; with league/group names
  for the banner and the league admin's overview.
- **Byes** — in a `RACE` group the generator gives the team sitting out a fixture against the bye
  (`MatchDay.bye`, no away team, no games, `CONFIRMED` at once); it scores the bye score in the table.
  Deleting a plan and the game-plan lock (doc 12 §5) ignore bye fixtures. `ScheduleFixtureDto.bye`.
- **Table** — `StandingService` computes both tables from the fixtures: a race fixture's score is
  its final running score; order points → goal difference → head-to-head (mini-table of the tied
  teams) → goals for → name; `StandingDto` gains `place`, `goalsFor`, `goalsAgainst`,
  `goalDifference`. The stored `Standing` rows stay as the official table cache for guards.
- Tests: `RaceScoringTest` (7), `RaceResultIntegrationTest` (6).
- **Frontend** (`dtfb-frontend-ng`): mode dropdown and race section in the rule-set dialog (new rule
  sets start as Race to 42); race entry on the result page with the step rules checked as you type
  (`race-check.ts` mirrors `RaceScoring`), countdown and overdue note; a countdown banner at the top
  of the app for both captains, and per-region counts for neutral admins; the region page "Open
  results" (tournament management); the group table (Live/Official) on the schedule page; byes read
  "Bye"; the team area's "Table" page shows the table of every group the team is placed in, with its
  own row marked.
- **Not yet:** "kick-off passed, nothing entered"; manual tie order (lot/penalty); the playoffs
  (SPO-99); the table on the public page (SPO-110).

## Questions for the competition management (Daniel)

1. Are the upcoming Regionalliga rules (M1, 20./21.03.2027) the same as Regionalliga Damen 2026 —
   Race to 42, 7 segments D1, D2, D3, S1, D4, S2, D5, draw allowed in the Vorrunde?
2. 15 minutes to confirm, and "struck if nothing is entered after 15 minutes" — does that apply to the
   Regionalliga? (Either way it's a rule-set setting; this only sets the Regionalliga's value.)
3. Tie-breaks: lot in the placement range, penalty in the promotion/relegation range — still so? Is
   "head-to-head" the direct fixture's result, or points/goals among all tied teams?
4. The playoffs (semi-finals, placement games, 3rd place) — same format on the M1 weekend?
5. Late arrival: "per 10 minutes a segment counts as lost to 0" (Bundesliga §9.2) — also for the
   Regionalliga?

## Watch for / triggers to revisit

- A league needs **best of X / sets** → build `GAMES` properly: sets per game on the result page,
  game winner from sets, per-game override of the game mode on game-plan entries (a double best of
  3, a single best of 5).
- The upcoming Regionalliga rules differ from the 2026 document → adjust the seeded blueprint, not
  the model.
- Captains regularly miss the deadline for reasons outside their control (bad venue Wi-Fi) → reconsider
  "blocked" vs "flagged only".
