# Knock-out stage — fixtures fed by group places and earlier results, final ranking, promotion

> **Discussion — draft, not yet proposed to the team (SPO-99).** A season gets a second kind of group:
> **`KNOCKOUT`**, beside today's round robin. Its fixtures don't start with teams but with **sources** — "Group A,
> place 1", "winner of SF 1", "loser of SF 1" — and the teams **fill in automatically** once the source is decided
> (the group finished, the source fixture final). An admin can override a filled team (e.g. after a lot). A
> **helper creates the usual shapes** (crossed semi-finals or quarter-finals, game for 3rd, placement games for the
> remaining places) with kick-off slots as for tournament weekends. Each knock-out fixture can award places
> (`winnerPlace`/`loserPlace`), which gives a **final ranking** of the tier; promotion and relegation places on the
> tier mark it, and **copy-forward places next season's teams accordingly**. The schedule page gets a **bracket
> view**. Knock-out fixtures play by their own rules (no draw, e.g. "Race to 42 K.O. (DTFB)").
>
> See also: [09-league-model.md](./09-league-model.md) (§1 "in-season phases deferred" — reopened here),
> [11-placement-board.md](./11-placement-board.md) (promote/demote), [12-matchday-scheduling.md](./12-matchday-scheduling.md)
> (generator, fixed slots), [17-result-entry-confirmation.md](./17-result-entry-confirmation.md) (final results),
> [21-rule-set-blueprints.md](./21-rule-set-blueprints.md) (snapshots, tier override),
> [22-fixture-modes.md](./22-fixture-modes.md) (Race to 42, the K.O. blueprint, tie order),
> [32-public-view.md](./32-public-view.md) (the board shows knock-out fixtures too).
>
> Question raised: how does a league play its playoffs — semi-finals, game for 3rd, placement games — when the
> pairings are only known after the group stage, and how does that lead to a final ranking and promotion?
> Short answer: **a knock-out group whose fixtures name their sources and fill themselves; places per fixture make
> the final ranking; the tier's promotion/relegation places carry it into copy-forward.**

## Context

- A season has one phase today: round robin in groups (`Season → League → Tier → Group → Round → MatchDay`).
  Doc 09 §1 deferred in-season phases "until a real league needs it". Two do:
  - **Regionalliga Damen 2026** (rules §8–10): 10 teams in 2 groups of 5, then on the same weekend: 1st and 2nd of
    each group → crossed semi-finals; the winners are promoted, the losers play for 3rd (the third promotion place);
    3rd and 4th → placement games 5–8; the two 5ths → game for 9–10.
  - **Bundesliga** (Daniel, 28.09.): 2 groups of 12, then three blocks of four per group — top four: crossed
    quarter-finals (1st vs 4th of the other group), semi-finals, final; middle: placement games (not played in
    practice); bottom: playdowns and relegation.
- The 2027 Regionalliga format is open — it depends on the number of teams (B-2026-10-12-4); Marvin wants a
  knock-out system for M1 either way (12.10.). So the model must cover different shapes, not one hard-coded format.
- The rules already prepare it: the seeded blueprint "Race to 42 K.O. (DTFB)" (`TWO_POINT_LEAD`, no draw), and doc 22
  says Vorrunde and knock-out stage differ by their rules.
- A fixture (`MatchDay`) has `teamHome`/`teamAway` as plain references; the columns are nullable, but result entry,
  line-ups, standings and the schedule assume both teams are set (or a bye).
- Promotion between seasons is manual today: copy-forward clones the structure and placements, then the placement
  board's Promote/Demote buttons move a team one tier up or down (doc 11).
- Group tables: points → goal difference → head-to-head → goals for → name; a tie beyond that (lot / penalty) is
  resolved by an admin, but a manual tie order is not built yet (doc 22).

## Options considered

**Where the knock-out stage lives**

