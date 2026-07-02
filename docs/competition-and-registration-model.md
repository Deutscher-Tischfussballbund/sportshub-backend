# Competition & Team-Registration Model

> Single source of truth for the **season → competition → division** structure, the
> **federation-configurable rule system**, and **team registration** (placement + roster).
> The backend owns and enforces this model; frontends only *consume* it.
> Companion to [`authorization-model.md`](./authorization-model.md) (roles & scope),
> [`role-concept.md`](./role-concept.md), and
> [`season-archiving-and-deletion.md`](./season-archiving-and-deletion.md) (delete/archive policy).

## 0. Context & goal

SportsManager is being **replaced** by this backend — there is no external sync; teams
and competitions live here. This model supersedes the old NestJS `Season` /
`RegionSeason` / `Team` (draft→submit→**sync**) design. It must support both:

- **Leagues** — divisions with promotion/relegation, season over season.
- **Single tournaments** — a one-off competition with groups/brackets.

The frontend (`dtfb-frontend-ng` admin app) is migrating onto these endpoints; see the
build plan (§7) for the order in which slices ship.

## 1. Vocabulary → the competition tree

The existing competition tree is kept; only `Event` is renamed to `Competition`.

```
Season ─ Competition ─ Discipline ─ Stage ─ Pool ─ Round ─ MatchDay(teamHome/teamAway)
   │         (was Event)   │
   │                   (Category)
   └─ duration (start/end) + registration open/closed
```

| Term | Entity | Meaning |
|---|---|---|
| **Season** | `Season` | time container: **duration** (start/end) + a **registrationOpen** flag |
| **Competition** | `Competition` *(renamed from `Event`)* | a **league or a single tournament**; carries deadlines + rule overrides |
| Discipline | `Discipline` → `Category` | a category split within a competition (e.g. Herren/Damen); a competition may run several |
| Stage | `Stage` | a phase, e.g. *Hauptrunde* / **Playoffs**; anchors relative deadlines |
| **Division** | `Pool` | a league table — *or* a group/bracket in a tournament. `Pool.name` carries the label ("1. Bundesliga"). **No `kind` discriminator** — not needed today. |
| Round / fixture | `Round` / `MatchDay` | matchday number / the home-vs-away fixture |

**Naming decisions**
- `Event → Competition`: **done as step 1** (mechanical rename across entity/DTO/controller/
  repo/resolver/`importId`; no behaviour change). The authz resolver was already
  `CompetitionEventResolver`, so internal naming converges.
- `Pool` is **not** renamed to `Division`: it is the generic structural unit (division in a
  league, group/bracket in a tournament). The league label lives in `Pool.name`.

## 2. The placement ≠ roster split (core principle)

Two **different ownership domains**, deliberately separated:

| | **Placement** — which team is in which division | **Roster** — which players on a team |
|---|---|---|
| Owner | `REGION_ADMIN` | `TEAM_ADMIN` (the captain) |
| Primary action | **copy from previous season → promote/relegate**, add/drop teams | add/remove players |
| Nature | top-down admin CRUD | self-service, rule-gated |
| Lifecycle | just exists / is edited | `DRAFT → SUBMITTED → CONFIRMED` |

The old `draft→submitted` status belongs to the **roster**, not to placement. Placement is
plain admin editing whose first-class operation is **copy-forward**.

## 3. Domain entities

### 3.1 Existing (kept; `Team` stays standing)

- `Team { name, club }` — the club's **standing** identity; already referenced by `MatchDay`.
  It is *not* per-season.
- `Season`, `Competition`, `Discipline`, `Stage`, `Pool`, `Round`, `MatchDay` — unchanged
  except the rename and the new fields below.

### 3.2 `Season` — gains time fields

```
Season {
  name, federation,                       // existing
  startDate, endDate,                     // general duration
  registrationOpen: boolean,             // is roster registration open?
  archivedAt,                             // soft-delete (see season-archiving-and-deletion.md)
}
```
`registrationOpen` is the **roster-management switch** (when team admins may edit rosters).
A scheduled open/close *window* was considered and dropped — a single boolean was enough;
deadlines that gate finer actions come from the `RuleSet` (§4).

### 3.3 `Competition` — gains deadlines + optional rule override

```
Competition {
  name, season,                          // (renamed from Event)
  ruleSet?: RuleSet,                     // nullable → inherits Federation defaults (§4)
  // own deadlines live inside the (overriding) RuleSet
}
```

### 3.4 `TeamParticipation` — placement (NEW) ✅ L1 built

```
TeamParticipation {
  team, competition,                     // a team's entry in one competition
  pool?,                                 // the Division it is placed into (null = registered, unplaced)
  copiedFromParticipationId?,            // carry-over chain (promotion/relegation history)
  rosterStatus,                          // roster lifecycle (§3.6): DRAFT → SUBMITTED → CONFIRMED
}
```
- Owned by `REGION_ADMIN`. Created en masse by **copy-forward**, then edited
  (promote/relegate = move to another `Pool`; add/drop teams).
- A team may have several participations in one season (league + cup).
- **The season is derived from the competition** (a competition belongs to exactly one season), so
  it is not stored on the participation — avoids any season↔competition mismatch.

### 3.5 `RosterEntry` — roster, first-class & auditable (NEW) ✅ L2 built

```
RosterEntry {
  participation,                         // belongs to a TeamParticipation
  player,                                // existing Player
  addedAt, removedAt?,                   // membership over time; removal is a SOFT-DELETE (row kept)
}
```
First-class (not a `playerIds[]` blob) so transfer history is answerable and auditable. The active
roster = entries with `removedAt == null`. There is **no per-entry status** — the lifecycle is a
property of the *whole* roster (§3.6), a player is simply present or removed.

