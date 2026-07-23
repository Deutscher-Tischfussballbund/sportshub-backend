# League Model — split from tournaments (proposed)

> **Kind: model + decision (proposed, not yet implemented).**
> This doc **supersedes the single-tree "Option A"** in
> [`01-competition-and-registration-model.md`](./01-competition-and-registration-model.md) §1.1.
> Doc 01 still describes what the code does *today*; this doc describes the direction the
> model is moving. The placement/roster split and lifecycle (doc 01 §2–§3.6) are **kept
> unchanged** and only re-parented onto the new league tree.

## 0. Decision

The current model uses **one tree** (`Season → Competition → Discipline → Stage → Pool →
Round → MatchDay`) for both leagues and single tournaments, overloading each node
(doc 01 §1.1). Analysis (2026-07-07) established that **leagues and tournaments share almost
nothing above the individual game**, so we **separate the two concerns**:

- **Build a proper league model now** (leagues are what DTFB actually runs).
- **Park the tournament model** — keep only a clean *seam* (the shared game/identity core).
  A colleague's app already models tournaments and will be integrated here later; do **not**
  build the tournament aggregate now, and do not let tournament assumptions shape the league
  model.

### Why they don't share a tree

| | **League** | **Tournament (ranking circuit)** |
|---|---|---|
| Season | league season (duration + registration) | **independent** ranking season, own dates (may overlap or differ) |
| Ranked subject | **team** | **player** — even a doubles pairing splits points equally to each player |
| Participant | standing `Team` + roster (lifecycle) | **ad-hoc**: 1 player (solo) or 2 (doubles); "draw-your-partner" re-pairs each round |
| "Standing" | table *within a group* (W/D/L/pts/sets) | points *across many events* → season ranking → qualification (e.g. German championship) |
| First-class ops | copy-forward, promote/relegate | ranking-points calc, qualification cutoff |
| Structure | tier → group → matchday | event → (draw) → placement |
| Rules | match/scoring rules per league/tier (§3) | placement+participant points formula (§5) |

The only genuine overlap is the **atomic game** (`Match`/`MatchSet`/`MatchEvent`) and the
shared identities (`Player`, `Team`, `Category`, `Federation`, `Location`).

## 1. League tree

> **Naming (decided 2026-07-07):** the season entity **stays `Season`** — it is already the
> league time-container (`registrationOpen` + archive/delete) and the frontend L0 CRUD is live
> on it; renaming now is pure churn. A separate `RankingSeason` is added later with the parked
> tournament aggregate (§5). So below, "LeagueSeason" = the existing `Season` entity.

```
Season              federation, startDate, endDate, registrationOpen, archivedAt   (name kept)
  League            e.g. "Bayern Herren", one category; the copy-forward + ruleset-default unit
    Tier            "1. Bayernliga", "2. Bayernliga"   ← FIRST-CLASS; `level` (1=top) orders the promote/relegate ladder
      Group         "Gruppe A", "Gruppe B"             (one round-robin table of teams)
        Round       Spieltag N
          MatchDay  a fixture: teamHome vs teamAway
            Match   an individual game (SINGLE | DOUBLE …); `position` (1-based) orders it per the game plan
              MatchSet
        Standing    (Group × Team) — computed from the effective LeagueRuleSet
```

| Level | Entity | Meaning | vs. today |
|---|---|---|---|
| Season | `Season` | time container + registration switch | **name kept** (not renamed); `RankingSeason` added later (§5) |
| League | `League` | one category's ladder in a federation for a season; carries `category` + default ruleset | was `Competition` (renamed); `Discipline`/category folded in as an attribute |
| Tier | `Tier` | promotion/relegation level (ordinal `level`, 1 = top) | **NEW** — was baked into `Pool.name` |
| Group | `Group` | one round-robin table | was `Pool` (split: tier extracted out) |
| Round | `Round` | matchday number (Spieltag) | kept |
| Fixture | `MatchDay` | team-vs-team encounter | kept |
| Game | `Match` → `MatchSet` | individual single/double game + its sets | kept |
| Table row | `Standing` | league table entry | kept; re-parented Pool → Group |

