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
TLS vhost.

**⚠️ `X-Frame-Options` must be `SAMEORIGIN`, not `DENY`, on this vhost specifically.** Keycloak's
JS adapter loads `/silent-check-sso.html` (part of the app's own static assets) in a hidden,
same-origin iframe on every page load to silently check for an existing session. A blanket
`X-Frame-Options: DENY` — a common general hardening default, and fine on the Keycloak/backend
vhosts — blocks even this same-origin frame, and the app never gets past a blank page (Firefox
surfaces it as `NS_ERROR_XFO_VIOLATION` on the `silent-check-sso.html` request):

```nginx
add_header X-Frame-Options "SAMEORIGIN" always;
```

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
`BootstrapAdminInitializer` already auto-created a minimal `app_user` row for that `dtfb_id` on
first boot (see `07-prod-keycloak-and-admin-bootstrap.md` §3) — **before** this script gets to run.
The script's own `INSERT` for that same `dtfb_id` then fails with a duplicate-key error on
`app_user.UK_app_user_dtfb_id`, and since it's one multi-row `INSERT` statement, it fails
atomically and nothing after that line in the script executes either (script aborts there). Fix
once, then re-run the whole script:

```sql
DELETE FROM role_assignment WHERE user_id = (SELECT id FROM app_user WHERE dtfb_id = 'flock');
DELETE FROM app_user WHERE dtfb_id = 'flock';
```

Once the script creates the real `user-flock` row (with the fixed id/name the script expects),
`BootstrapAdminInitializer` becomes a harmless no-op on every future restart. To avoid hitting this
every time the DB gets wiped and reseeded, consider unsetting `SPORTSHUB_BOOTSTRAP_ADMIN_DTFB_ID`
in `.env` once `00-bootstrap.sql` has run at least once — it already creates both global admins,
making this redundant going forward.

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
- Seeded federation/club/player names with umlauts (e.g. "Saarländischer",
  "Nordrhein-Westfälischer", "Görlich", "Jürgen", "Köln") render correctly, not as "SaarlÃ¤ndisch".
- Deleting a tracker issue shows no console error (client expects `204`, not an empty `200`).

**Status: all of the above confirmed working on the first live rollout as of 2026-07-28**
(`v0.2.2`), including admin login end-to-end after the `X-Frame-Options` fix above.

## 9. Redeploying a schema-breaking migration (DB wipe + reseed)

Some migrations are explicitly schema-shape-only, not a data-preserving backfill (e.g.
`V2__user_player_split.sql` — see its own header comment), because this deployment only ever had
~7 testers and no real production history to carry forward. Applying one of these against the
VPS's already-populated tables fails outright (FK/constraint violations), so the database gets
wiped and rebuilt from `00-bootstrap.sql` + `seed-region.sh` instead of migrated in place. First
done for `v0.3.0` (2026-08-26) — full steps and every gotcha hit along the way:

1. Cut and push a new tag (`git tag -a vX.Y.Z -m "..." && git push origin vX.Y.Z`), wait for
   `release.yml` to publish the image.
2. **Back up the tracker issues first** — unlike the rest of the DB (disposable seed/demo data),
   `tracker_issue`/`tracker_issue_vote` hold real tester-reported feedback and have zero foreign
   keys into the domain schema (only an internal vote→issue FK), so they survive a wipe+reseed
   independently:
   ```bash
   docker compose exec sportshub-db mysqldump -u sportshub -p"$SPORTSHUB_DB_PASSWORD" \
     --default-character-set=utf8mb4 --no-create-info --complete-insert \
     sportshub tracker_issue tracker_issue_vote > tracker-backup-$(date +%F).sql
   ```
   `--no-create-info` is deliberate — restoring the dump's own `CREATE TABLE` would overwrite
   whatever the new version's Flyway migrations just built (e.g. `V6` widening `status` from an
   `enum` to `varchar`) with the old, possibly-stale definition. Only the `INSERT`s get replayed.
