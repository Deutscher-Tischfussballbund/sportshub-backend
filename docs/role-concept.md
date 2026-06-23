# Role & Authorization Concept

> **Status: DRAFT — foundation for discussion.** Sections 1–4 describe what exists in
> the code today. Section 5 is a *straw-man* proposal, not agreed policy. Section 6
> collects the open questions that must be answered before we gate endpoints — most
> importantly the granularity of `TEAM_ADMIN` / `CLUB_ADMIN` and how federation game
> rules interact with self-service.

---

## 1. Purpose

Define who may do what in the DTFB sportshub backend, so authorization can be enforced
consistently (via `@PreAuthorize` + the `@authz` `AuthorizationService`). This document is
the reference for that decision; nothing here is locked until we agree on Section 6.

## 2. Domain hierarchy

There are **two trees**, and authorization scopes attach to the organisation tree.

**Organisation**

```
DTFB (national)
└── Federation  (Landesverband = "region")
    └── Club
        └── Team
            └── Player (roster membership — NOT modelled yet, see 6.3)
```

**Competition** (owned by the federation running it)

```
Season → Competition → Discipline (+ Category) → Stage → Pool → Round
       → MatchDay (home/away Team, Location) → Match → MatchSet / MatchEvent
```

Key relationships in code: `Club.federationId → Federation`; `Team.club → Club`
(recently changed from `Team → Federation`); `Season.federation → Federation`.

## 3. Current roles (as coded in `Role.java`)

| Role | Wire value | Scope | Intended meaning |
|---|---|---|---|
| `ADMIN` | `admin` | GLOBAL | DTFB national administrator |
| `REGION_ADMIN` | `region_admin` | REGION | Landesverband administrator |
| `CLUB_ADMIN` | `club_admin` | CLUB | Club administrator |
| `TEAM_ADMIN` | `team_admin` | TEAM | Team administrator |
| `TOURNAMENT_UPLOADER` | `tournament_uploader` | CLUB | Enters/imports tournament data for a club |
| `REGION_TOURNAMENT_UPLOADER` | `region_tournament_uploader` | REGION | Enters/imports tournament data for a region |

A role is granted via a `RoleAssignment` (player + role + scopeType + scopeId). `scopeId`
points at a federation / club / team depending on scope; null for GLOBAL.

## 4. Scope model & inheritance

Higher scopes inherit everything below: **GLOBAL ⊃ REGION ⊃ CLUB ⊃ TEAM**. A region admin
administers every club and team within that region; a club admin administers the teams in
that club. This is already implemented in `AuthorizationService.canManageScope(...)` and used
by the (live) `grant`/`revoke` gates.

Roles mapped onto the organisation tree (the scope each role is granted at):

```
  GLOBAL      ADMIN ............................ administers everything below
    │
  Federation  REGION_ADMIN · REGION_TOURNAMENT_UPLOADER
  (region)      └─ owns this region's competition tree:
    │              Season → Competition → Discipline → Stage → Pool → Round
    │              → MatchDay → Match → MatchSet / MatchEvent
    │
  Club        CLUB_ADMIN · TOURNAMENT_UPLOADER
    │
  Team        TEAM_ADMIN          ← granularity under debate (§6.1)
    │
  Player      (roster membership — not modelled yet, §6.3)

  Inheritance:  GLOBAL ⊃ REGION ⊃ CLUB ⊃ TEAM
  • An admin granted at one level administers everything beneath it.
  • Uploader roles feed data only — no structural edits, no role grants.
```

Two role *families*:

- **Admin roles** (`*_ADMIN`) — manage structure and may grant roles within their scope.
- **Uploader roles** — feed data only (imports, match results); never edit structure or grant roles.

## 5. Straw-man capability model (NOT final)

| Role | Proposed capabilities |
|---|---|
| **ADMIN** | Everything: federations, categories, all competitions, all clubs/teams, all imports, grant/revoke any role. |
| **REGION_ADMIN** | Full competition CRUD within the region; manage clubs & teams in-region; grant region/club/team roles in-region; region-wide imports. Not federations/other regions. |
| **CLUB_ADMIN** | Manage the club's teams; grant club/team roles in-club; submit/confirm results for the club's teams. Not the competition bracket. |
| **TEAM_ADMIN** | Edit the team; submit/confirm that team's results. **(scope/usefulness under debate — see 6.1)** |
| **TOURNAMENT_UPLOADER** (club) | Import data + enter results for the club's teams. No structural edits, no grants. |
| **REGION_TOURNAMENT_UPLOADER** (region) | Same, region-wide. Primary actor for `POST /import/data`. |

Condensed endpoint → minimum gate (full list in the team chat / prior analysis):