| | How | Verdict |
|---|-----|---------|
| **A group of kind `KNOCKOUT` in the same tier** | `Group.kind` = `ROUND_ROBIN` \| `KNOCKOUT`; the tier "Regionalliga" holds Gruppe A, Gruppe B and "K.O.-Runde"; a group may override the tier's rules | **Chosen.** Doc 09's own suggestion (a phase attribute on the group). The tier stays a promotion level; the final ranking is per tier and spans group stage and knock-out stage naturally. |
| A tier of its own ("Playoffs") | the knock-out stage as a tier with the K.O. rule set as tier override (doc 11's sketch) | **Rejected.** A tier is a promotion level (`Tier.level`); a "Playoffs" tier would appear as a level on the placement board and in promote/demote, and the final ranking would have to span two tiers. |
| A separate tournament model | brackets in the parked tournament model (doc 09) | **Rejected** — far too big for M1, and the playoffs are part of the league season, with its rosters and rules. |

**How pairings are defined**

| | How | Verdict |
|---|-----|---------|
| **Sources, filled automatically** | each knock-out fixture names a source per side; teams fill in when the source is decided; admin override | **Chosen** (Marvin, 2026-10-23). Works for any shape; nothing to do on the match day except entering results. |
| Manual after the group stage | an admin creates each round's fixtures by hand once teams are known | **Rejected** — on a tournament weekend there is no time between rounds, and no bracket or final ranking falls out of it. |
| Fixed formats only ("2×5 Regionalliga") | a format enum with built-in pairings | **Rejected** as the model — formats differ per league and year. Kept as **helper presets** that create source-based fixtures, which the admin can still edit. |

**Rules of the knock-out fixtures**

| | Verdict |
|---|---------|
| **Rule override on the group** (snapshot like the tier override, doc 21); resolution group → tier → league → federation default | **Chosen** — the K.O. blueprint goes on the knock-out group; `LeagueRuleResolver.effectiveFor(group)` gains one step. |
| Force `TWO_POINT_LEAD` in knock-out groups | **Rejected** — hides a rule in code; a `GAMES` league needs its own way to avoid a draw. |

## Decision

**Model**
- Now: `Group.kind` (`ROUND_ROBIN` default, `KNOCKOUT`) and an optional `Group.ruleSet` override (a snapshot, like
  `Tier.ruleSet`). `LeagueRuleResolver.effectiveFor(group)`: group override → tier override → league → federation
  default. Migration adds both columns; existing groups become `ROUND_ROBIN`.
- Now: a knock-out fixture is a `MatchDay` in the knock-out group with a **source per side** (`homeSource`,
  `awaySource`, embedded): `GROUP_PLACE` (`sourceGroupId`, `sourcePlace`), `WINNER` or `LOSER` (`sourceMatchDayId`).
  `teamHome`/`teamAway` stay null until resolved. `name` carries the label ("HF 1", "Spiel um Platz 3").
- Now: optional **`winnerPlace` / `loserPlace`** on a knock-out fixture (Final 1/2, game for 3rd 3/4, game for 9th
  9/10). A loser without a `loserPlace` and without a later fixture is placed by the round they dropped out in
  (shared place, e.g. 5–8 for quarter-final losers).
- Now: rounds of the knock-out group are its bracket rounds (QF, SF, F); kick-offs and venue come from slots as in
  doc 12 (`slots: [{ startDate, locationId? }]`, one per round), or are set per fixture.

**Filling teams**
- Now: `GROUP_PLACE` resolves when the source group is **finished** — every non-bye fixture final (`CONFIRMED`) —
  from its official table; `WINNER`/`LOSER` when the source fixture is final. Triggered on
  `MatchDayConfirmedEvent` (and on withdrawal, doc 22 / SPO-74).
- Now: a knock-out fixture can only be entered once both teams are filled; result entry, line-ups and confirmation
  then work as for any fixture. It must end decided: a drawn knock-out fixture can't become final (409 with a clear
  message) — the group's rules must not allow a draw (checked when the knock-out stage is created, warning otherwise).
- Now: an admin can **override** a filled team (lot, penalty, a withdrawal, a correction) — marked "set by hand" and
  never overwritten automatically afterwards. Allowed until the fixture has a result.
- Now: an admin corrects a final result whose winner feeds a later fixture → the later fixture is re-filled if it has
  no result yet; if it has one, the correction is refused (409 `KNOCKOUT_DOWNSTREAM_PLAYED`) — undo the later one first.
- Now: a group table tie that needs a lot blocks nothing — the computed order fills the slot, the admin overrides if
  the lot says otherwise. (A manual tie order in the table itself stays separate, doc 22.)

