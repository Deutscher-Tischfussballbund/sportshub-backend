# Sportshub Backend

The central backend for the DTFB (Deutscher Tischfußballbund) sports platform. It owns the
domain data — federations, clubs, teams, players, seasons, competitions, matches and results —
and is the **single source of authorization** for every DTFB frontend (admin console, member
portal, public results, tournament manager).

Authentication is delegated to Keycloak (OIDC); this service answers *"what may you do?"* via a
DB-backed, scope-aware role model. See [`docs/authorization-model.md`](docs/authorization-model.md).

## Tech stack

| | |
|---|---|
| Language / runtime | Java 25 |
| Framework | Spring Boot 4 (Web MVC, Data JPA, Security, OAuth2 Resource Server) |
| Build | Gradle (wrapper included — no local Gradle needed) |
| Database | H2 (dev, file-backed) · MySQL 8 (prod) |
| Migrations | Flyway (prod only) |
| Auth | Keycloak / OIDC — JWT resource server |
| API docs | springdoc OpenAPI + Swagger UI |
| Mapping | MapStruct · Lombok |

## Quick start (development)

**Prerequisite:** JDK 25 ([OpenJDK](https://jdk.java.net/25/)) — bundled with your IDE or installed locally.

```shell
./gradlew bootRun
```

The app starts on **http://localhost:8082** with the `dev` profile (the default). That's enough to
boot and explore the API — Keycloak and the external API are only needed for authenticated calls
and player-import flows respectively (see below).

**From your IDE:** a run configuration is provided in [`.run`](.run) and is picked up automatically by IntelliJ.

**Build a runnable jar:**

```shell
./gradlew clean build
java -jar build/libs/sportshub-backend-0.0.1-SNAPSHOT.jar
```

### Supporting services

| Component | Port | Needed for | How |
|---|---|---|---|
| Sportshub Backend | 8082 | — | this app |
| Keycloak | 8084 | authenticated requests | separate [`dtfb-keycloak`](https://github.com/deutscher-tischfussballbund/dtfb-keycloak) repo (`docker compose up -d`) |
| H2 / MySQL | — | persistence | H2 in dev (auto), MySQL in prod |

## Profiles & configuration

Two Spring profiles, selected via `SPRING_PROFILES_ACTIVE`:

### `dev` (default) — [`application-dev.yaml`](src/main/resources/application-dev.yaml)
- **H2**, file-backed at `./data/testdb` (survives restarts; the `data/` dir is git-ignored).
- `ddl-auto: create-drop` — Hibernate rebuilds the schema each boot, then seeds it from
  [`seed/access-seed.sql`](src/main/resources/seed/access-seed.sql) (demo federations, clubs, a team,
  and the dev users below).
- **Flyway is disabled** here — the dev schema is throwaway.
- H2 console at **http://localhost:8082/h2-console** (JDBC URL `jdbc:h2:file:./data/testdb;AUTO_SERVER=TRUE`, user `sa`, no password).

Dev seed users (`dtfb_id` = the Keycloak username they log in with):

| `dtfb_id` | Role | Scope |
|---|---|---|
| `admin` | `ADMIN` | global |
| `region` | `REGION_ADMIN` | Bayern (`fed-by`) |
| `club` | `CLUB_ADMIN` | TFC München (`club-tfcm`) |
| `team` | `TEAM_ADMIN` | TFC München 1 (`team-tfcm-1`) |
| `test` | — | none (plain player) |

### `prod` — [`application-prod.yaml`](src/main/resources/application-prod.yaml)
- **MySQL**, all connection details env-driven.
- **Flyway owns the schema**; `ddl-auto: validate` asserts the entities match the migrated schema (Hibernate never mutates prod).
- JWKS fetched lazily from an internal URL; the issuer claim validated against the public URL (so startup never blocks on Keycloak).

Environment variables (prod):

| Variable | Default | Purpose |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://sportshub-db:3306/sportshub` | MySQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `sportshub` | DB user |
| `SPRING_DATASOURCE_PASSWORD` | *(required)* | DB password |
| `KEYCLOAK_ISSUER_URI` | `https://id.dtfb.de/realms/dtfb` | **public** issuer (validates the token `iss` claim) |
| `KEYCLOAK_JWK_SET_URI` | `http://keycloak:8080/realms/dtfb/protocol/openid-connect/certs` | **internal** JWKS endpoint (key fetch) |
| `SPORTSHUB_BOOTSTRAP_ADMIN_DTFB_ID` | *(unset)* | grants this `dtfb_id` global admin on startup — see below |
| `SPORTSHUB_CORS_ALLOWED_ORIGINS` | `https://admin.dtfb.de` | allowed CORS origin(s) for the admin console (comma-separated) |

## Authentication & authorization

- **Keycloak / OIDC** answers *who you are*. The app is a JWT **resource server**; the player
  behind a token is resolved (and lazily created on first login) from the `dtfb_id` claim.
- **Authorization is centralized here**, not in Keycloak — roles carry **scope** (region/club/team/
  competition ids), which is domain data. Roles live in the `role_assignment` table and are enforced
  server-side via `@PreAuthorize` + the `@authz` (`AuthorizationService`) component.
- Scope hierarchy: `GLOBAL → REGION (federation) → CLUB → TEAM`, plus `COMPETITION`.

Read the model docs before changing access rules:
- [`docs/authorization-model.md`](docs/authorization-model.md) — the canonical model
- [`docs/role-concept.md`](docs/role-concept.md) · [`docs/authorization-acls-vs-scoped-rbac.md`](docs/authorization-acls-vs-scoped-rbac.md)
- [`docs/competition-and-registration-model.md`](docs/competition-and-registration-model.md)

### Bootstrap admin

Granting a role requires already being an admin — so a fresh prod database has nobody who can
create the first one. Set `SPORTSHUB_BOOTSTRAP_ADMIN_DTFB_ID` (the Keycloak username of your first
admin) and on startup that player is ensured to hold a global `ADMIN` grant. It is **idempotent**
(safe to leave set across restarts) and a no-op when unset (dev uses the seed instead).

## Database migrations (Flyway)

Prod schema changes are versioned SQL in [`src/main/resources/db/migration`](src/main/resources/db/migration):

- `V1__init.sql` is the baseline, generated from the JPA entity metadata (MySQL dialect).
- Add changes as **new** `V2__*.sql`, `V3__*.sql` files — **never edit an applied migration**.
- On boot Flyway applies pending migrations, then Hibernate `validate` checks the entities match.

> Migrations are MySQL-specific, so Flyway is off in `dev` (H2 + `create-drop`). To regenerate DDL
> for a new migration, export it from Hibernate with the `MySQLDialect` and diff against the current schema.

## API documentation

With the app running:

- **Swagger UI:** http://localhost:8082/swagger-ui.html
- **OpenAPI JSON:** http://localhost:8082/v3/api-docs

The frontend's generated `@dtfb/api` client is produced from this OpenAPI document. For ad-hoc
requests you can also import the OpenAPI URL into [Bruno](https://www.usebruno.com/).

## Docker & deployment

The backend ships as a container and runs as its **own** compose stack, separate from Keycloak. The
two stacks share an external `dtfb` Docker network so the backend can reach Keycloak by service name.

Configuration is supplied via a **`.env` file** next to `docker-compose.yaml` — Compose loads it
automatically for the `${...}` placeholders in the compose file. Copy the committed template and
fill it in (`.env` itself is git-ignored, so secrets never get committed):

```shell
cp .env.example .env              # then edit: set SPORTSHUB_DB_PASSWORD, KEYCLOAK_ISSUER_URI, …
docker network create dtfb        # one-time, shared with the Keycloak stack
docker compose build              # build the image locally, or `docker compose pull` a release
docker compose up -d
```

`.env` variables (see [`.env.example`](.env.example) for the full template):

| Variable | Required | Purpose |
|---|---|---|
| `SPORTSHUB_DB_PASSWORD` | **yes** | password for the `sportshub-db` MySQL (app + db container) |
| `KEYCLOAK_ISSUER_URI` | **yes** | public Keycloak issuer; must match the token `iss` claim |
| `SPORTSHUB_BOOTSTRAP_ADMIN_DTFB_ID` | first deploy | `dtfb_id` granted global admin on startup (idempotent) |
| `SPORTSHUB_DB_USER` | no (`sportshub`) | DB username |
| `KEYCLOAK_JWK_SET_URI` | no (internal default) | JWKS endpoint over the `dtfb` network |
| `SPORTSHUB_TAG` | no (`latest`) | image tag to run; pin a release in prod |

> The two **required** vars use `${VAR:?}` in the compose file, so `docker compose up` fails fast
> with a clear message if they're missing.

- [`Dockerfile`](Dockerfile) — multi-stage, Java 25, layered-jar extraction, non-root user.
- [`docker-compose.yaml`](docker-compose.yaml) — `sportshub-backend` + a dedicated `sportshub-db`
  (MySQL). The backend gates on its DB's healthcheck; the cross-stack Keycloak dependency is handled
  by lazy JWKS + `restart: unless-stopped` rather than `depends_on` (Compose can't span projects).

### Releases

Tagging `vX.Y.Z` triggers [`.github/workflows/release.yml`](.github/workflows/release.yml), which
builds and pushes `ghcr.io/deutscher-tischfussballbund/sportshub-backend:X.Y.Z` (and `:latest`).
Deploy by pinning the tag and rolling:

```shell
SPORTSHUB_TAG=X.Y.Z docker compose pull
SPORTSHUB_TAG=X.Y.Z docker compose up -d
```

Rollback = set `SPORTSHUB_TAG` back to the previous version and re-up.

## Project layout

```
src/main/java/de/dtfb/sportshub/backend/
  access/            # roles, scopes, role assignments, @authz, auth/me, bootstrap admin
  <domain>/          # federation, club, team, player, season, competition, discipline,
                     #   stage, pool, round, match, matchday, matchset, matchevent, standing, ...
  configuration/     # SecurityConfig, etc.
  base/              # BaseEntity (nano-id keyed)
src/main/resources/
  application*.yaml  # base + dev + prod profiles
  db/migration/      # Flyway migrations (prod)
  seed/              # dev seed SQL
docs/                # authorization & domain model docs
```
