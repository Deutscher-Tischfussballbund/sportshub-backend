# API consumers & authorization (multi-frontend)

**Kind:** decision · **Status:** the identity model AND audience gating are settled and in code; the
*public read tier* (which specific GETs go anonymous) is still open, flagged below.

sportshub-backend is intended to serve **several frontends with different purposes** — the admin app
today, a team portal next (doc 06), and plausibly a public results site and/or a mobile client later.
This doc records how that works without special-casing each consumer, and the one discipline that keeps
the shared API from silently becoming one app's backend.

Related: [02 role concept](./02-role-concept.md), [03 authorization model](./03-authorization-model.md),
[04 ACLs vs scoped RBAC](./04-authorization-acls-vs-scoped-rbac.md),
[06 team portal split](./06-frontend-team-portal-split.md),
[07 prod Keycloak & bootstrap](./07-prod-keycloak-and-admin-bootstrap.md).

---

## 1. Authorization is identity-based, not client-based

The backend is a **pure OAuth2 resource server** (`SecurityConfig`, stateless, `@EnableMethodSecurity`).
Its `.jwt()` decoder validates a token's **issuer** (`…/realms/dtfb`), **signature** (JWKS), and
**expiry**. It does **not** care which OAuth *client* minted the token — there is no audience validator
(see §5).

Every authorization decision is computed from **who the human is**, never how they logged in:

```
JWT → `dtfb_id` claim → Player → role_assignment rows → canManage*/canOrganize*/canRepresent…
```

`canManageSeason(id)`, `canManageLeague(id)`, `canEditRoster(id)` etc. resolve the caller's
`role_assignment` grants and walk the target entity up the domain tree to a matching scope (doc 03).
Roles live in **our DB, keyed on `dtfb_id`** — not in Keycloak, not on the client.

**Therefore multiple Keycloak clients "just work."** A region admin logging in through the admin app,
a future team portal, or a mobile app gets identical authority, because the grants hang off their
identity. Clients are just different front doors for the same users.

### Onboarding a new frontend — the touchpoints (none are authz code)

1. **A Keycloak client** in the `dtfb` realm that emits the **`dtfb_id` claim** — same client scope /
   protocol mapper as `dtfb-admin-web`. The backend keys on it, and `PlayerRegistryService`
   lazily auto-provisions a `Player` from it (+ `email`/name claims) on first call.
2. **CORS origin** — add the frontend's origin to `sportshub.cors.allowed-origins` (per-profile).
3. **Redirect URIs / web origins** on the client.
4. **Allow-list the client id** — add it to `sportshub.security.allowed-clients` (§5). Without
   this the resource server now rejects its tokens outright, regardless of role. If the new
   consumer is a machine/service client that also needs to write (no end user, so it can't hold a
   `RoleAssignment`), also add an `ApiClientGrant` row for it (§5).

No change to `AuthorizationService`, the gates, or the DTOs (beyond the one wired app-write example
in §5). A new human-facing consumer inherits the whole role/scope model for free and simply
exercises the slice of the API its users are authorized for.

---

## 2. Endpoint shape: shared **core**, not per-frontend **BFF**

For a genuinely multi-consumer API, the risk is not the CRUD surface — it's letting one app's screens
dictate endpoint shapes. Split the surface in two:

**Core (belongs here) — domain resources + domain projections.** Reusable by any consumer:
- **Resource CRUD** — `/v1/seasons`, `/v1/leagues`, `/v1/tiers`, `/v1/groups`,
  `/v1/team-participations`, roster lifecycle, etc. Domain-oriented; every consumer that manipulates the
  domain uses the same endpoints.
- **Domain projections** — read-models named after a *domain* concept, useful to more than one consumer.
  `GET /v1/leagues/{id}/structure` (tier→group + participation counts) is the canonical example: an admin
  board, a portal, and a public standings navigator can all consume it.

**Not here (belongs in the frontend, or a future per-frontend BFF) — screen shapes.** A read tailored to
one app's *layout* or *workflow* couples the shared core to that app and other consumers fight it. Tell:
it is named after a **screen** or bundles a **workflow-specific** slice.

### The principle

> **Core API = domain resources + domain projections. Screen/view shapes live in each frontend's own
> service layer (and later a per-frontend BFF if one needs heavy bespoke aggregation). Name endpoints
> after domain nouns, never after screens.**

### Worked example — the placements view

The admin placements screen needs resolved rows (team/league/group names) **and** a region-scoped
"placeable teams" picker. The right split:
- **Core:** the CRUD (`team-participations`) and the domain projection (`leagues/{id}/structure`) — shared.
- **Frontend:** composing those into the table shape, and deriving the region-filtered candidate list,
  lives in the admin app's `RegionPlacementsService`. It is *not* a `getPlacementBoardPage` endpoint in the
  core.

This is why we resolved *not* to add a screen-shaped placement-board endpoint to the backend: the
frontend owns its view composition, keeping the core consumer-neutral. Genuinely shared aggregation
(structure, standings) stays server-side; workflow-specific shapes do not.