### 3.6 Roster lifecycle (on the participation) ✅ L2 built

```
DRAFT ──submit──▶ SUBMITTED ──confirm──▶ CONFIRMED
  ▲                   │                       │
  └──── reopen ───────┴───────── reopen ──────┘   (by an admin with final say)
```
The status is `TeamParticipation.rosterStatus` — one lifecycle per team's roster (whole-roster
completeness/approval, not per player). Team admin edits in `DRAFT`; **submit** locks the roster; an
admin **confirms** (or reopens). Editing (add/remove/submit) is a hard rule gated by
`Season.registrationOpen` + the `DRAFT` state; confirm/reopen are admin lifecycle moves.

## 4. Rules as distributed settings (RuleSet dropped)

> **Decision update:** the configurable `RuleSet` engine (typed+JSON storage, federation→competition
> resolution hierarchy, relative deadlines) is **dropped**. It was over-engineered for current needs.

Instead, **each rule is a plain setting on whichever entity it naturally belongs to**, enforced by
hardwired checks in the services (and surfaced in the frontend). This is simpler, discoverable, and
type-safe — at the cost of a schema change to add a new rule (acceptable; rules are added rarely).

Rules live where they fit, e.g.:

```
Season.registrationOpen          // roster editing window (already exists; enforced in L2)
Competition.maxRosterSize?       // (future) roster-size limit, checked on submit
Stage.<lockDate>?                // (future) a deadline anchored to a phase
```

Add rules incrementally as fields on the relevant element; there is no central rule store, no
override layer, and no resolution hierarchy. (Point system for standings likewise becomes a setting
where it fits, not a `RuleSet` field.)

## 5. Governance: final say

Rules are enforced as **hard rules** from the settings above (e.g. edits rejected when
`registrationOpen == false` or the roster is not `DRAFT`). The **final say** over a roster is a role
split, not an override engine: the team submits (`TEAM_ADMIN`), an admin **confirms/reopens**
(`CLUB_ADMIN → REGION_ADMIN`, walking *up* the hierarchy) — deliberately a different authority than
the submitter, so no one approves their own submission. A blanket admin override and change-request
escalation are **deferred** (see §8) — add them only if a real need appears.

## 6. Authorization mapping (reuses the existing model)

Roles already exist: `ADMIN > REGION_ADMIN > CLUB_ADMIN > TEAM_ADMIN` (+ `COMPETITION_ORGANIZER`, renamed from `EVENT_ORGANIZER` alongside the entity).
**Captain = `TEAM_ADMIN`** (no separate role, no magic links).

| Action | Gate |
|---|---|
| Edit `Season` window/duration, `Competition` | `canManageSeason` / `canManageRegion(federationId)` |
| Copy-forward (`canManageSeason(#targetSeasonId)`), placement create (`canManageCompetition`), promote/relegate/drop (`canManageParticipation`) | `REGION_ADMIN` |
| Edit own team's roster / submit (`canEditRoster`) | `TEAM_ADMIN` via `canRepresent(team)` (or admin above), gated by `Season.registrationOpen` + `DRAFT` |
| Confirm/reopen the roster (`canConfirmRoster`) | admin above the team (`CLUB_ADMIN` → `REGION_ADMIN`) — not the submitting team admin |

## 7. Build plan (L0 → L3)

Each layer ships value on its own. Start with the rename so all later naming is final.

- **L0 — Rename + Season CRUD** ✅ done
  - Backend: `Event → Competition` rename; `Season` duration + a `registrationOpen` boolean
    (a scheduled open/close window was tried then dropped — see
    [`season-archiving-and-deletion.md`](./season-archiving-and-deletion.md)); migration.
  - Frontend: regenerate client; **Season CRUD view** (region-scoped) modeled on the player
    view + edit dialog (name, duration, registration toggle). *This is the first migrated module.*
- **L1 — Placement (admin-driven)** ✅ backend done
  - Backend: `TeamParticipation` CRUD (add/drop = create/delete, promote/relegate = move `pool`);
    **copy-forward** operation (clone `Competition→Discipline→Stage→Pool` + placements from a source
    season; pools reset to `PLANNED`; fixtures/results not carried); authz. *Done.*
  - Frontend: region placements view + copy-forward *(done)*; manual add/move/remove UI *(pending —
    needs pool↔competition exposure in the DTOs)*.
- **L2 — Roster + hard rules** ✅ backend done
  - Backend: `RosterEntry` (soft-delete history); whole-roster lifecycle
    `DRAFT→SUBMITTED→CONFIRMED` on `TeamParticipation.rosterStatus`; edit/submit/confirm/reopen
    endpoints; hard rule = `Season.registrationOpen` + `DRAFT` (no `RuleSet` — see §4); authz split
    (`canEditRoster` vs `canConfirmRoster`). *Done.* Further rule-settings (roster size, deadlines)
    added per-element as needed.
  - Frontend: team roster editor (`TEAM_ADMIN`), open/closed indicator *(pending)*.
- **L3 — Approvals + richer rules** *(deferred; see §5/§8)*
  - Optional change-request entity + escalation (`CLUB_ADMIN → REGION_ADMIN`) if a real need appears;
    more rule-settings on the relevant elements.
  - Frontend: approval queue; request flow for team admins.

## 8. Deferred / out of scope (for now)

- **Self-service team sign-up** (a team *applying* to a competition) — fits open tournaments,
  not leagues; revisit after L3.
- Magic-link captain access (old SM flow) — not needed; captain = `TEAM_ADMIN`.
- Federation-wide license/roster windows from the old global `SeasonDto` — license features
  were removed from the admin app; out of scope unless licensing returns.
