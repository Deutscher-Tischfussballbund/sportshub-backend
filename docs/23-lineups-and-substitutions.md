# Line-ups and substitutions — who plays whom, before and during a fixture

> **Decision (proposed) 2026-09-29 by Marvin, to confirm with the team and the competition management
> (Daniel). Built 2026-09-29, backend and frontend (§Implementation). Reverses meeting decision
> B-2026-09-21-11** ("line-ups and substitutions stay
> on paper for M1") — the result page is to show which players play against whom, which needs the
> line-up. Each team's captain enters the **line-up** before kick-off: the players per game of the game
> plan (2 for a double, 1 for a single), from the team's current roster. It stays **hidden from the
> opponent until both are submitted** and is locked then. **Substitutions** are recorded as
> `MatchEvent`s of the new type `SUBSTITUTION` (`playerIn` / `playerOut`, from game X on) — the start of
> an event log per game that later carries goals, timeouts and cards (live ticker, referees). **No
> result entry without both line-ups**, except for neutral admins, wherever the rule set requires
> line-ups — which is the default.
>
> See also: [17-result-entry-confirmation.md](./17-result-entry-confirmation.md) (who enters results;
> this adds a precondition), [22-fixture-modes.md](./22-fixture-modes.md) (the Race profile carries
> the block rule), [03-authorization-model.md](./03-authorization-model.md) (the "referee (future)"
> row this prepares), [15-club-membership.md](./15-club-membership.md) (the roster the players come
> from).
>
> Question raised: the result page should show which player plays against whom — where do the players
> per game come from, and how do substitutions fit in?
> Short answer: **a line-up per team entered before kick-off, revealed when both are in; substitutions
> as match events; results need both line-ups (except for neutral admins).**

## Context

The result page lists the games ("Doppel 1", "Einzel 1" …) but not who played them. The players per
game come from the line-up, which today is on paper only (B-2026-09-21-11, SPO-100 in version 1.1).
The regulations already expect it digitally — *Regionalliga Damen 2026* §3/§5, *Bundesliga 2026* §7:

- The line-ups are **exchanged** before the fixture and **entered completely before it starts**; the
  away team enters them online. The exchanged line-ups count as a document; later changes count as
  manipulation.
- **D1–D3:** six different players. **S1, D4, S2, D5:** again six different players, at least two of
  whom played in D1–D3. A player plays **at most two** games and **at most one single**; **at most ten**
  different players per fixture.
- **Substitutions:** at most four per fixture; only players who haven't played yet (and aren't lined up
  for another game); a substitute takes over the position **in later games too**; in a double both may
  be replaced in one timeout (counts as two). Timing rules (timeout, "after three goals") are for the
  players and the tournament management.

`MatchEvent` already exists as a skeleton — game, team, `playerId` (a plain string, no reference),
time, `type` (`GOAL`, `OWN_GOAL`, `CARD`, `TIMEOUT`, `START`, `END`, `OTHER`), running score, an
untyped `json` payload — writable by admins only (`canOrganizeMatch`). Nothing uses it.

## Options considered

