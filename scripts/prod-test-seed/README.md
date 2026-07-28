# Prod test-system seed — VPS tester rollout

One-time seed for the VPS test deployment: 2 global admins + 5 region admins (one per real
DTFB Landesverband), each region admin also getting a team-admin account, plus one full example
season per region so testing starts immediately with no manual click-through setup.

Lives outside `src/main/resources` deliberately — this is ops tooling for a one-off rollout, not
something that should ship inside the jar (unlike `src/main/resources/seed/access-seed.sql`,
which is dev-profile-only and baked into the classpath on purpose).

## Why two steps (SQL, then a script)

- **`00-bootstrap.sql`** — raw SQL for the handful of things that genuinely have no other path:
  `Federation` (needs a fixed id), `Club` (no create endpoint exists in the API at all),
  `Player` (must exist *before* an account's first Keycloak login — nothing creates one via API),
  `RoleAssignment` (the real grant endpoint needs an already-admin JWT — same chicken-and-egg
  `BootstrapAdminInitializer` already solves once for the very first admin; no benefit
  re-solving it per tester). Mirrors the exact pattern `src/main/resources/seed/access-seed.sql`
  already uses for fixed, human-readable ids.
- **`seed-region.sh`** — everything downstream (Season → League → Tier → Group →
  TeamParticipation → RosterEntry, submitted and confirmed) goes through the **real REST API**,
  logged in as each region's own freshly-empowered region-admin account. That layer holds real
  business logic (registration rules, roster-status lifecycle) that's safer to exercise for real
  than hand-replicate in SQL — and it's exactly what the account will use for real afterward.

Both verified together end-to-end against a real MySQL 8.4 + a real Keycloak instance before
being committed (full season chain, region-admin auth via ROPC, roster submit+confirm) — see the
git history for this file for the verification notes.

## Order of operations

1. **Flyway must have already applied `V1__baseline.sql`** (i.e. the app has booted at least
   once with the real prod config) — this seed assumes the schema already exists.
2. Create the 12 Keycloak users (see table below) — **no bulk-creation tooling exists**, do this
   via the SSH-tunneled admin console or the Admin REST API directly. For every user:
   - username = the `dtfb_id` column value below, **exactly** (Keycloak's `dtfb_id` protocol
     mapper maps username → the `dtfb_id` claim 1:1 — a mismatch here silently logs the person
     into a different, roleless `Player` row instead of the seeded one)
   - password set with **"Temporary" unchecked** (a forced `UPDATE_PASSWORD` action breaks both
     the fixed-password plan and the ROPC login `seed-region.sh` uses)
   - **"Email Verified" checked** (the realm requires it; SMTP is off by default, so an
     unverified account can never log in)
3. Run `00-bootstrap.sql` against the VPS's MySQL — **always with `--default-character-set=utf8mb4`**
   (the script also sets `SET NAMES utf8mb4;` itself as a first line of defense, but a client that
   defaults to something else, e.g. `latin1`, will still double-encode every umlaut on the way in;
   this bit the first real rollout — see "Fixing already-corrupted data" below):
   ```
   mysql -h<host> -u<user> -p --default-character-set=utf8mb4 sportshub < 00-bootstrap.sql
   ```
4. Run `seed-region.sh` (needs `curl`, `jq`, and the `dtfb-api` Keycloak client's secret —
   `dtfb-keycloak`'s `.env`, written there by `scripts/setup-keycloak.mts`):
   ```
   export KEYCLOAK_URL=https://sh-id-test.dtfb.de
   export API_BASE_PATH=https://sh-api-test.dtfb.de
   export DTFB_API_CLIENT_SECRET=<from dtfb-keycloak's .env>
   ./seed-region.sh
   ```
5. Testers log in for real — role and season already active, nothing left to click through.

## Account roster

| Tester | Role(s) | Federation | Region-admin username | Team-admin username |
|---|---|---|---|---|
| Marvin Flock | GLOBAL ADMIN | — | `flock` | — |
| Tim Wedemann | GLOBAL ADMIN | — | `wedemann` | — |
| Helmut Poppen | REGION_ADMIN + TEAM_ADMIN | TFVHH — Tischfussballverband Hamburg | `poppen` | `poppen-team` |
| Daniel Görlich | REGION_ADMIN + TEAM_ADMIN | MTFV — Mitteldeutscher Tischfussballverband e.V. | `goerlich` | `goerlich-team` |
| Stefan Engelhardt | REGION_ADMIN + TEAM_ADMIN | NWTFV — Nordrhein-Westfälischer Tischfussballverband | `engelhardt` | `engelhardt-team` |
| Jürgen Meyer | REGION_ADMIN + TEAM_ADMIN | STFV — Saarländischer Tischfussball Verband e.V. | `meyer` | `meyer-team` |
| Paul Fleischanderl | REGION_ADMIN + TEAM_ADMIN | TFVB — Tischfussballverband Berlin e.V. | `fleischanderl` | `fleischanderl-team` |

Passwords: `region` for every region-admin account, `team` for every team-admin account, `admin`
for the two global admins — all set in Keycloak, not in this SQL (the backend never sees a
password; Keycloak is the identity provider).

Each region gets one demo club with 3 teams (clearly named as test data, e.g. "Test-Verein
Hamburg 1/2/3" — not real club names) and one season ("Test-Saison 2026/27") with a Herren
league, one tier, one group, all 3 teams placed and roster-confirmed against a shared pool of
12 filler players (`player-f1`..`player-f12`, reused across regions — harmless for demo data,
`roster_entry` has no uniqueness constraint preventing it).

## Re-running

`00-bootstrap.sql` is **not idempotent** (plain `INSERT`s, no `ON DUPLICATE KEY`/upsert) — running
it twice will fail on the primary-key/unique-constraint collisions. If you need to redo a single
region, either delete that region's rows first or hand-edit the script for the one region you're
re-running. `seed-region.sh` accepts an optional federation-id argument to target just one region
(e.g. `./seed-region.sh fed-tfvhh`) for exactly this kind of retry.

## Fixing already-corrupted data (umlauts double-encoded)

If `00-bootstrap.sql` was run without `--default-character-set=utf8mb4` on a client whose own
default charset isn't utf8mb4, every non-ASCII character gets double-encoded on the way in (e.g.
"ä" — bytes `C3 A4` — is stored as `C3 83 C2 A4`, displaying as "Ã¤" everywhere downstream,
including the admin frontend). All tables are correctly `utf8mb4` — this is purely an insert-time
client-charset bug, not a schema issue. Repair the affected columns in place (safe to run more than
once — the `LIKE '%Ã%'` guard only touches rows that are actually still corrupted):

```sql
UPDATE federation SET name = CONVERT(CAST(CONVERT(name USING latin1) AS BINARY) USING utf8mb4)
  WHERE name LIKE '%Ã%';
UPDATE club SET city = CONVERT(CAST(CONVERT(city USING latin1) AS BINARY) USING utf8mb4)
  WHERE city LIKE '%Ã%';
UPDATE player SET first_name = CONVERT(CAST(CONVERT(first_name USING latin1) AS BINARY) USING utf8mb4)
  WHERE first_name LIKE '%Ã%';
UPDATE player SET last_name = CONVERT(CAST(CONVERT(last_name USING latin1) AS BINARY) USING utf8mb4)
  WHERE last_name LIKE '%Ã%';
```

Run this through a properly UTF-8 client too (`--default-character-set=utf8mb4`), otherwise the
`'%Ã%'` literal in the `LIKE` pattern itself gets mangled on the way in and won't match anything.