**Creating a knock-out stage (helper)**
- Now: "Add knock-out stage" on a tier: choose the source groups, how many teams per group go into the main bracket
  (2 → semi-finals, 4 → quarter-finals), crossed pairing (1A–2B, 1B–2A; 1A–4B, 2A–3B, …), game for 3rd yes/no,
  placement games for the remaining places yes/no (the next two per group → games for 5–8, and so on), and the slots.
  It creates the knock-out group with the tier's knock-out rules (default: the K.O. blueprint), its rounds and
  source-based fixtures with places.
- Now: everything it creates stays editable (sources, places, labels, kick-off) until results exist — the helper is a
  shortcut, not a format.
- Covers both known formats: Regionalliga 2026 (2 per group → semis, game for 3rd, placement 5–8 and 9–10) and the
  Bundesliga top block (4 per group → quarter-finals, semis, final, no placement games); the Bundesliga bottom block
  (playdowns) is a second knock-out group fed by places 9–12.

**Final ranking**
- Now: `GET /v1/tiers/{id}/final-ranking` — per team: place (or a shared range "5–8"), how it was decided (knock-out
  fixture / dropped out in round X / group place), promotion or relegation mark. Teams of a tier without a knock-out
  stage are ranked by their group table (several groups: same place side by side, e.g. two "1st").
- Now: provisional until every fixture that decides a place is final; served live like the tables.
- Now: shown under the tier on the schedule page and in the team area; later on the public board (doc 32).

**Promotion and relegation**
- Now: `Tier.promotionPlaces` and `Tier.relegationPlaces` (counts from the top / bottom of the final ranking, null =
  none). The final ranking marks those teams.
- Now: **copy-forward** places marked teams one tier up / down (by `Tier.level`) in the new season instead of the same
  tier; the top tier's promoted teams and the bottom tier's relegated teams stay where they are, marked, for an
  admin. The placement board shows the mark, and Promote/Demote stay for manual corrections.
- Not now: promotion **across leagues** (Regionalliga → Bundesliga, different league and sometimes federation) —
  marked in the final ranking only; the admin of the target league places the team.

**Bracket view**
- Now: the schedule page shows a knock-out group as a bracket — rounds as columns, each fixture with its label,
  teams (or "Sieger HF 1" while open), score and "vorläufig"; placement games beside the main bracket. Keyboard and
  screen-reader friendly as a structured list underneath (a library primitive, `dtfb-bracket`).
- Later: the same view on the public board.

**What stays as it is**
- Round-robin groups, tables, the generator (`FixtureGenerationService`) and result entry are untouched apart from
  "teams not yet known" handling; the knock-out group simply has no table (its place is the bracket).
- Rosters belong to the team's participation in the league (`RosterEntry` → `TeamParticipation`), so a team plays
  the knock-out stage with its roster; no placement in the knock-out group needed.

**Effort (rough)**
- Backend (kinds, group override, sources, filling, places, final ranking, copy-forward, tests): ~1.5 weeks.
- Frontend (helper dialog, edit sources, bracket view, final ranking, marks on the placement board): ~1 week.

## Open questions

- **Regionalliga 2027 format** — depends on the team count (Daniel). The model covers the known shapes; with ~13
  teams and a Swiss system (SPO-127) the knock-out stage would be fed by Swiss places instead of group places — same
  `GROUP_PLACE` source.
- **Fixtures without a result and "group finished"** — the rules strike a fixture with no result after 15 minutes,
  but the system has no "struck" state yet (doc 22 "not yet"); until then an admin has to finalize or delete such a
  fixture before the knock-out stage fills. Decide together with SPO-15's 15-minute question.
- **Shared places** — are dropped-out teams without a placement game really "5–8", or ranked by their group record?
  Matters for promotion only if a promotion place falls into such a range.
- **Cross-league promotion** — who moves the Regionalliga's promoted teams into the Bundesliga, and when?
- **Third promotion place** in the Regionalliga 2026 (winner of the game for 3rd) — `promotionPlaces = 3` covers it;
  confirm for 2027.
- **Penalty** for a tie in the promotion/relegation range (doc 22): an admin override of the filled team covers the
  knock-out side; the table side waits for the manual tie order.
