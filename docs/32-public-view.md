# Public view — fixtures, results and tables for spectators

> **Discussion — draft for the meeting 12.10., not yet proposed to the team (SPO-110).** Spectators need a page without login: the M1 link
> from dtfb.de (SPO-59) has nothing to point to yet. Proposal: a **small public frontend of the Sports
> Hub** (its own Angular project in this workspace, no Keycloak) on its own host, e.g.
> `live.dtfb.de`. Its nginx forwards an **allow-list of read-only `GET`s** to the backend and adds a
> read-only API key on the way — the key never reaches the browser, and anonymous reads stay closed
> (doc 10 §4). nginx caches the answers for a few seconds, which absorbs the reload peak on a match
> day. dtfb.de only links to it (option c: our page now, dtfb.de rendering itself later if ever).
> Results show while they are being entered, **marked "provisional"**.
>
> See also: [10-api-consumers-and-authz.md](./10-api-consumers-and-authz.md) (§4 no anonymous access,
> §6 list filters), [20-api-keys.md](./20-api-keys.md) (read-only keys),
> [17-result-entry-confirmation.md](./17-result-entry-confirmation.md) (provisional vs. final results),
> [22-fixture-modes.md](./22-fixture-modes.md) (Race to 42), [06-frontend-team-portal-split.md](./06-frontend-team-portal-split.md)
> (why the team surface stays in the admin app — a different question).
>
> Question raised: where does the spectators' view of the Regionalliga run, and does it show results
> before both captains have confirmed them?
> Short answer: **our own small public app behind a key-adding proxy; provisional results shown, clearly marked.**

## Context

- M1 (20./21.03.2027): the Damen-Regionalliga is played through the Sports Hub; spectators follow it via a
  link from dtfb.de where the "Live" button of a league sits today (SPO-59). There is no page to link to.
- No anonymous reads (decided 26.09., doc 10 §4); API keys are read-only and meant server-to-server — a
  key in browser JavaScript is public. The admin app always requires a Keycloak login.
- So *something* must read server-side with a key and serve the page.
- Who could change dtfb.de is open (SPO-59 has no owner with access).
- The backend already has what the page needs: `GET /v1/leagues?federationId`, `/v1/groups?leagueId`,
  `/v1/matchdays?groupId` (with scores), `/v1/matches?matchDayId`, and
  `/v1/groups/{id}/standings?provisional=true` (a live table counting entered, unconfirmed results).
- Load: a match weekend means many spectators reloading the same few lists every minute (SPO-90).

## Options considered

**Where the page runs**

| | How | Verdict |
|---|-----|---------|
| (a) **Own public frontend + key-adding proxy** | a separate small Angular app; its nginx forwards allow-listed `GET`s to the backend with `X-API-Key` and caches them | **Chosen for M1.** In our hands, no dependency on dtfb.de, reuses `@dtfb/components` and the i18n setup; the proxy is a few lines of nginx. |
| (a') Own public frontend with server-side rendering | an Angular SSR / Node server holds the key and renders HTML | **Not now.** Better for search engines, but a Node server to run and secure; the proxy gives the same protection of the key. Possible later on the same app. |
| (b) dtfb.de renders the data itself | the dtfb.de server reads with its own key | **Not for M1** — needs someone with access to dtfb.de and its stack (unclear, SPO-59). Stays possible at any time: it is just another key holder. |
| (c) **(a) now, (b) later if wanted** | dtfb.de links (or embeds) our page | **Chosen** — (b) is not excluded, just not on the M1 path. |
| Public routes in the admin app | skip the login for some routes | **Rejected** — the admin app bootstraps Keycloak and loads admin code; a public bundle should be small and separate, and the admin app keeps one audience. |
| `permitAll()` GETs in the backend | anonymous reads | **Rejected** — reverses doc 10 §4. The proxy's allow-list is the same idea, but outside the backend and limited to exactly what the page needs. |

**Provisional or only final results**

| | Verdict |
|---|---------|
| Only final (both captains confirmed / admin) | **Rejected** — confirmation often comes at the end of the day, so the page would not be live. |
| **Provisional too, clearly marked** | **Chosen** — scores as entered, a "vorläufig" badge until final; the table has an official/live switch (default: live during a running match day). Corrections appear as they are made. |

## Decision

**App and hosting**
- Now: a new Angular project `public` in this frontend workspace (beside `admin`), using `@dtfb/components`
  and the same i18n setup (de default, en). No Keycloak, no admin code.
- Now: its own host (proposal: `live.dtfb.de`, test: `sh-live-test.dtfb.de`; final name with SPO-86), its own
  container like the admin frontend.
- Now: its nginx proxies `/api/` to the backend **only for an allow-list of `GET` paths** and sets
  `X-API-Key` (a dedicated read-only key for the public view, from the container env). Everything else → `404`.
  Allow-list for M1: `/v1/seasons`, `/v1/leagues`, `/v1/tiers`, `/v1/groups`, `/v1/matchdays`,
  `/v1/matches`, `/v1/groups/{id}/standings`, `/v1/locations` — with the scopes doc 10 §6 already requires.
- Now: nginx `proxy_cache` with a short lifetime (15–30 s) per URL — the whole crowd of a match day costs the
  backend one request per list and interval.

**Content for M1** (mobile first, WCAG AA, like everything else)
- Federation → season → league/group picker; deep links per group (what dtfb.de links to).
- Schedule: date, time, venue, pairing, score; "vorläufig" until final; bye fixtures marked.
- Table: live (counting provisional results) and official; withdrawn teams as in the admin app.
- Fixture detail: the single games with player names and scores (what the SM live view shows today).
- No personal data beyond names: no player numbers, birth years, nationality, licence (SPO-79 keeps the key
  itself narrow too).

**Which leagues are public**
- Now: every league of a non-archived season is visible on the public page. Watch for: hobby/pub leagues
  that don't want to be public (agenda "Hobbyligen") → then a `public` flag on the league.

**Effort (rough)**
- Frontend app with three views + picker: ~1 week; nginx/container/deploy: ~1 day; a key and its env on the
  VPS: minutes. No backend change needed for M1.

## Open questions

- **Provisional results public** — confirm the proposal (agenda item since 28.09.).
- **Host name** — `live.dtfb.de` or a path under an existing host; who sets up DNS (SPO-86)?
- **Who builds the dtfb.de link** (SPO-59, owner open) — a plain link per league, or an embedded `<iframe>`?
- **Player names in fixture details** — the SM shows them publicly today; keep that, or teams and scores only?
- **Repo**: does the public app go into the separate Sports Hub frontend repo Helmut is extracting (SPO-55)?