**What drops for leagues:** `Discipline` (category becomes a `League.category` attribute — a
Damen league is a separate `League`, not a second Discipline) and `Stage` (temporal phase;
leagues here don't need it now). `Pool.tournamentMode` is replaced by
`LeagueRuleSet.playSystem`; `Pool.poolState` survives as `Group.state`.

> **Deferred:** in-season *phases* (Hauptrunde → Playoffs / Relegationsrunde). Reintroduce as a
> `Group.phase` attribute or a level only when a real league needs it — do not model
> speculatively. This is the concept the old `Stage` node held.

## 2. Season & structure lifecycle — copy-forward is a convenience, not an invariant

`League`/`Tier`/`Group` are **per-season instances** under a `LeagueSeason`.

- **Copy-forward** clones `League → Tier → Group` (+ `TeamParticipation` placements, and — unless
  opted out via `copyRoster=false` — each placement's active `RosterEntry` rows) from a prior
  season into a new one as a *starting point* (as today, doc 01 §7 L1). It **references the same
  shared rulesets** (§3), it does not clone them.
- **Roster copy** (added post-Phase-1): most teams' rosters barely change season to season, so
  copy-forward pre-fills the new participation's roster from the source's active (non-removed)
  entries instead of leaving it empty. The clone stays `RosterStatus.DRAFT` and the copy bypasses
  `RosterService` validation (direct save, same as the participation clone) — it's a starting
  point, not an enforced invariant, so a new season's roster-size rules (which may differ) don't
  block the copy; the team edits and submits normally once the new registration period opens.
- It is **not guaranteed**: teams fluctuate (unknown whether the same teams return), and the
  **number of leagues/tiers/groups can differ season to season** (common in smaller
  federations). So everything copied forward is **fully editable**, and a season can also be
  built from scratch. Provenance is tracked with `copiedFromParticipationId` (already on
  `TeamParticipation`); no rigid standing-league identity is required across seasons.
- **Promotion/relegation** = move a `TeamParticipation` to a `Group` in the `Tier`
  above/below (now a real cross-tier move, not string-matching `Pool.name`).

## 3. `LeagueRuleSet` — reusable, shareable, extensible

Leagues genuinely need configurable match & scoring rules (this **reverses the
"RuleSet dropped / distributed settings" decision of doc 01 §4 — for leagues only**). It is a
**typed** config entity — *not* the generic JSON rule-engine that was rightly dropped, and not
scattered fields.

**Reuse model (decided 2026-07-07):** a ruleset is a **standalone, reusable row**. The *same*
ruleset may apply to **multiple tiers, even multiple leagues**; a tier *may* (but need not)
have its own. So it is referenced, not embedded — editing one ruleset intentionally affects
every tier/league that references it (to diverge, clone it).

**Attachment & resolution:** referenced (nullable) from `Tier`, `League`, and — as the last
fallback — `Federation.defaultRuleSet`.
```
effectiveRuleSet(group) = group.tier.ruleSet ?? group.tier.league.ruleSet
                          ?? group.tier.league.season.federation.defaultRuleSet   (else historical 2/1/0)
```
Implemented in `LeagueRuleResolver.effectiveFor`; when nothing resolves, the `pointsX` helpers fall
back to the historical 2/1/0. Ownership: `federation` (null federation = a DTFB-global template).

**Fields (initial — expected to grow; this list is not complete):**
```
LeagueRuleSet {
  name, federation?,                      // owner; null = DTFB global template
  playSystem: ROUND_ROBIN | SWISS,        // SWISS retained — most common mode in table soccer, primarily a (parked) tournament format
  pointsWin, pointsDraw?, pointsLoss,     // standings points (pointsDraw null ⇒ draws impossible)
  gamePlan: [ GamePlanEntry ],            // ordered composition of a matchday (§3.1)
  setsPerGame,                            // best-of-N sets in one Match (1 = single set)
  pointsToWinSet,                         // goals to take a set
  matchdayDecision: ALL_GAMES | FIRST_TO, // matchday finished when all games played, or first to N game-wins
  matchdayTarget?,                        // the N for FIRST_TO
  sideSwitchAllowed: boolean,             // may home/away swap sides
  // future: tiebreakers, forfeit/no-show points, goalie rules, …
}
```

### 3.1 Game plan
The matchday composition is **per league/tier** (lives in the ruleset), not per individual
matchday. Modeled as an ordered child collection so it stays queryable and extensible:
```
GamePlanEntry { ruleSet, position: int, gameType: SINGLE | DOUBLE | GOALIE }
```
e.g. `[1:DOUBLE, 2:DOUBLE, 3:SINGLE, 4:SINGLE, 5:DOUBLE]`. A `MatchDay` instantiates its
`Match` rows from this plan.

> **Extensibility note:** the ruleset is explicitly **incomplete**. Add new rules as typed
> fields on `LeagueRuleSet` (or as new child entities where a rule is a list). Schema churn is
> cheap today (H2 `create-drop`, Flyway deferred to cutover — see
> [`../defer-flyway`](./05-season-archiving-and-deletion.md) context in the migration memory).

## 4. Placement & roster — kept, re-parented

`TeamParticipation` (L1) and `RosterEntry` (L2) are **unchanged** in behaviour and become
cleanly **league-only** (ad-hoc tournament pairings never used them). Re-parenting only:
- `TeamParticipation.competition` → **`league`** (season derived from `league.season`).
- `TeamParticipation.pool` → **`group`** (nullable = registered-but-unplaced).
- Roster lifecycle `DRAFT → SUBMITTED → CONFIRMED`, soft-delete, authz split
  (`canEditRoster`/`canConfirmRoster`), and the `registrationOpen` gate all stay as doc 01
  §3.4–§3.6 / §6 -- **except** the gate now only binds a self-editing `team_admin` (added
  post-Phase-1): an admin above the team (club/region/global, resolved via `canConfirmRoster`) may
  add/remove/submit regardless of `registrationOpen`, e.g. to fix a copy-forwarded roster before
  registration opens. `RosterController` passes the resolved `canConfirmRoster` verdict into
  `RosterService` as `actingAsAdmin`; the service doesn't depend on `AuthorizationService` itself.

**Withdrawal (added post-Phase-1):** a team drops out of a league via a **status change**, not a
delete — `TeamParticipation.status` (`ACTIVE`/`WITHDRAWN`, default `ACTIVE`) +
`withdrawnAt` (set on transition). `POST /v1/team-participations/{id}/withdraw`, gated by the same
`canManageParticipation` (region admin) as update/delete; withdrawing a participation that's
already `WITHDRAWN` is a 409. Withdrawing locks the roster (`RosterService` refuses
add/remove/submit/confirm/reopen once withdrawn) and excludes the participation from future
copy-forward (a team that dropped out doesn't automatically re-enter next season). Resolving the
team's remaining scheduled fixtures (forfeit/walkover scoring) is a **separate, deferred concern**
(§7) — withdrawal only flags the participation; already-scheduled `MatchDay`s are left for an admin
to resolve manually.

**Delete guard:** hard-deleting a `TeamParticipation` is now refused (`409 PARTICIPATION_HAS_MATCHES`)
once the team has any recorded `MatchDay` or `Standing` in that league — before this, a plain
`repository.delete(...)` neither failed nor cleaned up (`MatchDay`/`Standing` FK `Team` directly,
not `TeamParticipation`), so deleting mid-season silently orphaned the match/standing history from
its placement record. Delete stays available for the zero-history case (a misregistered team);
withdraw is the correct action once matches exist.

## 5. Tournament seam (parked — do not build now)

Kept only so the future integration has somewhere to attach; **no entities built now.**

- **Shared core** it will reuse: `Player`, `Match`/`MatchSet`/`MatchEvent`, `Category`,
  `Federation`, `Location`. Not `Team`/`TeamParticipation`/`RosterEntry` (those are league-only).
- **Sketch of the eventual aggregate** (for reference, from the 2026-07-07 analysis):
  ```
  RankingSeason (federation, start/end)              — separate from the league `Season`
    RankingRule    (see below)
    Tournament     (date, category/format Singles|Doubles, location)
      Entry        (1 player solo | 2 players doubles; ad-hoc)
      Result       (placement + participantCount → points; same points to each pairing member)
    PlayerRanking  (player × RankingSeason × category → Σ points → rank → qualification)
  ```
- **Ranking-points rule.** Points are per **player** (a pairing splits equally), from
  **placement + participant count** via a federation-and-season-specific formula, e.g.:
  ```
  MAX(1, ROUND( 10 * POW(MAX(MIN(n,100),2), 0.6) * (p-n) * LOG(p/n) / ((1-n)*LOG(1/n)) * 4 ))
    n = participant count, p = placement, m = weighting multiplier (doubles may reduce)
  ```
  This is an **arbitrary expression**, so the eventual design needs a pluggable
  strategy/expression, not tunable constants. **Ownership:** a federation may set its own rule
  per season; DTFB (global admin) may edit any; **unmanaged → DTFB default applies.**
- Because the colleague's app already covers tournaments, prefer **integrating** that model
  over rebuilding it — this seam just guarantees the league model won't have to be unpicked to
  make room for it.
- **Importer parked (decided 2026-07-07).** The `importer/` chain (`Event→Discipline→Stage→
  Group`) ingests historical/tournament SM data and needs a full rework at the integration
  anyway. It is **disabled/parked** during this migration rather than re-pathed onto the new
  tree — not worth the cost now. Revisit with the tournament integration.

## 6. Migration plan

Cheap **now**, expensive after prod cutover + Flyway baseline (Flyway deferred; dev/test are
H2 `create-drop`, entities are the source of truth). Because there is **no data to preserve**,
this is a straight code restructure, not an expand/contract data migration.

**Decisions locked (2026-07-07):** (A) **full rename + restructure** — do the renames now, not
structure-first; (1) **keep `Season`** (add `RankingSeason` later); (2) `Group` table =
**`comp_group`** (`group` is a SQL reserved word); (3) **rule enforcement is Phase 2**, after
the structural change; (4) **importer parked** (§5); (5) multi-category competition ⇒ multiple
`League`s (`League.category` is singular); (6) **Flyway stays deferred**.

### Phase 1 — structural restructure (one backend branch → PR; `./gradlew test` green at the end)
Compile-coupled, so it lands as one coordinated change. Work order:

| # | Work | Key touchpoints |
|---|---|---|
| 1 | New entities | `tier/*` (Tier→League), `leaguerules/*` (`LeagueRuleSet`, `GamePlanEntry`) — entity/dto/mapper/repo/controller/service each |
| 2 | Rename `Competition`→`League` | `competition/`→`league/`; `/v1/competitions`→`/v1/leagues`; add `category` + `ruleSet?`; op names `getAllCompetitions`→`getAllLeagues` … |
| 3 | Rename `Pool`→`Group` | `pool/`→`group/`; table **`comp_group`**; parent `stage`→`tier`; **drop `tournamentMode`** (+ its `@NotNull` on `PoolDto`); `PoolState`→`GroupState` |
| 4 | Delete `Discipline` + `Stage` | remove both aggregates; Category resolves at League-create |
| 5 | Re-parent leaves | `Round`(pool→group); `Standing`(pool→group, `@UniqueConstraint(group_id,team_id)`, `StandingService` walk `matchDay→round→group`); `TeamParticipation`(`competition`→`league`, `pool`→`group`, mapper `seasonId` via `league.season`) |
| 6 | Authz | rewrite `CompetitionResolver` spine (drop Discipline/Stage overloads + repos, add Tier/Group); `AuthorizationService` `canManageCompetition`→`canManageLeague`, re-path `canManageParticipation`/`canOrganize*`, add `canManageTier`/`canOrganizeGroup` |
| 7 | ⚠️ `SeasonStructure.java` | rewrite all 13 JPQL **string** paths (`md.round.pool.stage.discipline…`→`md.round.group.tier.league.season`) + the leaf→root bulk-delete order — no compiler help, **top risk** |
| 8 | `findVisible*` queries | re-path ~20 archived-season JPQL strings (Group/Round/MatchDay/Match/MatchSet/MatchEvent/TeamParticipation); fix derived-name queries (`findByStage_Discipline_CompetitionId`→`findByTier_League_Id`) |
| 9 | Structure read-model | `CompetitionStructureService` + `CompetitionStructureDto`: `Discipline/Stage/PoolNode`→`Tier/GroupNode`, drop `tournamentMode`, keep `participationCount` |
| 10 | Copy-forward | `CopyForwardService`: clone `League→Tier→Group` (reference shared rulesets, don't clone; reset group state `PLANNED`); `CopyForwardResultDto` drop `disciplines`/`stages`, add `tiers`/`groups` |
| 11 | OpenAPI | `OpenApiConfig.TAG_ORDER`/`TAG_DESCRIPTIONS` (drop Discipline/Stage, add Tier/Group/LeagueRuleSet, rename); fix `OpenApiDocsTest` ordering assertion |
| 12 | Seed | rewrite `access-seed.sql`: drop discipline/stage inserts, add tier/comp_group/league_rule_set/game_plan_entry, `league.category_id`, no `tournament_mode`; **IDs ≤14 chars** |
| 13 | Importer | **park** (disable) — do not re-path (§5, decision 4) |
| 14 | Tests | rename/rewrite ~30 files; the two integration tests post to `/v1/disciplines`+`/v1/stages` in `setup()` → rewire to `/v1/tiers`+`/v1/groups`; add Tier/ruleset/resolution tests |

### Phase 2 — rule enforcement (additive)
- **Done:** standings points come from the group's effective `LeagueRuleSet` — resolved by
  `LeagueRuleResolver` as `group.tier.ruleSet ?? tier.league.ruleSet`, falling back to the
  historical 2/1/0 when none is configured. `StandingService.onMatchDayConfirmed` awards
  `pointsWin`/`pointsDraw`/`pointsLoss` from it.
- **Deferred:** matchday validation against the game plan (count/type/order of `Match` rows),
  `setsPerGame`, `pointsToWinSet`, `matchdayDecision`. These need a modeled link between a
  `MatchDay` and its rule set's game plan (auto-generating a matchday's `Match` rows from the
  plan, or validating submitted results against it) — a distinct feature; today matches are
  created ad hoc with no plan linkage. Revisit when the matchday-composition flow is designed.

### Phase 3 — frontend catch-up (`feature/sportshub-backend-migration`)
Regenerate `@dtfb/api` (one churn, here); sweep call sites; rework the region **placements**
view + **placement dialog** (Competition/Pool → League/Tier/Group). **Season CRUD and the
roster editor are largely unaffected** (the reason `Season`/roster names were kept).

### Phase 4 — tournament seam / `RankingSeason`
Deferred to the colleague-app integration (§5).

### Risk register (verify first each session)
1. `SeasonStructure.java` — hand-written JPQL path strings + delete-order list, invisible to the compiler; a wrong path silently breaks delete/archive.
2. `CompetitionResolver` — typed spine feeding 8 `@authz.canOrganize*` gates; wrong overloads → silent authz holes.
3. operationId churn — every controller rename churns the generated client; do the frontend regen **once**, at Phase 3.
4. `tournamentMode` removal threads DTO validation / structure DTO / copy-forward / seed / tests — grep it all.
5. `OpenApiDocsTest` hardcodes `… < Stage < Match Event` tag order — fails until updated.
6. prod Flyway enabled + zero scripts — fine while prod isn't live; don't flip it.

## 7. Open decisions / deferred (post-Phase-1)
- **In-season phases** (playoffs/relegation) — deferred (see §1 note).
- ✅ **Federation-default ruleset source** — RESOLVED (2026-07-15): the federation default lives on
  `Federation.defaultRuleSet` (nullable) and is the last fallback in `LeagueRuleResolver`
  (`tier ?? league ?? federation.defaultRuleSet`, else historical 2/1/0). Editable via
  `PUT /v1/federation/{id}` (`defaultRuleSetId`).
- ✅ **Tier ladder order** — RESOLVED (2026-07-15): `Tier.level` (Integer, 1 = top) defines the
  promote/relegate order instead of parsing `Tier.name`; carried by copy-forward and surfaced
  (sorted) in the `LeagueStructure` read-model.
- ✅ **Match order within a matchday** — RESOLVED (2026-07-15): `Match.position` (1-based) maps a game
  to its `GamePlanEntry.position`. Enforcement/auto-generation still deferred (Phase 2, §6).
- **Standings tiebreakers**, forfeit/no-show scoring, goalie rules — extend `LeagueRuleSet` when
  needed (Phase 2+). ⚠️ **Data-capture prerequisite:** `Standing` currently holds only
  `setsWon/setsLost` (no `goalsFor/against`, no `pointsAdjustment`); add the columns before/with the
  tiebreaker rules or they can't be computed.
- **Per-match player lineup** — NOT modeled. `RosterEntry` is the team's season roster; `MatchEvent`
  carries only a loose untyped `playerId` string. There is no entity linking which roster players
  played a given `Match` (needed for player appearances/stats, and the bridge the parked tournament
  per-player ranking will need — this is what the old `ImportPlayer` carried). Decide when player
  stats or the tournament integration (§5) is picked up.
- **Tournament aggregate + `RankingSeason`** — deferred to the colleague-app integration (§5).