3. Wipe the schema — the app's own `sportshub` user already has `ALL PRIVILEGES` scoped to the
   `sportshub` database name, which MySQL honors for `CREATE`/`DROP DATABASE` on that exact name
   even without a global grant, so **no root password needed**:
   ```bash
   docker compose exec sportshub-db mysql -u "$SPORTSHUB_DB_USER" -p"$SPORTSHUB_DB_PASSWORD" \
     -e "DROP DATABASE sportshub; CREATE DATABASE sportshub CHARACTER SET utf8mb4;"
   ```
4. Update `SPORTSHUB_TAG` in `.env`, `docker compose pull && docker compose up -d`, tail logs
   until Flyway applies every migration and Hibernate `validate` passes.
5. Restore the tracker backup now that the schema exists:
   ```bash
   docker compose exec -T sportshub-db mysql -u sportshub -p"$SPORTSHUB_DB_PASSWORD" \
     --default-character-set=utf8mb4 sportshub < tracker-backup-*.sql
   ```
6. Reseed per §7. **`00-bootstrap.sql` is not idempotent and assumes an empty schema** — if it was
   already run once against this database before (e.g. a partial/failed earlier attempt), rows
   from that attempt collide with the fresh `INSERT`s; there's no partial-skip, only a clean wipe
   or hand-editing the script for a single retry.
7. Keycloak is untouched by any of this — the 12 tester accounts don't need recreating, only the
   backend's own rows.

**Gotchas hit doing this the first time (all now fixed in the repo, kept here for the next time):**
- `00-bootstrap.sql` used to `INSERT` the root federation (`fed-dtfb`) itself, colliding with
  `V5__federation_hierarchy.sql`'s own seed of that exact row — Flyway runs first, so the script's
  insert always failed with `ERROR 1062` on that row. Fixed: the script no longer inserts it.
- If `SPORTSHUB_BOOTSTRAP_ADMIN_DTFB_ID` is set, `BootstrapAdminInitializer` auto-creates a
  minimal `app_user` row for that `dtfb_id` on boot, before `00-bootstrap.sql` runs — the script's
  own insert for the same `dtfb_id` then fails with `ERROR 1062` on
  `app_user.UK_app_user_dtfb_id`. Fix once (`DELETE FROM role_assignment WHERE user_id = (SELECT
  id FROM app_user WHERE dtfb_id = '...'); DELETE FROM app_user WHERE dtfb_id = '...';`), then
  re-run; consider unsetting the env var afterward to avoid recurring on every future wipe.
- `seed-region.sh`'s `login()`/`api()` used `curl -sf` piped straight into `jq` — combined with
  `set -euo pipefail`, a failed login/request used to kill the whole script silently, before its
  own "Login failed" diagnostic ever ran. Fixed: failures now print `HTTP <code>: <body>` to
  stderr before exiting.
- A wrong `DTFB_API_CLIENT_SECRET` produces Keycloak's generic `unauthorized_client: Invalid
  client or Invalid client credentials` regardless of *why* it's wrong (stale value, bad
  client/realm config). `kcadm.sh get clients -r dtfb --fields ...,secret` (bulk list) does **not**
  reliably return the live secret — query the dedicated sub-resource instead, the same one the
  Admin Console's Credentials tab uses:
  ```bash
  CLIENT_UUID=$(docker compose -f docker-compose.prod.yaml exec keycloak \
    /opt/keycloak/bin/kcadm.sh get clients -r dtfb -q clientId=dtfb-api --fields id --format csv --noquotes | tail -n1)
  docker compose -f docker-compose.prod.yaml exec keycloak \
    /opt/keycloak/bin/kcadm.sh get "clients/${CLIENT_UUID}/client-secret" -r dtfb
  ```

## Residual risks (accepted, not blocking for a short test window)

- Fixed, shared, weak tester passwords (`region`/`team`/`admin`) — mitigated by the realm's
  brute-force lockout, but this deployment should be locked down or torn down after the test
  window ends.
- `given_name`/`family_name` don't populate from Keycloak (Lightweight Access Tokens strip profile
  claims) — display names come from the seed script's direct `player` table inserts instead.
