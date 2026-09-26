# Rule sets as blueprints — every league owns a frozen-at-season-end snapshot

> **Proposed (2026-09-26), to confirm in the meeting on 2026-09-28. Not built.** `LeagueRuleSet`
> splits into two roles: a federation-owned **blueprint** library that can be edited freely, and a
> **snapshot** — a private copy each `League` (and optionally a `Tier`) receives when it is created
> or copied forward. The snapshot belongs to that season: editable while the season runs, frozen
> once it ends (`Season.hasEnded()`). The federation default is only consulted at creation time, so
> the runtime resolver shrinks to `tier ?? league`. This replaces the lock-and-clone protection from
> doc 14 and realizes meeting decision B-2026-08-17-1 ("rule sets are copied when a league/tier is
> created; the copy stays editable; an active/archived status keeps the list tidy"), which was
> never implemented as decided.
>
> See also: [09-league-model.md](./09-league-model.md) §3 (the `LeagueRuleSet` fields and resolution
> order), [14-team-player-versioning.md](./14-team-player-versioning.md) ("`LeagueRuleSet` edit
> lock", superseded once this is built), [12-matchday-scheduling.md](./12-matchday-scheduling.md)
> (scheduling fields live on the rule set), [16-root-federation.md](./16-root-federation.md)
> (federation ownership), [05-season-archiving-and-deletion.md](./05-season-archiving-and-deletion.md)
> (archiving, which no longer carries the protection).
>
> Question raised: should rule sets be blueprints, so that once a season is closed its rules are never
> touched again?
> Short answer: **yes — copy on use, freeze at season end; blueprints stay free to evolve.**

## Context

Today one `LeagueRuleSet` row is **shared** by every `League`/`Tier` that references it, across
seasons: `CopyForwardService` explicitly carries the reference over ("RuleSet is shared --
referenced, not cloned"). A league without its own rule set falls back at runtime to
`Federation.defaultRuleSet` (`LeagueRuleResolver`: tier → league → federation default → seeded
"DTFB Standard" → historical 2/1/0). The resolver feeds `RosterService` (roster size bounds),
`StandingService` (points) and `FixtureGenerationService` (scheduling mode).

History is protected by a lock (doc 14): `LeagueRuleSetService.update` refuses rule-affecting edits
with `409 RULE_SET_LOCKED_BY_CLOSED_SEASON` when a League/Tier of an **archived** season references
the row; `POST /v1/league-rule-sets/{id}/clone` forks it. Switching a federation's default is
separately refused once a default-dependent tier has fixtures
(`FederationDefaultRuleSetChangeBlockedException`). The lock has four gaps:

1. **Keyed on archiving, not on the season ending.** `isLockedByClosedSeason` checks
   `Season.archivedAt`; a season that has ended but was never archived can still have its rules
   rewritten.
2. **The federation-default fallback is invisible to it.** The lock only looks for direct
   League/Tier references. Editing the *content* of a federation's default rule set rewrites the
   effective rules of every closed season whose leagues relied on the fallback.
3. **Sharing across seasons.** Because copy-forward shares the row, adjusting the rules for next
   season also changes the season that is still running (until the old one is archived — then the
   edit is refused for both).
4. **Recalculation would rewrite history.** Standings are incremented at confirmation with the
   points valid at that moment. Once standings are recomputed from all results (SPO-73), the
   *current* content of a shared row would be applied to past seasons.

## Options considered

| | How | Verdict |
|---|-----|---------|
| A — Keep lock + clone, patch the gaps | Lock on `Season.hasEnded()` instead of `archivedAt`; include federation-default dependents in the lock check. | Rejected as the end state: closes gaps 1–2 but not 3–4. Protection stays a check that every new rule-affecting code path must remember, and a shared row still couples seasons. Acceptable as a stopgap until B is built. |
| B — Archived flag only | Keep lock + clone, add an `archived` flag so old rule sets drop out of pickers. | Rejected: tidies the list (the second half of B-2026-08-17-1) but leaves all four gaps. |
| C — Blueprints + per-league snapshots | Copy on use; the snapshot is season-owned and frozen when the season ends. | **Chosen.** History is safe by construction (nothing else references a snapshot), leagues can deviate individually, and it is what B-2026-08-17-1 asked for. |

## Decision

- **Two roles for `LeagueRuleSet`**, distinguished by a new column (e.g. `kind = BLUEPRINT |
  SNAPSHOT`, or a nullable `blueprint_id` on snapshots pointing at their origin):
  - **Blueprint** — owned by a `Federation` (as today via `LeagueRuleSet.federation`), listed on the
    rule-set page, freely editable and deletable when unused as a template. Never referenced by a
    `League`/`Tier` at runtime. Gains an `archived` flag that hides it from pickers.
  - **Snapshot** — created by copying a blueprint (fields + `GamePlanEntry` rows). Referenced by
    exactly one `League` or `Tier`; not listed in the blueprint library; deleted with its owner.
- **Creating a league** takes a blueprint (default: the federation's default blueprint) and stores
  a snapshot on `League.ruleSet`, which becomes mandatory. **A tier** keeps an optional override:
  choosing a blueprint for a tier stores a snapshot on `Tier.ruleSet`; no choice means "use the
  league's snapshot".
- **Resolver:** `LeagueRuleResolver` resolves `tier.ruleSet ?? league.ruleSet`. The federation
  default and the seeded fallback are consulted only when creating a league, not at runtime.
  (Gap 2 disappears.)
- **Freeze:** a snapshot is editable while its season is running and read-only once
  `Season.hasEnded()`. The edit check reads the owner's season directly — no reference scan, no
  dependence on `archivedAt`. (Gap 1 disappears.)
- **Copy-forward** copies the source league's/tier's **snapshot** into a new snapshot for the new
  season (continuity: last season's tweaks carry over). The new season can reset a league's
  snapshot from a blueprint explicitly. (Gap 3 disappears.)
- **"Apply blueprint"** — an explicit action to overwrite the snapshots of selected leagues/tiers of
  a running season from a blueprint, e.g. after the federation changed its rules for everyone.
  Refused for ended seasons.
- **Standings recalculation** (SPO-73) always uses the league's own snapshot, which cannot change
  after the season ends. (Gap 4 disappears.)
- **Removed once built:** `isLockedByClosedSeason`, `RULE_SET_LOCKED_BY_CLOSED_SEASON`,
  `FederationDefaultRuleSetChangeBlockedException` (changing the default blueprint only affects
  future leagues), and the clone endpoint's role as a workaround (clone stays useful for blueprints).
- **Migration (Flyway):** every existing `League`/`Tier` reference to a shared row gets its own
  snapshot copy; leagues without a rule set get a snapshot of their federation's current default
  (or the seeded "DTFB Standard"); rows no longer referenced by anything become blueprints. Ended
  seasons get snapshots of the rules they effectively ran with at migration time — the best
  approximation available, since the shared rows may already have been edited. No data wipe needed.
- **Frontend:** the rule-set page lists blueprints (with an "archived" filter); league/tier
  dialogs pick a blueprint and show/edit "the rules of this league" (the snapshot), read-only for
  ended seasons.

## Open questions

- **Freeze point.** Season end (`Season.hasEnded()`), as proposed, or already at the **first
  confirmed result** of the league? The stricter variant prevents changing points mid-season;
  allowing it until the season ends requires a standings recalculation on every rule-affecting
  edit (SPO-73).
- **Copy-forward source.** Previous season's snapshot (proposed) or a fresh copy of the blueprint
  it came from (picks up blueprint improvements automatically, but drops per-league tweaks)?
- **Tier snapshots.** Keep the optional per-tier override (proposed), or restrict snapshots to the
  league level and drop `Tier.ruleSet`?
- **Scope of B-2026-08-17-1's "active/archived status"** — on blueprints only (proposed), or also a
  visible status on snapshots?