| | How | Verdict |
|---|-----|---------|
| **Substitutions as `MatchEvent`s (chosen)** | A `SUBSTITUTION` event per change (`playerIn`, `playerOut`, the game it takes effect in) | **Chosen** (dev team's proposal). One time-ordered log per game that goals, timeouts and cards join later — live ticker (SPO-116), score cross-checks, player statistics, disputes, and a future referee role (doc 03). The line-up stays the plan; line-up + events = who actually played |
| Overwrite the line-up on substitution | Change the player in the affected games directly | Rejected — loses the original line-up (a document per the rules) and who changed what when |
| A separate substitution table | Own entity just for substitutions | Rejected — would duplicate what `MatchEvent` models and split the log again once goals come |
| Generic `relatedPlayer` for the second player | `player` + `relatedPlayer`, meaning per type | Rejected — must be looked up per type; explicit `playerIn` / `playerOut` explains itself |
| **Line-up hidden until both are in (chosen)** | Each captain sees only their own until both submitted | **Chosen** — the rules *exchange* line-ups; seeing the opponent's first would be an advantage |
| **Line-ups required by default (chosen)** | Rule-set setting, on unless switched off | **Chosen** — every league seen so far needs line-ups (Marvin); a league that doesn't can switch it off |

## Decision

**Line-up** (`Lineup`, one per fixture and team; `LineupEntry` per game and slot):

- For every game of the fixture: 2 players for a double, 1 for a single (goalie games: 1), from the
  team's **current roster in the league** (doc 15). Staged and submitted as a whole.
- Entered by the team's **captain** (`team_admin`) or a neutral admin, **before kick-off**.
- **Hidden** from the opponent until both teams have submitted; then both are shown and **locked** —
  from then on only substitutions change who plays.
- Missing at kick-off: the result page and the pending overview say so; the tournament management
  decides. Nothing is blocked automatically.

**Line-up rules** — checked on submit (400 with the reason):

- General (rule-set settings): at most N games per player (2), at most M singles per player (1), at
  most K different players per fixture (10).
- Race profile (doc 22): the block rule — the first block of doubles (D1–D3) needs six different
  players; the second block (S1, D4, S2, D5) six different players, at least two of them from the first
  block. The block boundaries follow from the game plan; the exact numbers are confirmed with Daniel.

**Substitutions** — `MatchEvent` of type `SUBSTITUTION`:

- Fields: `match` (the game it takes effect in), `team`, `playerIn`, `playerOut` (both references to
  `Player`), `timestamp`. The existing `playerId` becomes a proper `player` reference, used by
  single-player events later (goal, card).
- Recorded by the team's own captain (or a neutral admin) during the fixture. Checked: at most four
  per fixture (a double's two players count as two); `playerOut` is lined up for that game;
  `playerIn` is on the roster, hasn't played yet and isn't lined up for another game.
- A substitute **takes over the position in later games too** (positional).
- A captain can delete their own substitution while the game has no score yet; after that only a
  neutral admin.

**Who actually played** = the line-up with the substitution events applied in game order. The result
page shows it per row: "Doppel 1: Müller / Schmidt – Weber / Klein" (pictures with the image
milestone, SPO-31/32).

**Result entry** (extends doc 17): where the rule set requires line-ups, a team can only enter a result
once **both** line-ups are submitted; a **neutral admin** always can (e.g. a team forgot its line-up).
Rule-set setting `lineupRequired` — on by default (also for existing rule sets), switchable off.

**Permissions per event type:** substitutions — the team's captain; goals, timeouts, cards — later
the tournament management or a **referee** (a future role scoped to a fixture, doc 03's "referee
(future)"; not the tournament-admin role rejected in B-2026-09-21-12, which was about overrides).

## Implementation (backend, 2026-09-29)

- **Model** (`V17`): `Lineup` (fixture, team, `submittedAt`), `LineupEntry` (line-up, game, slot,
  player); `MatchEvent` gets `player` / `playerIn` / `playerOut` as real references and the type
  `SUBSTITUTION` (column now a plain varchar); rule-set fields `lineupRequired`,
  `lineupMaxGamesPerPlayer`, `lineupMaxSinglesPerPlayer`, `lineupMaxPlayers`, `lineupBlockRule`,
  `maxSubstitutions` (the Race to 42 blueprints: required, 2, 1, 10, on, 4).
- **Endpoints** (`LineupController`, logic in `LineupService`): `GET /v1/matchdays/{id}/lineups`
  (both sides as far as the viewer may see them — own side, neutral admin, or everyone once both are
  in or kick-off has passed — plus who plays each game and the substitutions);
  `PUT …/lineups/{HOME|AWAY}` (`{ games: [{ matchId, playerIds }], submit }` — a draft only checks
  roster and slots, `submit` checks the rules); `POST …/substitutions`
  (`{ side, matchId, playerOutId, playerInId }`), `DELETE …/substitutions/{eventId}`. Writes are
  captains of that side or neutral admins; captains are locked out once both line-ups are in.
- **Substitution checks:** both line-ups in; the game has no score yet (captains); the outgoing player
  plays that game; the incoming one is on the roster, not in any game and not substituted out before;
  at most `maxSubstitutions`.
- **Result entry:** a team's entry is `409` while the rule set requires line-ups and not both are in;
  the result view has `lineupRequired`, `lineupsComplete` and per game `homePlayers` / `awayPlayers`
  (after substitutions, as far as visible). Rebuilding or deleting a fixture's games (doc 12 §5)
  removes its line-ups and events first.
- `MatchEventService` keeps a player unchanged on update when none is sent (it used to be a string).
- Tests: `LineupIntegrationTest` (6); result tests switch line-ups off in their league rules
  (`LineupTestSupport`).
- **Frontend** (`dtfb-frontend-ng`): the fixture page (team area and admin schedule) holds both: each
  side's line-up state and who plays each game (the opponent hidden until both are in), and the score
  entry, which opens for captains once both line-ups are in. The line-up is edited in a dialog
  (2026-10-08, explicit save like every edit dialog): a player select per slot, Cancel / Save draft /
  Submit, the rules explained before submitting (`lineup-check.ts` mirrors `LineupService.violation`).
  Substitutions are recorded in their own dialog and listed on the page. Old `…/lineup` links redirect
  to the fixture page. The rule-set dialog has a line-up section.

## Open questions

- **Numbers for M1** (Daniel): the block rule, at most four substitutions, at most ten players — as in
  Regionalliga Damen 2026?
- **Who enters the line-up:** the rules name the away team for the online entry. Does each team enter
  its own (proposed), or one team both?
- **Line-up without a submitted opponent at kick-off:** show the own line-up to the opponent anyway once
  the fixture has started, so the result page isn't empty? Proposed: yes, from kick-off on.
- **Referee role:** scope (fixture, match day, tournament weekend) and who appoints — when the first
  league wants referees.

## Watch for / triggers to revisit

- Captains regularly change line-ups after submitting (injuries before the start) → a "withdraw
  submission" step before the opponent has submitted.
- Goal-by-goal entry (SPO-116) lands → validate a game's score against its goal events.
