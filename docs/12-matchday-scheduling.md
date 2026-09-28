# Matchday/fixture scheduling — generator + two scheduling conventions

> **Kind: model + decision (backend + frontend implemented 2026-07-26; fixed slots, gap between
> rounds, schedule view and WINDOW-only negotiation added 2026-09-27, SPO-56/SPO-75; the games of
> a fixture from the game plan added 2026-09-28, SPO-71, §5).** Closes the
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
play-system-shaped config (resolved via `tier → league`, `LeagueRuleResolver.effectiveFor`; since
doc 21 each league owns its own copy of a template, so the scheduling mode is set per league or on
the template it is created from).

## 2. The generator (`FixtureGenerationService`)

`POST /v1/groups/{id}/fixtures/generate` (body: `{ startDate, doubleRoundRobin }`) pairs a
group's placed (`ParticipationStatus.ACTIVE`) teams via the standard **circle (polygon) method**:
fixes the first team, rotates the rest one position each round. An odd team count is padded with
a bye — whichever team lands on it sits that round out. `doubleRoundRobin` mirrors every pairing
with home/away swapped instead of once. Home/away otherwise alternates by round parity — a
standard approximation; perfect per-team balance isn't attempted.

Guards: 409 if the group already has any `Round` (delete the plan first, see below — this mirrors
the same "check reality, not a flag" style as `MatchDayRepository.existsByLeagueIdAndTeamId` in doc
11), 409 if fewer than two teams are placed, 409 if the effective rule set has no
`schedulingMode` configured (an admin must pick one explicitly — there is no silent default).

**Fixed slots (tournament weekends, SPO-56/SPO-75, added 2026-09-27).** A league that is played
as a tournament — the Regionalliga: all teams at one venue, Sat 10:00/13:00/15:30/18:00 and Sun
9:30/12:00/14:00 — needs a real kick-off and venue per fixture from the start, not a 7-day
spacing. The request takes an optional `slots: [{ startDate, locationId? }]`:

- exactly one slot per generated round (else 400), in strictly ascending order (else 400);
- round N is played at slot N: every fixture of that round gets the slot's `startDate` and
  location — all matchups of a round start at the same time, the host assigns tables on site;
- those fixtures are `CONFIRMED` right away (`scheduleConfirmedAt` set), like an admin PUT (§3) —
  the organizer's slot is final, there is nothing to negotiate;
- only in `DAY_BATCH` mode (409 in `WINDOW`, where the teams agree on dates themselves);
- `startDate` in the request is then optional (the first slot is the start).

Slots are a one-off input at generation time, not stored on the rule set: a tournament day's date
and venue differ every season anyway, and the list covers one weekend (8 teams → 7 rounds → 7
slots) as well as several tournament days. With 8 teams a single round robin fills exactly the
seven Regionalliga slots.

**Gap between rounds (DAY_BATCH, added 2026-09-27).** Without slots, the request can carry an
optional `roundSpacingDays` (≥ 1, default 7) — the gap between the provisional round dates, e.g. 14
for a league that plays every other week, so the admin has less to correct afterwards. Rejected in
`WINDOW` mode (409; there the rule set's window length *is* the gap) and together with `slots`
(400). `GET /v1/groups/{id}/rules` returns a group's effective rules (tier override, else league;
204 if none), so the generate dialog knows the mode and window length up front.

**Deleting a plan** — `DELETE /v1/groups/{id}/fixtures` (204, same `canOrganizeGroup` gate):
removes the group's rounds, fixtures and their (still empty) games, so the plan can be generated
again, e.g. after a wrong slot. Refused with 409 once any fixture has a result entered
(`resultState` ≠ `OPEN`) — from then on the plan is history and standings may count it.

**The games of each fixture** come from the rule set's game plan — see §5 (SPO-71, 2026-09-28).
Until then B-2026-09-21-9 ("the schedule within a matchup is already built") held for *configuring*
the game order in the rule-set dialog only.

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

- **`DAY_BATCH`**: the admin's bulk day-assignment ("Assign dates", §4) writes directly via
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
  - **Only in `WINDOW` leagues** (added 2026-09-27): both return 409 unless the group's effective
    rules say `WINDOW`. With fixed matchdays (`DAY_BATCH`, incl. fixed slots) or no mode, the
    organizer sets the dates — otherwise a captain could reopen a fixed tournament slot. The team
    fixtures page shows the propose/accept buttons only for rounds with a window.

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
   (mirrors the Round-existence check used elsewhere) and `participationCount >= 2`. Since
   2026-09-27 with an optional slot editor (tournament days with date, venue and kick-off times;
   a live "n of m slots" counter against the round count), plus a row action to delete the plan
   (`delete-fixtures-dialog.component.ts`). The dialog loads the group's effective rules
   (`GET /v1/groups/{id}/rules`) and adapts: `DAY_BATCH` offers slots or a "gap between rounds"
   (1–4 weeks → `roundSpacingDays`); `WINDOW` shows the window length from the rules instead; no
   mode shows a warning and blocks generating. While fewer than two teams are placed, the group
   row says so and links to the placement board (`?seasonId&leagueId&tierId` deep link).
