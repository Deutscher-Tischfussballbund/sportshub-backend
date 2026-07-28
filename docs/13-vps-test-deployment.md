# VPS test deployment — full stack (sportshub-backend + dtfb-frontend-ng + Keycloak)

> How to stand up a real test server for the sportshub migration: Keycloak + sportshub-backend +
> MySQL + the Angular admin app, fronted by nginx, with 12 tester accounts and a full example
> season pre-seeded. This is the corrected, as-executed version of the original deployment plan —
> every gotcha below was hit for real on the first live rollout and is fixed at the repo level,
> not just noted here.

## 0. What must already be true in each repo (no VPS needed for these)

All three are merged as of this writing — listed so a future redeploy (new VPS, disaster
recovery) knows what it's relying on:

- **sportshub-backend**: `src/main/resources/db/migration/V1__baseline.sql` exists (Flyway
  baseline — without it, `ddl-auto: validate` fails to boot against a fresh MySQL, full stop).
  `application-prod.yaml`'s `server:` block sets `forward-headers-strategy: framework` (without
  it, any absolute URL the app builds — notably the GitHub OAuth2 client's `redirect_uri` for the
  tracker — resolves from the raw proxied request instead of the public scheme/host, and GitHub
  rejects it with *"the redirect_uri is not associated with this application"*).
- **dtfb-keycloak**: `scripts/clients.json`'s `dtfb-admin-web` client has the deployment's admin
  frontend URL in `redirectUris`/`webOrigins`. `nginx.example.conf` blocks only `/admin/*` (Admin
  Console UI + Admin REST API) — **not** `/realms/master`. See §4 for why.
- **dtfb-frontend-ng**: `docker-compose.yaml` + `.env.example` exist for the admin app stack.

## 1. Provision the VPS

- Ubuntu 22.04/24.04, a non-root sudo user (disable root SSH login + password auth once it's set
  up), Docker + compose plugin, `ufw` allowing only `22/80/443`.
- DNS: point three subdomains at the VPS IP — Keycloak, API (backend), admin (frontend). Example
  used below: `sh-id-test.dtfb.de`, `sh-api-test.dtfb.de`, `sh-admin-test.dtfb.de`.
- `docker network create dtfb` — once, before anything else. All three stacks are independent
  compose files; only this shared external network lets the backend reach Keycloak by service
  name.
- If pulling private GHCR images: `docker login ghcr.io -u <user> --password-stdin` with a
  classic PAT scoped `read:packages` (piped via stdin, never typed into shell history).

## 2. Deploy Keycloak

```bash
cd dtfb-keycloak
docker compose -f docker-compose.prod.yaml pull keycloak keycloak-setup
docker compose -f docker-compose.prod.yaml up -d
docker compose -f docker-compose.prod.yaml run --rm keycloak-setup
```

`.env` needs `KC_HOSTNAME` (public, `https://sh-id-test.dtfb.de`), `KC_BOOTSTRAP_ADMIN_USERNAME`/
`PASSWORD`, `KEYCLOAK_DB_USER`/`PASSWORD`, `KEYCLOAK_REALM=dtfb`. Leave `KC_HOSTNAME_ADMIN`,
`KC_PROXY_HEADERS`, `KC_HTTP_ENABLED` as the compose defaults. `SMTP_*` can stay unset — every
test account gets `emailVerified` set manually (see §5), so nobody needs a real verification
email.

**Retrieve the `dtfb-api` client secret now, while `.env` is fresh** (needed later for seeding):
`setup-keycloak.mts` only writes `KEYCLOAK_CLIENT_SECRET` into `dtfb-keycloak/.env` if that file
**already existed** at the moment the setup job ran — otherwise it only prints the secret to the
job's own console output and nothing is persisted. If you missed it:

```bash
docker compose -f docker-compose.prod.yaml logs keycloak-setup | grep KEYCLOAK_CLIENT_SECRET
```

or just regenerate it via Admin Console → Clients → `dtfb-api` → Credentials → Regenerate (safe —
nothing in the running backend reads this secret; only the one-off seed script in §6 does).

## 3. nginx for Keycloak

Use `dtfb-keycloak/nginx.example.conf` as-is (already fixed) — key points if hand-rolling:

- Proxy upstream is **`127.0.0.1:8180`**, not 8080 — that's the actual host port
  `docker-compose.prod.yaml` binds (`"127.0.0.1:8180:8080"`).
- Block only `/admin/` and `= /admin`. **Do not block `/realms/master`.** Keycloak's
  `KC_HOSTNAME_ADMIN` split only retargets the Admin Console UI + Admin REST API to the
  tunnel-only hostname — it does **not** give the master realm its own frontend hostname for the
  actual OIDC login/token flow. The Admin Console is itself an OIDC client of the master realm, so
  its login redirect (`/realms/master/protocol/openid-connect/auth`) always goes through the
  single public hostname regardless. Blocking `/realms/master` breaks the Admin Console's login
  outright (shows as a blank "Something went wrong" screen with untranslated i18n keys) — not just
  one helper endpoint. The bootstrap admin account is the only thing this exposes, and it's
  protected by password + the realm's brute-force lockout (§2/realm settings) like any other
  account.
- Admin access is via SSH tunnel only, and the tunnel target must match the real bound port:
  ```bash
  ssh -L 8080:localhost:8180 user@id-host
  open http://localhost:8080/admin/
  ```

## 4. Deploy the backend

```bash
cd sportshub-backend
docker compose pull       # or `docker compose build` if no release tag has been cut yet
docker compose up -d
docker compose logs -f sportshub-backend   # Flyway applies V1, then Hibernate validate, "Started"
```

