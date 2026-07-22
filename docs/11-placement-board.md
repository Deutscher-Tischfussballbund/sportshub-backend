# Placement board — visual rework of the region-admin placements view (proposed)

> **Kind: decision (proposed, not yet implemented).** Captures a design conversation
> (2026-07-22) about replacing the flat `dtfb-table` in the frontend's region-placements
> view with a visual, drag-and-drop board. Nothing in this doc is built yet — no schema
> changes, no new endpoints. See [[next-frontend-priorities]] / `frontend-backend-migration`
> memory (dtfb-frontend-ng repo) for the tracking note.

## 0. Why

The current placements view (`+region/region-placements.component.ts` in `dtfb-frontend-ng`)
is one flat table of `TeamParticipation` rows for a selected season — no visual grouping by
tier or league, and no sense of level ordering. The ask: a board where a whole season's
structure (which leagues, which tiers within each, which groups within each tier, which teams
in each group) is visible at once, ordered by `Tier.level` (1 = top), with drag-and-drop to
move teams between groups.

## 1. Layout

Per league, stacked vertically by tier level (1 on top), each tier's groups laid out as
side-by-side lanes underneath it:

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

A league with **zero** `Tier`/`Group` rows at all renders as just the "Unplaced" list, full
width — no tier/group columns to show. This covers two distinct real cases that render
identically for now:

1. A **new league this season** that hasn't had its tier/group structure set up yet (that
   setup is the separate, still-unbuilt League/Tier/Group CRUD work — the board only
   *consumes* structure, never creates it).
2. A **permanently groupless league** — a standing league type that intentionally never
   subdivides into groups. Confirmed (2026-07-22): copy-forward never converts one type into
   the other; a copied league keeps whatever tier/group setup its source had.

**Open question, not resolved:** the domain model has no explicit way to say "this league is
*intentionally* groupless" vs. "not configured yet" — both are just leagues with zero
`Tier`/`Group` rows. Not needed for the board (both render the same), but the future
League/Tier/Group CRUD work may want an explicit flag so an admin isn't nagged to add groups
to a league that will never have them.

**No new backend read planned.** Every value needed (`League`, `Tier.level`, `Group`,
`TeamParticipation`) is already available via existing list endpoints; the client-side
join-everything pattern already used throughout this app (`RegionPlacementsService` and
effectively every other data service in `dtfb-frontend-ng`) is sufficient at the expected
per-region data volumes. The deferred "placement-board read" endpoint idea (mentioned in
`09-league-model.md`-adjacent notes) is not being built for this — revisit only if this
specific client-side join becomes a real performance problem.

## 2. Team movement

Three distinct actions, deliberately handled differently:

- **Move within a tier** (drag a team from one group lane to another under the *same* tier
  section): a plain `TeamParticipation.group` update to the target group.
- **Promote/relegate** (drag a team's card from one tier's lane into a *different* tier's
  lane): same underlying operation (`group` update to a group under the new tier) — the
  tier-crossing drag *is* the promotion/relegation, no separate action needed.
- **Unplace** (drag into/out of the league's "Unplaced" lane): `group` set to `null` — already
  a valid, existing state (`TeamParticipation.group` is nullable; a team can be registered for
  a league without a group assignment, e.g. seed's `tp-by25-4`).

**Leaving the league entirely** (deleting the `TeamParticipation`) is **not** a drag target —
deliberately kept as an explicit, confirm-gated button per team card (reusing the existing
`remove-placement-dialog` pattern), both because a stray drag shouldn't be able to trigger a
delete, and to match this app's established convention that destructive actions get an
explicit confirm step, not an implicit gesture.

**Accessibility requirement, not optional:** every drag-based move needs a non-drag
equivalent to meet this repo's WCAG-AA/axe bar — a "Move to…" affordance per team card
(button/menu opening a group picker, reusing the existing move-dialog's picker logic) as the
keyboard/screen-reader path, alongside the drag gesture for mouse users.

## 3. Randomize (per-tier shuffle)

A button, scoped to **one tier at a time** (not a whole league — mixing tiers would silently
promote/relegate teams as a side effect of a "shuffle"), that redistributes every team
currently in any of that tier's groups (plus, arguably, unplaced teams belonging to that
league/tier) evenly at random across the tier's groups.

### Safety gate: when is a tier "safe" to randomize without confirmation?

**Decided: check for the actual existence of `Round`/`MatchDay` rows under the tier's groups,
not `Group.groupState`.**

Investigated (2026-07-22) whether `Group.groupState` (`PLANNED → READY → RUNNING → FINISHED`,
plus `CANCELED`) could gate this — it can't be trusted for this purpose:

- `GroupController.updateGroup` (`PUT /v1/groups/{id}`) is a plain full-DTO overwrite with
  **no transition validation at all** — any state can be written over any prior state
  (`FINISHED → PLANNED` is accepted exactly as readily as `PLANNED → READY`).
- **Nothing automatically transitions it.** `RoundService`/`MatchDayService` (fixture
  generation) never touch `groupState`. The only automatic write anywhere is
  `CopyForwardService` resetting a cloned group to `PLANNED`.
- So `groupState` is a manually-maintained label with no enforced link to reality — a region
  admin could generate fixtures and simply forget to flip the group's state. Gating a
  data-destructive randomize on that label would only be as safe as everyone remembering to
  keep it accurate.

Instead: a tier is "draft" (randomize fires immediately, no confirmation) exactly when **none
of its groups have any `Round` or `MatchDay` rows yet** — a fact you can query directly, not a
flag someone has to remember to set. Once a tier has any fixtures, randomizing would silently
orphan the match/round data's team-composition assumptions, so at that point either require an
explicit confirm step, or (leaning towards this) disable randomize for that tier entirely —
there's no good reason to randomize a tier that's already live.

### Mechanics (once built)

No bulk-update endpoint needed — mirrors this app's established pattern (e.g. the roster
editor's staged save, club-teams captain assignment): compute the new random assignment
client-side, then fire one `PUT /v1/team-participations/{id}` per affected participation via
`forkJoin`, matching every other multi-write action already in this codebase.

## 4. Summary of what's NOT being built right now

- No new backend read (hierarchical placement-board endpoint) — client-side join is enough.
- No `Group` state-machine validation — out of scope; the randomize safety check
  sidesteps `groupState` entirely by querying `Round`/`MatchDay` existence instead.
- No explicit "this league is intentionally groupless" flag on `League` — deferred to
  whenever League/Tier/Group CRUD gets built, if it turns out to matter there.
- No bulk-update endpoint for randomize — N individual updates via `forkJoin`, same as
  elsewhere.
