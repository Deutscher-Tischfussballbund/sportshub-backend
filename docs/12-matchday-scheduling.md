# Matchday/fixture scheduling — generator + two scheduling conventions

> **Kind: model + decision (backend + frontend implemented 2026-07-26).** Closes the
> biggest gap between what's built and a real admin's workflow: until now, a season's fixtures
> could only be created one `MatchDay` at a time via direct API/seed — no pairing generator, no
> way to turn a draw into real calendar dates. See the `matchday-round-creation-gap` memory
> (dtfb-frontend-ng repo) for the original backlog entry.

## 0. What `Round` is for

`Round` is the round-robin **round number** ("Spieltag N") — it groups every fixture that
belongs to the same pass through the pairing table. It carries no date of its own; only
`MatchDay` (the individual team-vs-team fixture) has a `startDate`/location. Two fixtures in the
same `Round` can legitimately be played on different days — the model already allowed this, there
was just no tooling to *create* the rounds/fixtures or *assign* their dates. `Round` existence is
already used elsewhere as the "this tier is running" signal (doc 11 §3 gates Randomize/Clear-tier
on it); this feature keeps leaning on that same signal rather than resolving the separate,
still-open `Group.groupState` question (see the `group-state-semantics-open-question` memory).

## 1. Two real-world scheduling conventions

Federations run fixture scheduling one of two ways:

- **Mode A — `DAY_BATCH`**: an admin picks calendar days and assigns however many fixtures fit on
  each one (common for a single small group playing all its games at club evenings).
- **Mode B — `WINDOW`**: each round gets a date window (e.g. two weeks); the two teams agree on
  the exact date/venue for their own fixture within it.

The mode (and, for `WINDOW`, the window length) is configured on `LeagueRuleSet` —
`schedulingMode: DAY_BATCH | WINDOW`, `schedulingWindowDays` — since a federation's leagues tend
to run one way consistently, and `LeagueRuleSet` is already the natural, reusable home for
play-system-shaped config (resolved via the existing `tier → league → federation.defaultRuleSet`
chain, `LeagueRuleResolver.effectiveFor`).

## 2. The generator (`FixtureGenerationService`)

`POST /v1/groups/{id}/fixtures/generate` (body: `{ startDate, doubleRoundRobin }`) pairs a
group's placed (`ParticipationStatus.ACTIVE`) teams via the standard **circle (polygon) method**:
fixes the first team, rotates the rest one position each round. An odd team count is padded with
a bye — whichever team lands on it sits that round out. `doubleRoundRobin` mirrors every pairing
with home/away swapped instead of once. Home/away otherwise alternates by round parity — a
standard approximation; perfect per-team balance isn't attempted.

Guards: 409 if the group already has any `Round` (regeneration isn't supported — this mirrors the
same "check reality, not a flag" style as `MatchDayRepository.existsByLeagueIdAndTeamId` in doc
11), 409 if fewer than two teams are placed, 409 if the effective rule set has no
`schedulingMode` configured (an admin must pick one explicitly — there is no silent default).

**Deliberately out of scope:** auto-creating `Match` rows from `LeagueRuleSet.gamePlan` — the
game plan exists but nothing reads it anywhere yet; that per-game breakdown is a separate,
not-yet-built concern.

## 3. Turning a generated fixture into a real date

Every generated `MatchDay` gets a real, non-null `startDate` from the start — a computed default
inside its round's window (`WINDOW` mode) or the generation request's reference date (`DAY_BATCH`
mode) — so there is never a null/placeholder state to handle downstream. Three fields on
`MatchDay` track whether that date is still just the default, one side's proposal, or agreed by
both — independent of `ResultState`, which is about the *outcome*, not the *timing*:

```java
schedulingState: DEFAULT | PROPOSED | CONFIRMED   // default DEFAULT
scheduleProposedByDtfbId: String?
scheduleConfirmedAt: Instant?
```

- **`DAY_BATCH`**: the admin's bulk day-assignment (frontend, not yet built) writes directly via
  the existing full-entity `PUT /v1/matchdays/{id}`, which now always stamps `CONFIRMED` — a full
  admin PUT is authoritative over the whole fixture (same admin-bypass precedent as the roster
  edit bypass, PR #30) and finalizes any pending negotiation.
- **`WINDOW`**: a real propose/accept negotiation between the two team captains, not a one-shot
  overwrite —
  - `POST /v1/matchdays/{id}/schedule/propose` (body `{ startDate, locationId? }`) — either
    team's representative may call this at any time, including to reopen an already `CONFIRMED`
    fixture (mirrors the roster lifecycle's `reopen`). Validates the date falls within the
    round's `[windowStart, windowEnd]` (admin bypass via the plain PUT skips this).
  - `POST /v1/matchdays/{id}/schedule/accept` — only the *other* representative may accept a
    `PROPOSED` date: the exact submitter≠confirmer invariant already proven for match results
    (`MatchDayResultAuthorizationIntegrationTest`).
  - Both reuse the existing `@authz.canReportMatchDay` gate (team_admin of either team, or an
    admin above) — no new authz method needed.

**Kept lean for the (parked) tournament/competition block.** These scheduling fields describe a
generic "is this date final, and who said so" workflow — nothing league-specific — so a future
tournament `MatchDay` reuse (doc 09 §0: the atomic game/fixture concept is meant to be shared)
isn't blocked by them. The window itself lives on `Round`, which is already league-only in doc
09's model (tournaments use `event → draw`, no `Round` at all).

`MatchDay.location` was already nullable at the entity level; `MatchDayService.setDependants` now
skips the lookup when none is given, so a freshly generated fixture doesn't need a placeholder
venue before anyone has agreed on one.

## 4. Frontend (built 2026-07-26, `dtfb-frontend-ng` commit `ff9ff17`)

1. **Admin: generate fixtures** — `generate-fixtures-dialog.component.ts`, triggered from a row
   action on `region-league-detail.component.ts`'s group rows, gated on `GroupRow.hasFixtures`
   (mirrors the Round-existence check used elsewhere) and `participationCount >= 2`.
2. **Admin: `DAY_BATCH` bulk assignment** — `assign-schedule-dialog.component.ts`: an
   unscheduled-fixtures list using a new checkbox multi-select primitive added to `dtfb-table`
   (`selectable`/`rowId`/`selected`/`selectedChange`, scoped to all filtered rows, not just the
   current page) + a toolbar that applies a date/location to every checked fixture via
   `forkJoin`'d `PUT`s — no new bulk endpoint.
3. **Team: `WINDOW` propose/accept UI** — `propose-schedule-dialog.component.ts`, reached from a
   new `+team/team-fixtures.{component,service}.ts` (mirrors `team-rosters.*`) mounted as a
   `fixtures` sub-route under `/team/:teamId`.

**Known gap:** true end-to-end click-through (generate → day-batch assign → team propose →
opponent accept) was not exercised live — the dev seed has no fixture-free 2+-team group and no
`MatchDay` rows for a team to test the propose/accept path against. TypeScript type-checks
cleanly against the live regenerated API client; production build and `pnpm a11y` are clean on
every new surface reachable in the current seed state.