We don't need a separate BFF **tier** now (YAGNI) — just keep the core clean so introducing one later
(or the planned `projects/portal` split, doc 06) stays cheap. The portal will reuse the same CRUD +
`canManageSeason`/roster gates with zero backend changes, cut at `SUBMITTED`.

---

## 3. Different purposes map onto existing mechanisms

- **Writes** are already gated by role/scope — a team portal's `team_admin` can edit only their roster
  (`canEditRoster`); an admin above the team confirms (`canConfirmRoster`). No per-app logic.
- **Authenticated reads** — each consumer reads the slice its user can see (same gates).
- **Public/anonymous reads** — the one thing *not* yet supported (§4).

---

## 4. OPEN DECISION — the public read tier

Today `SecurityConfig` is `anyRequest().authenticated()`: **everything except Swagger/H2/OPTIONS needs a
token.** A public, read-only results site (no login) cannot read anything anonymously. §5's
`dtfb-service` client narrows this gap but doesn't close it: it lets a registered app read without
an *end user* logging in, but the app itself still needs a client-credentials token — the site's
own visitors never see or need one, so this is invisible to them either way. A truly anonymous
consumer (no credential at all, from anyone) still needs explicit `permitAll()` routes, decided
here. This is the same "read tier" gap noted in the authorization work — and multiple purposes make it the real prerequisite for
a differently-purposed public frontend.

It is **additive** (no rework): decide which GETs are public and `permitAll()` them. Candidates
(per the model): results, standings, schedules/match-days, rankings, and directory-style reads. Writes and
anything region/club/team-scoped stay authenticated. Design this before building a public consumer; capture
the chosen set here when decided.

---

## 5. Audience gating + app-level write grants (settled)

The resource server now validates the JWT's `azp` (authorized party — which Keycloak client
requested it) against `sportshub.security.allowed-clients`, in addition to issuer/signature/expiry
(`JwtDecoderConfig`, replacing the auto-configured `JwtDecoder` bean — Spring Boot backs off once
you define your own). A token from an unlisted client is rejected outright (401), before
`AuthorizationService` ever runs. Today's list: `dtfb-admin-web` (the admin app's real users),
`dtfb-api` (the confidential client `seed-region.sh` ROPC-logs real users in through — same
people, different front door, so it must be listed too), `dtfb-service` (a new
`serviceAccountsEnabled` client for machine/no-end-user callers, `dtfb-keycloak/scripts/clients.json`).

**Two independent authorization paths, one authentication mechanism:**

- **Humans** — unchanged: JWT → `dtfb_id` claim → `RoleAssignment` → `AuthorizationService`.
- **Apps/service clients** — JWT → `azp` claim, no `dtfb_id` (no end user) → a new
  `ApiClientGrant` row (`de.dtfb.sportshub.backend.access.apiclient`), looked up by `clientId`
  (= `azp`) → `ApiClientAuthorizationService` (exposed as `@apiClientAuthz`). Deliberately a
  **separate table and service**, not layered onto `RoleAssignment`/`AuthorizationService` — an
  app's permissions are managed independently (`/v1/admin/api-clients`, global-admin-only CRUD,
  read included — unlike most admin CRUD in this app, since this data controls write access for
  machine clients). A grant has `writeAccess` (read is implicit for any allowed client) and a
  `scopeType`/`scopeId` (reusing the `ScopeType` enum, mirroring `RoleAssignment`'s shape for a
  familiar mental model, but no shared code).

**One wired example, not a general retrofit** (per §2's core-vs-screen discipline — a reviewed
slice, not blanket access): `PUT /v1/matches/{id}` (match-result reporting) now also accepts an
in-scope, write-granted `ApiClientGrant`:
`@PreAuthorize("@apiClientAuthz.canOrganizeMatch(#id) or @authz.canOrganizeMatch(#id)")`.
**`@apiClientAuthz` must be checked first** — it always returns cleanly (`false` for a human token
or a client with no grant), whereas `@authz.canOrganizeMatch` throws for a token with no `dtfb_id`
rather than returning `false`; SpEL `or` short-circuits left-to-right, so throwing on the left
would never let a legitimate grant on the right get evaluated. Extend this pattern endpoint by
endpoint as real machine consumers need more, the same way `@authz` gates are added today.

---

## Status

| Concern | State |
|---|---|
| Identity-based, client-agnostic authz | ✅ settled, in code — multi-frontend ready |
| New-frontend onboarding = Keycloak client + CORS + redirect URIs + allow-list | ✅ no other backend code |
| Core-vs-BFF endpoint discipline | ✅ principle adopted (this doc); hold it in review |
| Audience gating (`azp` allow-list) | ✅ settled, in code (§5) |
| App-level write grants (`ApiClientGrant`) | ✅ settled, in code — one wired example (match updates, §5) |
| Public read tier | ⬜ open — design before a public consumer |