| Area | Gate |
|---|---|
| Federations, Categories | `@authz.isAdmin()` |
| Seasons | `canManageRegion(season.federationId)` |
| Teams | `canManageClub(team.clubId)` |
| Competition tree (event…matchevent) | region admin of owning region (needs hierarchy resolver; interim `isAdmin()`) |
| Bulk import | region uploader / region admin / global |
| Match result submit/confirm | actor over a participating team (team/club/region) |
| Player directory & search, list a player's roles | any admin role |
| List all role assignments | global admin (or scope to caller's regions) |
| Grant / revoke roles | `@authz.canGrant` / `canRevoke` (already live) |
| `me/*`, grantable-scopes, regions, standings, all league reads | `authenticated()` (future public) |

---

## 6. Open questions / decisions needed

### 6.1 Granularity of `TEAM_ADMIN` and `CLUB_ADMIN` — the central question

**The tension.** The most operationally sensitive action is **changing a team's roster**.
Game rules differ **per federation** (e.g. *no roster changes within N days of the playoffs*).
If a per-team `TEAM_ADMIN` can self-service their own roster, then **every team's changes must
be supervised** to ensure each federation's rules are followed — which is overkill at scale.

Raised in discussion: it may be **wiser to have a single team-administration authority per
federation** (region level) rather than one admin per team.

**Options to weigh:**

- **A — Per-team `TEAM_ADMIN` (fine-grained self-service).**
  - *Pros:* scales by delegation; teams own their data; low central workload.
  - *Cons:* only safe if the **system enforces the rules** (see 6.2); otherwise needs heavy
    human supervision; many grants to administer; higher compliance risk.
- **B — Consolidated team administration at federation/region level (no per-team admin).**
  - *Pros:* central control; one place to enforce each federation's rules; far fewer grants;
    `TEAM_ADMIN` as a distinct role may then be unnecessary.
  - *Cons:* bottleneck for large federations; the central person does all roster edits;
    less team autonomy.
- **C — Hybrid: team self-service, but the system enforces rule windows.**
  - Teams edit their own roster; the system **blocks** illegal changes (e.g. roster-freeze
    period); region admin can override.
  - *Pros:* autonomy + compliance without manual supervision.
  - *Cons:* requires modelling the rules (per-federation config) — the most build effort.

**This choice cascades:** it decides whether `TEAM_ADMIN` (and possibly `CLUB_ADMIN`) survive
as granular roles, and whether roster management is an endpoint at all.

### 6.2 Are federation game rules enforced by the system or by people?

The right granularity in 6.1 **depends on this.** If rules (roster-freeze windows, eligibility,
etc.) are **encoded and enforced** by the backend, fine-grained self-service (Option A/C) is
safe. If rules live only in regulations and human judgement, fine-grained self-service demands
supervision → favour consolidation (Option B). **Decision needed:** which rules, if any, does
the system enforce, and are they configurable per federation?

### 6.3 Roster / team-membership is not modelled yet

`Player ↔ Team/Club` membership does not exist in the data model (`PlayerMapper` returns an
empty `clubs` list as a placeholder). The very capability under debate — *managing team
members* — has **no entity and no endpoint** today. So 6.1 is partly a "what do we build"
question, not only "who may call it."

### 6.4 Uploader scope semantics

`TOURNAMENT_UPLOADER` is club-scoped, `REGION_TOURNAMENT_UPLOADER` region-scoped. The only
write that clearly fits is `POST /import/data` (currently a single bulk endpoint). **Decide:**
is import region-level only, or also club-level? Should uploaders also enter individual match
results, or only bulk-import?

### 6.5 Visibility of "all role assignments"

`GET /v1/admin/auth/assignments` returns **all** assignments. **Decide:** global-admin only, or
should a region admin see (only) their region's assignments? The latter needs result-scoping,
not just a gate.

### 6.6 Who may grant which roles (already partly decided)

Implemented today: a granter may grant within a scope they administer (region admin → region/
club/team roles in-region; club admin → club/team roles in-club; global → anything). Confirm
this stays, and confirm whether a region admin may mint **another region admin** for their own
region (currently allowed).

---

## 7. Implementation phasing (deferred until 6.1–6.2 are settled)

1. **Safe now, decision-independent:** gate `Federation`, `Category` → `isAdmin()`;
   `Season` → `canManageRegion`; `Team` → `canManageClub`; PII reads → any-admin;
   `assignments` → global admin.
2. **After uploader/result decisions (6.4):** `canUploadRegion` for import; `canSubmitResult`
   for matchday result/confirm.
3. **After the hierarchy resolver:** gate the competition tree by owning region.
4. **After 6.1/6.2/6.3:** model team membership + roster rules; decide if `TEAM_ADMIN` stays.

> Nothing in Section 7 should be built until the team/club-admin granularity (6.1) and
> rule-enforcement (6.2) questions are answered, since they may remove or reshape roles.
