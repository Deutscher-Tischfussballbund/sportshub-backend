# API keys — backend-issued, read-only machine access

> **Implemented (2026-09-25, SPO-48).** Machine consumers (e.g. a public results site) no longer get
> a Keycloak client plus a write-capable `ApiClientGrant`. They get a **backend-issued API key**,
> sent as `X-API-Key`. Only its SHA-256 hash is stored, and it is **read-only by construction**:
> `ApiKeyAuthenticationFilter` admits only `GET`/`HEAD`, and never on `/v1/admin/**` or `/v1/auth/**`.
> Every write needs an identifiable human (JWT → `dtfb_id` → `RoleAssignment`). Supersedes the
> app-level write grants of [10-api-consumers-and-authz.md](./10-api-consumers-and-authz.md) §5; the
> `azp` allow-list itself stays, for the user-facing Keycloak clients.
>
> See also: [10-api-consumers-and-authz.md](./10-api-consumers-and-authz.md) (identity-based authz,
> the open public read tier §4), [03-authorization-model.md](./03-authorization-model.md),
> [18-player-gender.md](./18-player-gender.md) (`genderDetail` masking, which applies to keys too).
>
> Question raised: store API keys only in the backend, not via Keycloak — and make them read-only,
> with writes only by identifiable users?
> Short answer: **yes — hashed keys in our DB, sent as `X-API-Key`, read-only enforced centrally
> in one filter; the write path for apps is removed entirely.**

## Context

Doc 10 §5 (2026-08-26) modelled each machine consumer as its own Keycloak client: the `azp` claim
had to be on `sportshub.security.allowed-clients`, and an `ApiClientGrant` row keyed on that client
id could carry `writeAccess` + a scope. There was one wired write: `PUT /v1/matches/{id}` accepted
`@apiClientAuthz.canOrganizeMatch(#id)`. A dedicated `dtfb-service` Keycloak client (client
credentials) existed for this.

SPO-48 reverses two parts of that:
- **Storage:** keys live only in the backend, not as Keycloak clients.
- **Writes:** API keys are read-only. Writes must be traceable to an identifiable user.

## Options considered

| | How | Verdict |
|---|-----|---------|
| Keep Keycloak clients + `ApiClientGrant` (doc 10 §5) | Machine token via client credentials; grant row may allow writes | Rejected. The ticket explicitly wants backend-only storage and no writes via keys; a service account is not an identifiable user. |
| **Backend-issued keys in `X-API-Key` (chosen)** | Random key shown once, SHA-256 hash stored; a filter authenticates it before JWT handling | **Chosen.** No Keycloak provisioning per consumer, revocable in one click, and read-only enforcement lives in one place. |
| Keys in `Authorization: Bearer <key>` | Same keys, standard header | Rejected. The backend would have to tell a key from a JWT by its shape; a dedicated header is unambiguous. |
| Store a scope (region, …) on each key now | `scopeType`/`scopeId` as on the old grant | Rejected for now. Reads aren't scoped for anyone yet (the open read tier, doc 10 §4), so a stored scope would be unenforced and misleading. |

## Decision

- **`ApiKey`** (`access/apikey`, table `api_key`, `V12__api_keys_replace_api_client_grants.sql`,
  which also drops `api_client_grant`):
  - Fields: `name`, `keyPrefix` (shown in the UI), `keyHash` (unique hex SHA-256), `active`,
    `expiresAt` (optional `LocalDate`, inclusive last valid day), `createdAt`, `createdByDtfbId`,
    `lastUsedAt` (refreshed at most once a minute).
  - A key is `dtfb_` + 32 random bytes (base64url). A fast hash is enough because the key is
    high-entropy random, not a user-chosen password.
- **Plaintext exactly once:** `POST /v1/admin/api-keys` returns `ApiKeyCreatedDto { apiKey, key }`.
  List and update never carry the key or its hash. Rotation = create a new key, delete the old one.
  Admin CRUD (`getAllApiKeys`/`createApiKey`/`updateApiKey`/`deleteApiKey`) is `@authz.isAdmin()`,
  reads included, because the list reveals which machine consumers exist.
- **`ApiKeyAuthenticationFilter`**: added to `SecurityConfig`'s chain before
  `BearerTokenAuthenticationFilter`. It is deliberately not a Spring bean, because Boot would also
  register a bean `Filter` for every request, outside the security chain. It enforces centrally,
  regardless of how an endpoint is gated:
  - unknown / deactivated / expired key → 401 `INVALID_API_KEY`
  - any method other than `GET`/`HEAD` → 403 `API_KEY_READ_ONLY`
  - `/v1/admin/**` or `/v1/auth/**` → 403 `API_KEY_PATH_NOT_ALLOWED`. Some admin reads (e.g.
    `/v1/admin/auth/user-search`, which returns emails, and `/assignments`) are open to any
    logged-in user today (the read-tier gap). They must not be handed to third-party apps.
  - key plus an `Authorization` header → 400: a request is either a user or a machine
- **An API-key caller is no user.** Its `ApiKeyAuthentication` is not a `Jwt`, so:
  - every `@authz` check refuses it (`currentJwt()` → `AccessDeniedException` → 403);
  - `UserRegistryService.currentUser(null)` now throws `AccessDeniedException` instead of an NPE;
  - `PlayerGenderVisibility` gives it the public `gender` only.
- **Write path for apps removed:** `writeAccess`, `ApiClientAuthorizationService`, and the
  `@apiClientAuthz` clause on `PUT /v1/matches/{id}` are gone. That endpoint is human-only again
  (`@authz.canOrganizeMatch`).
- **`dtfb-service` retired:** it is dropped from the `allowed-clients` defaults. It was never
  committed to `dtfb-keycloak/scripts/clients.json` (only a local change there, discarded). The
  client may still exist in running Keycloak realms (local, VPS) and is deleted there by hand. The
  allow-list keeps `dtfb-admin-web` and `dtfb-api` (both mint tokens for real people).
- **Swagger (SPO-44):** `OpenApiConfig` declares an `api-key` scheme (`apiKey` in header `X-API-Key`).
  It is offered only on the operations a key may call (GET/HEAD outside `/v1/admin/**` and
  `/v1/auth/**`) via the `apiKeyOnReadEndpoints` customizer. Every other operation keeps the global
  token-only requirement, so Swagger UI never sends a key that would be refused, nor a key plus a
  token (400).
- **Frontend:** `admin/api-keys` replaces `admin/api-clients`. Creating a key shows it once, with a
  copy button; keys can be deactivated, reactivated, edited (name/expiry) and deleted.

## What did NOT need to change

- Human authentication and authorization: JWT → `dtfb_id` → `RoleAssignment` → `@authz`, the
  `azp` allow-list, and every `@PreAuthorize` gate. Requests without `X-API-Key` pass through the
  new filter untouched.
- The domain read endpoints. A key reads exactly what any logged-in user can read today, minus the
  admin/auth paths.

## Watch for / triggers to revisit

- **The public read tier (doc 10 §4).** When reads get scoped or partly `permitAll()`, decide key
  scoping at the same time: a region-scoped key only makes sense once reads can be scoped.
- **A machine consumer that needs to write** (e.g. live scoring). Per SPO-48 it must act as an
  identifiable user (its own Keycloak account with a normal `RoleAssignment`), not via a key.
  Revisit only if that proves unworkable.
- **Read-volume abuse.** There is no rate limiting per key yet; `lastUsedAt` is the only usage
  signal. Add limits if a key is ever exposed in a browser or leaks.