`.env` needs `SPORTSHUB_DB_USER`/`PASSWORD`, `KEYCLOAK_ISSUER_URI` (public,
`https://sh-id-test.dtfb.de/realms/dtfb`), `KEYCLOAK_JWK_SET_URI` (leave default — internal, works
since both stacks share the `dtfb` network), `SPORTSHUB_CORS_ALLOWED_ORIGINS`
(`https://sh-admin-test.dtfb.de`), `SPORTSHUB_TAG` (pin a released version, don't float `latest`),
`GITHUB_OAUTH_CLIENT_ID`/`SECRET` (see §4a), `SPORTSHUB_BOOTSTRAP_ADMIN_DTFB_ID` (optional — see
the ⚠️ below, the SQL bootstrap in §6 makes this redundant for the two global admins).

**Images only publish on version tags, not on every push to `main`** —
`sportshub-backend/.github/workflows/release.yml` triggers `on: push: tags: ["v*"]` only.
`ci.yml` runs tests but never publishes. After merging any fix into `main`, you must also cut and
push a new tag before `docker compose pull` picks it up on the VPS:

```bash
git checkout main && git pull
git tag -a v0.X.Y -m "..."
git push origin v0.X.Y
```

(`dtfb-frontend-ng` has the mirror-image gotcha: its `build-and-push.yaml` only builds
`on: push: branches: [main]`. If the app's code lives on a not-yet-merged branch, there is no
image on GHCR at all — use `docker compose build` on the VPS instead of `pull` until it's merged.)

### 4a. nginx for the backend

Must set **both** `X-Forwarded-Proto` and `X-Forwarded-Host` (or an explicit
`proxy_set_header Host $host;`), proxying to `127.0.0.1:8082`:

```nginx
proxy_set_header Host              $host;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header X-Forwarded-Host  $host;
```

Without `X-Forwarded-Proto`, the backend's `forward-headers-strategy: framework` has nothing to
trust and falls back to the raw (usually wrong-scheme) request when building absolute URLs —
breaking the tracker's GitHub OAuth2 login (`redirect_uri` mismatch). nginx's `proxy_pass` also
defaults `Host` to the *upstream* address unless set explicitly, so don't rely on it being implicit.

### 4b. GitHub OAuth App (tracker)

Dedicated OAuth App per test deployment: GitHub → Settings → Developer settings → OAuth Apps →
New. Homepage = the API subdomain; **Authorization callback URL** must be exactly
`https://sh-api-test.dtfb.de/login/oauth2/code/github`. Client id/secret →
`GITHUB_OAUTH_CLIENT_ID`/`GITHUB_OAUTH_CLIENT_SECRET` in the backend's `.env`.
`TRACKER_GITHUB_ORG` defaults to `Deutscher-Tischfussballbund`.

## 5. Deploy the frontend

```bash
cd dtfb-frontend-ng
docker compose up -d   # build: . is already wired — build locally if no GHCR image exists (see §4)
```

`.env`: `API_BASE_PATH` (`https://sh-api-test.dtfb.de`), `KEYCLOAK_URL`
(`https://sh-id-test.dtfb.de`), `KEYCLOAK_REALM=dtfb`, `KEYCLOAK_CLIENT_ID=dtfb-admin-web`,
`SHOW_FEEDBACK_WIDGET=true`. nginx proxies straight to `127.0.0.1:${HOST_PORT:-8081}`, standard
TLS vhost, no special header requirements.

## 6. Create the 12 Keycloak users

Via the SSH-tunneled admin console (§3). For **every** user: password set with **"Temporary"
unchecked** (a forced password-change screen breaks both the fixed-password plan and ROPC
seeding), and **"Email Verified" manually checked** (SMTP is off, so unverified accounts can't log
in). Full roster (2 global admins, 5 region admins each also getting a team-admin account) is in
`scripts/prod-test-seed/README.md`.

## 7. Seed data

```bash
# 00-bootstrap.sql: Federation/Club/Team/Player/RoleAssignment rows that have no other creation
# path (no create-club endpoint; players must exist before first login).
# --default-character-set=utf8mb4 is required — omitting it double-encodes every umlaut in the
# seed data on insert (bit the first rollout: "Saarländisch" stored/displayed as "SaarlÃ¤ndisch").
# The script also SET NAMES utf8mb4 itself, but that's not a substitute for the client flag.
docker compose exec -T sportshub-db mysql -u sportshub -p"$SPORTSHUB_DB_PASSWORD" \
  --default-character-set=utf8mb4 sportshub \
  < scripts/prod-test-seed/00-bootstrap.sql
```

**⚠️ If `SPORTSHUB_BOOTSTRAP_ADMIN_DTFB_ID` was set on the backend's `.env`** (e.g. to `flock`),
`BootstrapAdminInitializer` already auto-created a minimal `player` row for that `dtfb_id` on first
boot (see `07-prod-keycloak-and-admin-bootstrap.md` §3) — **before** this script gets to run. The
script's own `INSERT` for that same `dtfb_id` then fails with a duplicate-key error on
`player`, and since it's one multi-row `INSERT` statement, it fails atomically and nothing after
that line in the script executes either (script aborts there). Fix once, then re-run the whole
script:

```sql
DELETE FROM role_assignment WHERE player_id = (SELECT id FROM player WHERE dtfb_id = 'flock');
DELETE FROM player WHERE dtfb_id = 'flock';
```

Once the script creates the real `player-flock` row (with the fixed id/name the script expects),
`BootstrapAdminInitializer` becomes a harmless no-op on every future restart.

Then, per region:

```bash
export KEYCLOAK_URL=https://sh-id-test.dtfb.de
export API_BASE_PATH=https://sh-api-test.dtfb.de
export DTFB_API_CLIENT_SECRET='<from §2>'
./scripts/prod-test-seed/seed-region.sh <federation-id>   # or no arg for all 5
```

Builds Season → League → Tier → Group → TeamParticipation → RosterEntry for real through the REST
API, authenticated via ROPC as each region admin's own Keycloak account.

## 8. Verify

- Backend: `docker compose logs -f sportshub-backend` shows Flyway applying `V1`, Hibernate
  `validate` passing, "Started".
- Each of the 12 accounts logs into the admin frontend and lands on the correct area, no
  `/no-access` bounce.
- Each region shows its seeded season/league/tiers/groups/teams/rosters immediately.
- Tracker: `https://sh-api-test.dtfb.de/tracker/index.html` — public list/create/vote work with no
  login; "Log in with GitHub" completes without the redirect_uri warning; convert/delete work for
  an org member.
- Keycloak Admin Console loads cleanly through the SSH tunnel (`http://localhost:8080/admin/`
  after `ssh -L 8080:localhost:8180 user@id-host`) — no blank "Something went wrong" screen.

## Residual risks (accepted, not blocking for a short test window)

- Fixed, shared, weak tester passwords (`region`/`team`/`admin`) — mitigated by the realm's
  brute-force lockout, but this deployment should be locked down or torn down after the test
  window ends.
- `given_name`/`family_name` don't populate from Keycloak (Lightweight Access Tokens strip profile
  claims) — display names come from the seed script's direct `player` table inserts instead.
