# Competition & Team-Registration Model

> Single source of truth for the **season → competition → division** structure, the
> **federation-configurable rule system**, and **team registration** (placement + roster).
> The backend owns and enforces this model; frontends only *consume* it.
> Companion to [`authorization-model.md`](./authorization-model.md) (roles & scope) and
> [`role-concept.md`](./role-concept.md).

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
   └─ duration (start/end) + registration window
```

| Term | Entity | Meaning |
|---|---|---|
| **Season** | `Season` | time container: **duration** (start/end) + **registration window** |
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
  registrationOpen: boolean,             // manual master switch
  registrationOpensAt, registrationClosesAt,   // scheduled window
  // isOpen is DERIVED (not stored): registrationOpen && now ∈ [opensAt, closesAt]
}
```
The registration window is the **roster-management window** (when team admins may edit
rosters); deadlines that gate finer actions come from the `RuleSet` (§4).

### 3.3 `Competition` — gains deadlines + optional rule override

```
Competition {
  name, season,                          // (renamed from Event)
  ruleSet?: RuleSet,                     // nullable → inherits Federation defaults (§4)
  // own deadlines live inside the (overriding) RuleSet
}
```

### 3.4 `TeamParticipation` — placement (NEW)

```
TeamParticipation {
  team, competition, season,             // a team's entry in one competition+season
  pool,                                  // the Division it is placed into
  copiedFromParticipationId?,            // carry-over chain (promotion/relegation history)
}
```
- Owned by `REGION_ADMIN`. Created en masse by **copy-forward**, then edited
  (promote/relegate = move to another `Pool`; add/drop teams).
- A team may have several participations in one season (league + cup).

### 3.5 `RosterEntry` — roster, first-class & auditable (NEW)

```
RosterEntry {
  participation,                         // belongs to a TeamParticipation
  player,                                // existing Player
  addedAt, removedAt?,                   // timestamps → rule evaluation ("cast after deadline?")
  status,
}
```
First-class (not a `playerIds[]` blob) so rules like *"no new members X days before
playoffs"* and transfer history are answerable and auditable.

### 3.6 Roster lifecycle (on the participation)

```
DRAFT ──submit──▶ SUBMITTED ──confirm──▶ CONFIRMED
  ▲                   │                       │
  └──── reopen ───────┴───────── reopen ──────┘   (by an admin with final say)
```
Team admin edits in `DRAFT`; **submit** locks the roster; an admin **confirms** (or reopens).
The `Season` window + `RuleSet` deadlines gate who may do what, when.

## 4. RuleSet — federation-configurable rules

Rules are **configuration**, not code. Stored **hybrid**: typed columns for known rules +
a JSON blob for federation-specific extras.

```
RuleSet {
  // typed (known) ──────────────────────────────
  pointSystem: { win, draw, loss, ... },         // also feeds StandingController
  rosterRules: { minSize, maxSize, ... },
  deadlines: [ Deadline ],
  // extensible ─────────────────────────────────
  extra: JSON,                                    // federation-specific knobs
}

Deadline {
  key,                                            // e.g. "ROSTER_LOCK", "TRANSFER", "WITHDRAWAL"
  // EITHER absolute OR relative to a competition-tree milestone:
  absoluteAt?: Instant,
  relativeToStageId?, offsetDays?,                // e.g. playoff Stage.startDate − 14d
}
```

**Resolution hierarchy** (most specific wins):

```
Federation.ruleSet        // DEFAULTS — each region/LV configures its own
    └─ Competition.ruleSet // OPTIONAL OVERRIDE — null ⇒ inherit federation defaults
```

`Federation` == the region/LV (see `authorization-model.md`); `canManageRegion(federationId)`
already gates editing it.

## 5. Governance: rules + final say

Two enforcement tiers, with a human escape hatch so the rule engine can start small:

1. **Hard rules** — auto-enforced from config (window open? past a deadline? roster size
   legal?). Violations are rejected by the backend.
2. **Manual authority** — anything not codified falls back to a human with **final say**.
   Two forms, both supported:
   - **Blanket override** — an admin with authority acts ignoring (overridable) rules.
   - **Change-request approval** — a team submits a request; it escalates
     `CLUB_ADMIN → REGION_ADMIN` (whoever exists, walking *up* the hierarchy).

> The manual hatch means we ship a couple of hard rules + override first, then codify more
> rules over time without ever being blocked.

## 6. Authorization mapping (reuses the existing model)

Roles already exist: `ADMIN > REGION_ADMIN > CLUB_ADMIN > TEAM_ADMIN` (+ `COMPETITION_ORGANIZER`, renamed from `EVENT_ORGANIZER` alongside the entity).
**Captain = `TEAM_ADMIN`** (no separate role, no magic links).

| Action | Gate |
|---|---|
| Edit `Season` window/duration, `Competition`, `RuleSet` | `canManageSeason` / `canManageRegion(federationId)` |
| Copy-forward, placement, promote/relegate (`TeamParticipation`) | `REGION_ADMIN` (`canManageRegion` / `canManageSeason`) |
| Edit own team's roster / submit | `TEAM_ADMIN` via `canRepresent(team)` (already exists), gated by window + deadlines |
| Confirm/reopen, override, approve change-requests | `CLUB_ADMIN` → `REGION_ADMIN` (final say) |

## 7. Build plan (L0 → L3)

Each layer ships value on its own. Start with the rename so all later naming is final.

- **L0 — Rename + Season CRUD + window**
  - Backend: `Event → Competition` rename; add `Season` duration + window fields (+ derived
    `isOpen`); migration.
  - Frontend: regenerate client; **Season CRUD view** (region-scoped) modeled on the player
    view + edit dialog (name, duration, window). *This is the first migrated module.*
- **L1 — Placement (admin-driven)**
  - Backend: `TeamParticipation`; **copy-forward** operation (clone divisions + placements
    from previous season); promote/relegate/add/drop endpoints; authz.
  - Frontend: region division/placement view (copy-forward + drag/move teams between pools).
- **L2 — Roster + hard rules**
  - Backend: `RosterEntry`; roster edit/submit/confirm lifecycle; `RuleSet` (Federation
    defaults → Competition override, hybrid storage); enforce window + deadlines as **hard
    rules**; blanket admin override.
  - Frontend: team roster editor (`TEAM_ADMIN`), open/closed + deadline indicators.
- **L3 — Approvals + richer rules**
  - Backend: change-request entity + escalation (`CLUB_ADMIN → REGION_ADMIN`); more codified
    rules from config.
  - Frontend: approval queue; request flow for team admins.

## 8. Deferred / out of scope (for now)

- **Self-service team sign-up** (a team *applying* to a competition) — fits open tournaments,
  not leagues; revisit after L3.
- Magic-link captain access (old SM flow) — not needed; captain = `TEAM_ADMIN`.
- Federation-wide license/roster windows from the old global `SeasonDto` — license features
  were removed from the admin app; out of scope unless licensing returns.
