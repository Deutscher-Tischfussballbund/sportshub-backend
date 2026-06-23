# Authorization Model

> Single source of truth for **who may do what** across all DTFB frontends.
> The backend owns and enforces this model; frontends only *consume* it for UX.

## 1. Principles

1. **Authentication is shared, authorization is centralized.**
   Keycloak answers *"who are you?"* (OIDC, shared by every frontend). This backend
   answers *"what may you do?"*. Roles live in **our** database (`role_assignment`),
   never in Keycloak — because roles carry **scope** (region/club/event ids), which is
   our domain data, not Keycloak's.
2. **The backend enforces; frontends present.**
   A frontend role check is never a security boundary — only a UX nicety (hide a button
   the user can't use). Every protected action is gated server-side via `@PreAuthorize`
   and the `@authz` (`AuthorizationService`) component. A role system is only "real" once
   enforcement is real.
3. **Expose capabilities, not raw roles.**
   With many frontends, none should reimplement the role→permission mapping. The backend
   derives and exposes capabilities (`/v1/auth/me/areas`, `/v1/admin/auth/grantable-scopes`)
   so frontends consume answers, not rules. The generated `@dtfb/api` client is the shared
   contract — every frontend imports the same `Role`/`ScopeType` enums; nobody hardcodes
   role strings.

## 2. The three axes of access (+ public)

The original role set modeled only administration. A multi-frontend ecosystem needs three
distinct axes plus a public tier:

| Axis | Question | Mechanism |
|---|---|---|
| 🟦 **Org administration** | "Which slice of the federation tree do you manage?" | scope-bearing roles: `admin` / `region_admin` / `club_admin` |
| 🟩 **Function / activity** | "What *job* do you perform for this competition?" | functional roles: `event_organizer` (+ future `referee`/`result_reporter`) |
| 🟨 **Affiliation / identity** | "Who are *you* relative to this data?" (my profile, my team) | **not a role** — an ownership check against `dtfb_id` / team roster |
| ⬜ **Public** | "Is this data world-readable?" | no auth — a separate read-access decision |

## 3. Scopes

A role grant is scoped to one node of the access hierarchy. `scopeId` points at that node.

```
GLOBAL
  └── REGION   (Federation.id)
        └── CLUB   (Club.federationId → Federation)
              └── TEAM   (Team.clubId → Club)

EVENT   (Event.id)  ── belongs to Season ── belongs to Federation (REGION)
```

- **`EVENT`** is the competition scope. **Leagues and tournaments are both `Event`s**
  (`Event → Season → Federation`); the league (match-days under `Round`) vs. tournament
  (pools/stages under the same Event) distinction is structural *below* the Event, so one
  `EVENT` scope covers both.
- An Event resolves up to a REGION via `Event.season.federation`, so a `region_admin`
  can manage events in their region and a global `admin` can manage all — this slots
  `EVENT` into the existing hierarchy for enforcement.

## 4. Role catalog

### Current — keep (org-administration backbone)
| Role | Scope | Meaning |
|---|---|---|
| `admin` | GLOBAL | Federation-wide administration; can grant any role. |
| `region_admin` | REGION | Manage a Landesverband: its clubs, teams, seasons, events. |
| `club_admin` | CLUB | Manage one club: its teams. |
| `team_admin` | TEAM | Act for a single team (e.g. submit/confirm league match-day results as the team's representative). |

### New — functional
| Role | Scope | Meaning |
|---|---|---|
| `event_organizer` | EVENT | Run one competition (league or tournament): structure, scheduling, results, standings for that Event. |

### Deprecated — superseded, pending removal
| Role | Why |
|---|---|
| `tournament_uploader` (CLUB) | Right idea (a functional "run results" role), wrong axis — anchored to CLUB instead of the competition. Superseded by `event_organizer` (EVENT). |
| `region_tournament_uploader` (REGION) | Same; a `region_admin` already covers region-wide event management. |

> Removal cascades to the generated `@dtfb/api` client and i18n keys, so it is a separate
> follow-up, not part of the initial role definition.

### Future — add only when a concrete frontend needs them
| Candidate | Scope | Driven by |
|---|---|---|
| `referee` / `scorer` | MATCH (assignment) | live scoring app — not yet built; do not add speculatively. |
| `result_reporter` | TEAM / EVENT | if team-rep reporting needs to be distinct from `team_admin`. |

## 5. Frontend → action map

Lineup confirmed by the team (2 exist today: admin console :4200, DTFB-ID :4500).

### Admin console (:4200) — federation back-office
| Action | Resource | Scope | Axis |
|---|---|---|---|
| Manage federations/regions | Federation | GLOBAL | 🟦 admin |
| Manage clubs in my region | Club | REGION | 🟦 region_admin |
| Manage teams in my club | Team | CLUB | 🟦 club_admin |
| Manage seasons, events, disciplines & locations in my region | Season/Event/Discipline/Location | REGION | 🟦 region_admin |
| Manage global classifications (categories) | Category | GLOBAL | 🟦 admin |
| Grant/revoke roles | RoleAssignment | scoped | 🟦 (already gated) |

### DTFB-ID / member portal (:4500) — identity & self-service
| Action | Resource | Scope | Axis |
|---|---|---|---|
| View/edit **my** profile, licenses, club membership | Player (self) | — | 🟨 subject = me |
| Register a DTFB-ID | Player | — | ⬜ / 🟨 |

→ Zero roles; everything is an ownership check against the authenticated `dtfb_id`.

### Public results / rankings site — spectators
| Action | Resource | Scope | Axis |
|---|---|---|---|
| Browse results, standings, schedules, rankings, directories | Match/Standing/MatchDay/Player/Club | — | ⬜ public |

→ Requires some **read** endpoints to become public (today everything is `authenticated()`).

### Tournament manager — organizers running an event
| Action | Resource | Scope | Axis |
|---|---|---|---|
| Build/seed pools, rounds, stages | Pool/Round/Stage | EVENT | 🟩 event_organizer |
| Schedule matches | Match | EVENT | 🟩 event_organizer |
| Enter/correct results, publish standings | Match/MatchSet/Standing | EVENT | 🟩 event_organizer |

### League match-day app — team representatives
| Action | Resource | Scope | Axis |
|---|---|---|---|
| Submit my team's match-day result | MatchDay `result` | TEAM (participant) | 🟦 team_admin + 🟨 must be a participating team |
| Confirm the opponent's submission | MatchDay `confirm` | TEAM (opponent) | 🟦 team_admin + 🟨 affiliation |

→ Gate = "team representative **of a participating team**": a `team_admin` grant **plus**
an affiliation check that the team is `teamHome`/`teamAway` of that match-day.

### Live scoring / referee app — at-the-table scoring (future)
| Action | Resource | Scope | Axis |
|---|---|---|---|
| Record set/point events, finalize score | MatchSet/MatchEvent/Match | MATCH | 🟩 referee (future) |

## 6. Enforcement state & plan

Audit (see git history / earlier review) found ~40 write endpoints reachable by **any
authenticated user** — authorization is currently unenforced except for role grant/revoke.
Closing this is the prerequisite for the model above to mean anything.

Planned tiers (enforcement work, separate from role definition):

- **A — Config** ✅ **Done.** Split by scope along the federation tree: a **Category** is a global classification (admin-only); everything that hangs off a region — **Season, Event, Discipline, Location** — is region-scoped (a region admin owns their slice). Each resolver lives in `@authz` and a region-less entity falls back to admin-only.
  - *Global config* (Category, Import): `@authz.isAdmin()` on writes; reads stay open. Verified by `CategoryControllerSecurityTest` (401 unauth / 403 non-admin / 201 admin / 200 read).
  - *Region config*:
    - **Season** (`Season.federation`): create → `canManageRegion(#seasonDto.federationId)`, update/delete → `canManageSeason(#id)`.
    - **Event** (`Event → Season → Federation`): create → `canManageSeason(#eventDto.seasonId)`, update/delete → `canManageEvent(#id)`.
    - **Discipline** (`Discipline → Event → … → Federation`; its `Category` is just a classification, not scope): create → `canManageEvent(#disciplineDto.eventId)`, update/delete → `canManageDiscipline(#id)`.
    - **Location** (`Location.federation`, nullable = a global venue): create → `canManageRegion(#locationDto.federationId)`, update/delete → `canManageLocation(#id)`.
    - Verified by `Season`/`Event`/`Discipline`/`LocationControllerSecurityTest`. (Event *competition data* — pools/matches/standings — is Tier C, gated on the owning Event's scope incl. `event_organizer`.)
  - Also fixed `GlobalExceptionHandler`: it swallowed `AccessDeniedException` into a 500 (now **403**), and its catch-all `Exception` handler was collapsing `ResponseStatusException` into a 500 — added a handler so an explicit status (e.g. **400** "a team requires a club") is honoured.
  - **No clubless teams**: `TeamService.create` now rejects a missing `clubId` with 400; every team belongs to a club.
- **B — Org hierarchy** (Federation = admin; Club = region admin of its region; Team = club admin of its club): new `@authz.canManageClub/canManageTeam` resolvers. ✅ **Done** — `FederationController` writes → `@authz.isAdmin()`; `TeamController` create → `@authz.canManageClub(#teamDto.clubId)` (clubless team ⇒ global admin only), update/delete → `@authz.canManageTeam(#id)` (added, resolves Team → Club → region/club admin). Verified by `TeamControllerSecurityTest`. Note: `ClubController` is read-only (no club write endpoints exist yet), so "Club" gating is N/A until those are added. Moving a team across clubs is gated on the *current* club only — revisit if cross-club moves need the target club checked.
- **C — Competition data** (Stage/Pool/Round/MatchDay/Match/MatchSet/MatchEvent) ✅ **Done.** Each entity resolves up its `@ManyToOne` spine to the owning Event — `Stage → Discipline → Event`, `Pool → Stage → …`, `Match → MatchDay → Round → …` — via `CompetitionEventResolver` (one component owns the 8 repos; the chain is eagerly fetched by a single load). The capability `@authz.canOrganize<Entity>` = the region/global admin above that event **OR** an `event_organizer` appointed to it. This is deliberately broader than `canManageEvent` (event meta — admins only): organizers run the competition, not the event's place in the federation tree. Writes gated: create on the parent's resolver (e.g. Stage create → `canOrganizeDiscipline(#stageDto.disciplineId)`), update/delete on the entity's own (`canOrganizeStage(#id)`). `StandingController` is read-only (standings are computed) so it has no write gate — N/A like `ClubController`. MatchDay `create/update/delete` are Tier C; its `result`/`confirm` remain Tier D. Verified by `CompetitionAuthorizationIntegrationTest` (real cascade + `event_organizer` allow, outsider/other-event deny) and `CompetitionControllerSecurityTest` (all 21 write endpoints deny a non-organizer).
- **D — Result flow** (MatchDay `result`/`confirm`) ✅ **Done.** Gated by `@authz.canReportMatchDay(#id)`: the caller must represent a *participating* team (axis 🟦+🟨) — a `team_admin` of `teamHome`/`teamAway`, or an admin above that team (club/region/global). Note this is the one place `team_admin` grants authority: `canManageTeam` (Tier B, team CRUD) deliberately excludes it, so Tier D has its own `canRepresent` check. The submitter-vs-opponent distinction (confirm) stays in `MatchDayService` — a person may not confirm their own submission (tracked by `submittedByDtfbId`); the gate only establishes affiliation. Verified by `MatchDayResultAuthorizationIntegrationTest` (participant submits → opponent confirms; own-submission rejected; non-participant & role-less forbidden; anon 401).
- **Read tier**: decide which GETs are public (axis ⬜).

Each resolver lives once in `AuthorizationService` (`@authz`) so it is the shared capability
engine every frontend relies on.
