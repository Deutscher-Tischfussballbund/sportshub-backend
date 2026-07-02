# Production: Keycloak integration & initial-admin bootstrap

> How the prod backend container authenticates against Keycloak, and how the **first
> administrator** gets access on a fresh deployment. Companion to
> [`authorization-model.md`](./authorization-model.md) (the DB-backed role/scope model).
> Grounded in `application-prod.yaml`, `docker-compose.yaml`, `SecurityConfig`,
> `BootstrapAdminInitializer`, and `PlayerRegistryService`.

## 1. The backend is a pure OAuth2 *resource server*

In prod the backend never logs into Keycloak and never calls Keycloak's admin API. It only
**validates the bearer JWT** on each request (stateless; `SessionCreationPolicy.STATELESS`).
Two env-driven values wire it to Keycloak:

| Property (`application-prod.yaml`) | Env var | Meaning |
|---|---|---|
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | `KEYCLOAK_ISSUER_URI` | **Public** issuer, must equal the token's `iss` claim (e.g. `https://id.dtfb.de/realms/dtfb`). Used as a *string* for validation — **no network call at boot**. |
| `…jwt.jwk-set-uri` | `KEYCLOAK_JWK_SET_URI` | **Internal** JWKS endpoint (`http://keycloak:8080/realms/dtfb/protocol/openid-connect/certs`). Signing keys are fetched **lazily on the first token**, so the backend boots even if Keycloak is not up yet. |

Setting `jwk-set-uri` explicitly (rather than deriving it from `issuer-uri`) is deliberate: it
decouples startup from Keycloak and lets the public issuer differ from the internal address.

### Networking (docker)

The backend stack (`docker-compose.yaml`, backend + its own MySQL) and the Keycloak stack
(`dtfb-keycloak/`) are **separate compose files** that share an **external docker network `dtfb`**:

```
docker network create dtfb        # one-time, before the first `up`
```

That shared network is how the backend reaches Keycloak by service name (`keycloak:8080`) for JWKS.
There is intentionally **no cross-stack `depends_on`** — the lazy JWKS fetch plus
`restart: unless-stopped` make both stacks self-healing regardless of start order.

### Security posture

Stateless; everything is `authenticated()` except `OPTIONS` (CORS preflight),
`/swagger-ui/**`, `/v3/api-docs/**`, and `/h2-console/**`. CORS origins come from
`SPORTSHUB_CORS_ALLOWED_ORIGINS`. **Roles are NOT read from the JWT** — the token only establishes
*identity*; all authority comes from DB-backed `RoleAssignment`s (see `authorization-model.md`).

## 2. The identity link: the `dtfb_id` claim

The backend resolves the caller with `PlayerRegistryService.currentPlayer(jwt)`, which reads
**`jwt.getClaimAsString("dtfb_id")`** and finds-or-creates the matching `Player` row on the first
authenticated request (profile fields stay null until then).

**Therefore Keycloak must stamp a `dtfb_id` claim into every user's access token** — a realm
protocol-mapper configured in `dtfb-keycloak` (typically the username, or a user attribute).
`dtfb_id` is the join key between a Keycloak account and a backend player. A token without it is
rejected (`401`, "Token missing dtfb_id claim").

## 3. Initial-admin bootstrap

**The chicken-and-egg:** granting a role requires already being an admin, but a fresh prod DB has no
admin (the `access-seed.sql` demo data is dev-only). Solved by `BootstrapAdminInitializer` + one env
var, mirroring Keycloak's own `KC_BOOTSTRAP_ADMIN`.

- Property `sportshub.bootstrap.admin-dtfb-id` ← env **`SPORTSHUB_BOOTSTRAP_ADMIN_DTFB_ID`**.
- On startup (`ApplicationReadyEvent`), **if it is set**, the backend finds-or-creates the `Player`
  with that `dtfb_id` and grants it a **GLOBAL `ADMIN`** `RoleAssignment`.
- **Idempotent** (the grant is a no-op if already present), so it is safe to leave configured across
  restarts. **Unset (the dev default) → does nothing.**

### Setup order (fresh deploy)

1. In Keycloak: the realm exists with the `dtfb_id` mapper, and the intended admin has a user
   account whose token will carry `dtfb_id = X`.
2. Deploy the backend with `SPORTSHUB_BOOTSTRAP_ADMIN_DTFB_ID=X` (plus DB creds,
   `KEYCLOAK_ISSUER_URI`, `KEYCLOAK_JWK_SET_URI`, `SPORTSHUB_CORS_ALLOWED_ORIGINS`).
3. On boot, the backend pre-creates player `X` and grants it GLOBAL ADMIN.
4. That person logs into the admin app via Keycloak; their token's `dtfb_id = X` → the backend
   recognizes them as the global admin. From there they grant every other role through the admin UI.

### ⚠️ The one gotcha to get right

**`SPORTSHUB_BOOTSTRAP_ADMIN_DTFB_ID` must exactly equal the `dtfb_id` claim Keycloak actually
issues for that user.** If they differ, the bootstrap grants admin to a `dtfb_id` nobody logs in as,
while the real user auto-provisions as a **role-less** player → lands on `/no-access`. Before
deploying, confirm what the realm's `dtfb_id` mapper resolves to (username vs. attribute) and set the
var to that.

## 4. Required prod env vars (summary)

From `docker-compose.yaml` (`SPRING_PROFILES_ACTIVE=prod`):

| Env var | Required | Purpose |
|---|---|---|
| `SPRING_DATASOURCE_PASSWORD` | yes | MySQL password (`sportshub-db`) |
| `KEYCLOAK_ISSUER_URI` | yes | public issuer = token `iss` |
| `KEYCLOAK_JWK_SET_URI` | no (defaults to `http://keycloak:8080/...`) | internal JWKS |
| `SPORTSHUB_BOOTSTRAP_ADMIN_DTFB_ID` | first deploy | grants the first GLOBAL ADMIN |
| `SPORTSHUB_CORS_ALLOWED_ORIGINS` | no (defaults `https://admin.dtfb.de`) | admin-console origin(s) |
| `SPRING_DATASOURCE_URL` / `_USERNAME` | no (defaults) | MySQL connection |
