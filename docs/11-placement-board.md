# Placement board — visual rework of the region-admin placements view

> **Kind: decision (implemented 2026-07-25).** Captures a design conversation (2026-07-22)
> about replacing the flat `dtfb-table` in the frontend's region-placements view with a
> visual, drag-and-drop board, then the actual build a few days later — which refined the
> original sketch in three ways, all noted inline below. No schema changes, no new endpoints;
> everything here is frontend-only (`+region/region-placements.component.ts` +
> `+region/components/placement-board/` in `dtfb-frontend-ng`). See the
> `placement-board-built`/`league-tier-group-management-built` memories (dtfb-frontend-ng repo)
> for the session write-up.

## 0. Why

The old placements view was one flat table of `TeamParticipation` rows for a selected season —
no visual grouping by tier or league, no sense of level ordering. That fit a one-time preseason
assignment; it stopped fitting once placements are managed for an **ongoing** league. The
board shows one league's one tier at a time (groups as lanes, ordered by `Tier.level`), with
drag-and-drop to move teams between groups.

## 1. Layout — **built as one tier at a time (dropdown-selected), not all tiers stacked**

The original sketch (below) proposed showing a whole league's tiers stacked vertically at
once. **Built differently, by request:** Season → League → Tier selects, and the board shows
only the selected tier's groups as lanes, plus one league-wide "Unplaced" lane. Simpler to
build well, and it resolves the layout's own accessibility tension for free — see §2.

Original sketch, for reference (not what shipped):
```
League: Bayernliga Herren 2024/25
  Unplaced (registered, no group yet)        [team, team]
  Tier 1 — 1. Bayernliga
    Gruppe A            Gruppe B
    [team, team, team]  [team, team]
  Tier 2 — 2. Bayernliga
    2. Bayernliga
    [team, team]
  Tier 3 — Playoffs
    Aufstiegsrunde
    []
```

**The "intentionally groupless league" open question from the original design is now
answered, indirectly:** [[league-tier-group-management-built]] (the League/Tier/Group CRUD
view, built the same session) established that `TeamParticipation` has no tier reference at
all — a team can only ever be placed via a `Group`. So "a league/tier with no groups" was never
really a distinct state to model; it's just not-yet-configured. Creating a tier now
auto-creates one default group (an undivided "pool"), so this ambiguity mostly doesn't arise
in practice anymore.

**No new backend read was needed**, as planned: `getLeagueStructure` (tiers + groups,
level-ordered) + `getAllTeamParticipations(seasonId?, leagueId?)` (already supports a `leagueId`
filter) + `getAllTeams` + `getAllRounds`, joined client-side, same pattern as everywhere else in
this app.

## 2. Team movement

- **Move within the current tier** (drag a team from one group lane to another, or to/from the
  shared "Unplaced" lane): a plain `TeamParticipation.group` update (full-DTO `PUT`, preserving
  every other field client-side — there's no partial-patch endpoint).
- **Promote/relegate — built as dedicated quick-actions, not a cross-tier drag.** Since only one
  tier's lanes are ever visible (§1), there's no lane to drag a team *into* for a different
  tier. Instead: a Promote/Demote button per team card jumps to the tier one level above/below.
  One click when that tier has exactly one group (the common case, per
  [[league-tier-group-management-built]]'s default-single-group design) — fires the move
  directly. A small group-picker dialog only when the adjacent tier is subdivided into more than
  one group.
- **Unplace** (drag into/out of the shared "Unplaced" lane): `group` set to `null` — a
  pre-existing valid state, not new.

**Leaving the league entirely (delete vs. withdraw) — refined the same session, after the board
shipped.** The original plan here was "deleting the `TeamParticipation` is a confirm-gated
button, never a drag target" — still true, but the confirm dialog itself got smarter: it tries
a hard delete first, and if the backend refuses (`409 PARTICIPATION_HAS_MATCHES` — a `MatchDay`
already exists for that team, drawn/scheduled or played, doesn't matter which), the *same*
dialog flips to offer withdrawing instead (keeps the row + history, locks the roster, excludes
it from future copy-forward). Rationale, from the user directly: once a matchday is drawn,
hard-deleting the placement would leave the schedule referencing a team that no longer exists —
withdrawal is the only sound option past that point, and `MatchDayRepository.existsByLeagueIdAndTeamId`
(existence, not "has a reported result") is the correct, precise, already-existing signal for
exactly when that line is crossed. No draft/status flag exists on `Season`/`League`/`Tier` to
check this ahead of time, nor is one needed — the per-team check is more precise than a
per-league one would be anyway (a league can have fixtures for other teams while a given team
still has none).

**Accessibility requirement, built as planned:** every drag-based move has a non-drag
equivalent — a kebab menu per team card (Edit roster / Move to… / Promote / Demote / Remove),
keyboard/screen-reader accessible via `@angular/cdk/menu`. The drag handle icon itself is
`aria-hidden` — a mouse/touch-only convenience, never the only way to act.

## 3. Randomize (per-tier shuffle) — built as planned, plus a sibling "Clear tier" action

A button, scoped to one tier at a time, that redistributes every **currently-placed** team in
that tier's groups evenly at random across those same groups. Confirmed by choice: never pulls
in already-unplaced teams, so it can't silently promote/relegate anyone as a side effect.

**New, not in the original design: "Clear tier"** — unplaces every currently-placed team in the
tier (moves them into the shared Unplaced lane) in one action. Added on request once the board
was otherwise done. Given the same safety gate as Randomize (below) — reasoning: even though
clearing doesn't scramble who-plays-whom the way a shuffle would, it's at least as disruptive to
a tier that's already running.

### Safety gate: when is a tier "safe" for Clear/Randomize?

**Built exactly as decided:** check the actual existence of `Round` rows under the tier's
groups, not `Group.groupState` (confirmed unreliable — see the original reasoning below,
unchanged). `RoundDto.groupId` is present directly on every row, so this is one unfiltered
`GET /v1/rounds` + a client-side membership check against the tier's group ids — no new
backend query. Once a tier has any `Round`, Clear tier and Randomize are both disabled outright
(not just confirm-gated) — matches the original "leaning towards disabling entirely" call.

*(Original investigation, unchanged):* `GroupController.updateGroup` does no transition
validation (`FINISHED → PLANNED` is accepted as readily as `PLANNED → READY`), and nothing
automatically transitions `groupState` except `CopyForwardService` resetting a clone to
`PLANNED` — so it's a manually-maintained label with no enforced link to reality, unusable as a
safety gate.

### Mechanics — built as planned

No bulk-update endpoint: compute the new assignment client-side, fire one
`PUT /v1/team-participations/{id}` per affected participation via `forkJoin`, same as every
other multi-write action in this app.

## 4. Summary of deviations from the original design

- **Layout**: one tier at a time via a dropdown, not every tier stacked (§1).
- **Promote/relegate**: dedicated quick-action buttons, not a cross-tier drag (§2).
- **New "Clear tier" bulk action**, gated the same as Randomize (§3).
- **Remove/Withdraw merged** into one guarded action instead of two separate ones (§2).
- Everything else (no new backend read, no `Group` state-machine validation, no bulk-update
  endpoint, `Round`-existence as the safety gate) shipped exactly as originally decided.