2. **Admin: `DAY_BATCH` bulk assignment** — `assign-schedule-dialog.component.ts`: an
   unscheduled-fixtures list using a new checkbox multi-select primitive added to `dtfb-table`
   (`selectable`/`rowId`/`selected`/`selectedChange`, scoped to all filtered rows, not just the
   current page) + a toolbar that applies a date/location to every checked fixture via
   `forkJoin`'d `PUT`s — no new bulk endpoint.
3. **Team: `WINDOW` propose/accept UI** — `propose-schedule-dialog.component.ts`, reached from a
   new `+team/team-fixtures.{component,service}.ts` (mirrors `team-rosters.*`) mounted as a
   `fixtures` sub-route under `/team/:teamId`. Since 2026-09-27 the buttons only appear for
   fixtures whose round has a window; otherwise the row says the organizer sets the date.
4. **Admin: schedule view** — `region-group-schedule.component.ts` at
   `/region/:regionId/leagues/:leagueId/groups/:groupId/schedule`, linked from the group rows: one
   card per round (shared kick-off + venue, or the round's window, in the header) with home, away,
   kick-off, venue, scheduling and result state per fixture. Backed by
   `GET /v1/groups/{id}/schedule` (rounds → fixtures with team and venue names resolved, one call
   instead of loading every team/round/matchday client-side).
5. **Admin: venues** — `region-venues.component.ts` at `/region/:regionId/venues` (SPO-77): the
   region's own venues plus the nationwide ones (no region; editable by global admins only), with
   add/edit/delete dialogs. The venue pickers in 1 and 2 only offer those
   (`GET /v1/locations?federationId=`). Deleting a venue that fixtures use is refused with 409
   `LOCATION_IN_USE`; a venue's region can't be changed. Where nationwide venues are maintained is
   still open (agenda 2026-09-27).

**Verified live 2026-09-27** (headless click-through against the dev stack): generate with a slot
→ schedule view → delete → regenerate; DAY_BATCH every 2 weeks with home & away (round 2 exactly
14 days later); WINDOW note on a window group; venue add → edit → use in a slot → blocked delete.
Not yet exercised live: the team propose/accept path — the seed's team login only sees its newest
team row, which has no fixtures (SPO-107).

## 5. The games of a fixture (SPO-71, 2026-09-28)

A fixture (`MatchDay`) consists of individual games (`Match`: single, double, goalie). Their order
and types come from the game plan of the group's **effective** rule set — the tier's override, else
the league's (docs/21) — e.g. `[1:DOUBLE, 2:DOUBLE, 3:SINGLE]` (docs/09 §3.1). `MatchPlanService`
creates one `Match` per plan entry (`position` and `type` from the entry, state `PLANNED`,
`startTime` = the fixture's kick-off):

- when the generator creates a fixture (§2), and when an admin creates one via `POST /v1/matchdays`;
- once on startup for fixtures from before SPO-71 that have no games and no result yet
  (`MatchPlanBackfill`, after the rule-set snapshot backfill). A group without a game plan gets
  fixtures without games, as before.

Deleting a fixture or a whole plan deletes its games.

**The game plan is fixed from the first entered result on** (decided by Marvin 2026-09-28). A
change that alters the effective game plan of a group — editing the league's/tier's rules, applying
a blueprint to it, a league switching blueprint, a tier override added or removed — then works like
this:

- **no result entered yet** in that group: every fixture of the group gets its games rebuilt from
  the new plan. Dates, venues, fixed slots and scheduling state stay as they are, so a mistake in the
  plan never forces deleting and regenerating a schedule;
- **a result entered** — any fixture with `resultState` ≠ `OPEN` (already on submit, not only on
  confirmation), or any game with a score: the change is refused with **`409 GAME_PLAN_LOCKED`**
  and rolled back as a whole.

All other rule fields (points, sets, roster sizes, scheduling) stay editable until the season ends
(`RULE_SET_FROZEN`, docs/21). The rule-set DTO carries a read-only `gamePlanLocked` so the dialog
can show the plan as fixed up front. Groups whose plan a change doesn't touch are left alone — e.g.
a tier with its own override when the league's plan changes.

**Not yet:** checking entered scores against `setsPerGame`, `pointsToWinSet` and
`matchdayDecision` (e.g. "first to N"). What a valid Regionalliga result looks like depends on the
format being clarified with the competition management (SPO-58); this belongs to result entry
(SPO-15/57, docs/17).
