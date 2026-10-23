# Public view — a board of running and upcoming fixtures as everyone's start page

> **Discussion — draft for the meeting 26.10., not yet proposed to the team (SPO-110).** Revised 2026-10-23: the
> first draft (11.10.) planned a separate public app for spectators. Now the **start page of the Sports Hub
> itself** is public: `/` shows a **board of running and upcoming fixtures across all federations**, filterable by
> federation, league and group, for everybody — anonymous visitors and logged-in users alike. One app, one URL.
> Anonymous visitors read through a **key-adding proxy** on the app's own nginx (an allow-list of read-only `GET`s,
> the read-only API key added there and never in the browser, a few seconds of cache); logged-in users read with
> their token as today. The board **remembers** the visitor's view: the filter is part of the URL (bookmarkable,
> shareable, what dtfb.de links to) and the last view is kept on the device. Results show while they are being
> entered, **marked "provisional"**.
>
> See also: [10-api-consumers-and-authz.md](./10-api-consumers-and-authz.md) (§4 no anonymous access,
> §6 list filters), [20-api-keys.md](./20-api-keys.md) (read-only keys),
> [17-result-entry-confirmation.md](./17-result-entry-confirmation.md) (provisional vs. final results),
> [22-fixture-modes.md](./22-fixture-modes.md) (Race to 42), [06-frontend-team-portal-split.md](./06-frontend-team-portal-split.md)
> (why the team surface stays in the one app).
>
> Question raised: where do spectators follow the Regionalliga, what do they see first, and does it show results
> before both captains have confirmed them?
> Short answer: **in the Sports Hub app itself — its start page is a live board for everyone; anonymous reads go
> through a key-adding proxy; provisional results shown, clearly marked.**

## Context

- M1 (20./21.03.2027): the Damen-Regionalliga is played through the Sports Hub; spectators follow it via a
  link from dtfb.de where the "Live" button of a league sits today (SPO-59). There is no page to link to.
- No anonymous reads (decided 26.09., doc 10 §4); API keys are read-only and meant server-to-server — a
  key in browser JavaScript is public. So *something* must read server-side with a key for anonymous visitors.
- Today the app requires a login for everything: `/` jumps to the last used area (`defaultAreaRedirectGuard`), a
  user without any role is stopped at `/no-access`, and the whole shell sits behind `anyAreaGuard`. Keycloak itself
  runs `check-sso` (`app.config.ts`), which does not force a login.
- Wanted (2026-10-23): one overview that everybody sees first — what is being played right now and what comes
  next, over all federations — narrowed down to a federation, league or group, and remembered when the visitor
  comes back.
- The backend has most of it: `GET /v1/leagues?federationId`, `/v1/groups?leagueId`, `/v1/matchdays` (with scores,
  `from`/`to`, `resultState`), `/v1/matches?matchDayId`, `/v1/groups/{id}/standings?provisional=true`. But every
  fixture list needs a scope (`leagueId`, `groupId`, … — else `400 FILTER_REQUIRED`, doc 10 §6), and a fixture row
  carries team names but no league, group or venue name.
- Load: a match weekend means many spectators reloading the same few lists every minute (SPO-90).
- Who could change dtfb.de is open (SPO-59 has no owner with access).

## Options considered

**Where the page runs**

| | How | Verdict |
|---|-----|---------|
| (a) **Public routes in the Sports Hub app** | `/` (the board) and the group and fixture pages need no login; admin areas stay behind login and are lazy-loaded, so anonymous visitors never download them | **Chosen.** The board is everyone's start page, so it belongs in the one app: one URL, one deployment, the existing schedule/table components (`group-standings`) reused, a visitor who logs in stays on the same page. |
| (b) Separate public app (the first draft) | a small Angular app of its own on its own host, sharing components | **Not now.** Two deployments and two URLs for the same pages, and logged-in users would see the board somewhere else. Revisit if the public bundle must be much smaller or server-rendered for search engines. |
| (c) dtfb.de renders the data itself | the dtfb.de server reads with its own key | **Not for M1** — needs someone with access to dtfb.de (SPO-59). Stays possible at any time: just another key holder. dtfb.de links our deep pages meanwhile. |
| `permitAll()` GETs in the backend | anonymous reads | **Rejected** — reverses doc 10 §4. The proxy's allow-list is the same idea, but outside the backend and limited to exactly what the pages need. |

**How anonymous visitors read**

| | Verdict |
|---|---------|
| **Key-adding proxy on the app's nginx** (`/public-api/…`) | **Chosen** — allow-list of `GET`s, `X-API-Key` added from the container env, `proxy_cache` 15–30 s per URL: the crowd of a match day costs the backend one request per list and interval. |
| Server-side rendering (Node holds the key) | **Not now** — a Node server to run and secure for the same protection of the key. |

**Provisional or only final results**

| | Verdict |
|---|---------|
| Only final (both captains confirmed / admin) | **Rejected** — confirmation often comes at the end of the day, so the board would not be live. |
| **Provisional too, clearly marked** | **Chosen** — scores as entered, a "vorläufig" badge until final; the table has an official/live switch (default: live during a running match day). Corrections appear as they are made. |

**Remembering the visitor's view**

| | Verdict |
|---|---------|
| **URL + device** | **Chosen** — the filter is in the URL (bookmarkable, shareable, dtfb.de links); the last view is kept in the browser and restored on the plain start page. No cookies, no account, no server state. |
| Per account | **Later** — for logged-in users it can move into the per-account settings (SPO-123); anonymous visitors have no account. |

## Decision

**Start page: the board** (mobile first, WCAG AA, like everything else)
- Now: `/` is the board for everyone. **Running** = kick-off passed and not final (including results being entered,
  `resultState` `SUBMITTED`); **upcoming** = the next 7 days. Grouped by league · group, sorted by kick-off. A row:
  kick-off, league · group, pairing, score with a "live"/"vorläufig" badge, venue; bye fixtures marked.
- Now: tap a fixture → fixture detail; tap a league/group → its schedule and table.
- Now: filters federation, league, group — chips above the list, one "reset". Default: all federations.
- Now: refreshes every 30 s while a fixture is running (matches the proxy cache).
- Now: anonymous visitors get a slim header (language switch, "Anmelden"); logged-in users the normal shell, with
  the board as its first nav entry ("Übersicht"). `/` shows the board instead of jumping to the last used area; the
  areas stay one click away.
- Now: a logged-in user **without any role** sees the board instead of being stopped at `/no-access`; `/no-access`
  stays for direct links into admin areas.
- Now: the public pages keep working when Keycloak is unreachable — they never route to `/auth-unavailable`; only
  the login button and the admin areas depend on it.

**Public pages** (no login)
- Now: the board; a group's schedule (date, time, venue, pairing, score, "vorläufig" until final) and table (live
  and official, withdrawn teams as for logged-in users); a fixture's detail (the single games with player names and
  scores — what the SM live view shows today).
- Now: no personal data beyond names: no player numbers, birth years, nationality, licence (SPO-79 keeps the key
  itself narrow too).

**Data path**
- Now: anonymous `GET`s go to `/public-api/…` on the app's own nginx, which forwards **only an allow-list** to the
  backend and adds `X-API-Key` (a dedicated read-only key for the public pages, from the container env); everything
  else → `404`. Allow-list: `/v1/federations`, `/v1/seasons`, `/v1/leagues`, `/v1/tiers`, `/v1/groups`,
  `/v1/matchdays`, `/v1/matches`, `/v1/groups/{id}/standings`, `/v1/locations` — with the scopes doc 10 §6 requires.
- Now: logged-in users read straight from the backend with their token, uncached, as today.
- Now: one HTTP interceptor picks the path by login state; the generated `@dtfb/api` client stays unchanged.

**Backend changes** (the board is the first view not scoped to one league or group)
- Now: on `GET /v1/matchdays` a **bounded date window counts as a scope** — `from` and `to` both set, at most 14
  days apart — and a new `federationId` filter (the league's season's federation). Without a scope or window it stays
  `400 FILTER_REQUIRED`.
- Now: `MatchDayDto` gains read-only `leagueName`, `groupName` and `locationName` (like `teamHomeName`), so a board
  row needs no further requests.

**Remember where the visitor left off**
- Now: the board's filter lives in the URL (`/?federation=…&league=…&group=…`), the deep pages have their own paths
  (`/groups/<id>`, `/fixtures/<id>`) — bookmarkable, shareable, what dtfb.de links to.
- Now: the last board URL is kept in `localStorage`; opening the plain `/` restores it, with a visible "Your last
  view · Show all" link. An explicit URL always wins. No storage (private mode) → simply no restore.
- Now: the same for logged-in users, per device. Later: per account (SPO-123).

**Which leagues are public**
- Now: every league of a non-archived season is visible. Watch for: hobby/pub leagues that don't want to be public
  (agenda "Hobbyligen") → then a `public` flag on the league.

**Hosting**
- Now: no second host — the app's host serves everybody (test: `sh-admin-test.dtfb.de`; final name with SPO-86);
  dtfb.de links its deep pages. The proxy and the key's env go into the frontend container.

**Effort (rough)**
- Backend window scope + names: ~1 day. Public routes, guard split, interceptor, proxy config: ~2 days. Board,
  filters, remember: ~3 days. Group and fixture pages reusing the existing components: ~2 days. A key and its env on
  the VPS: minutes.

## Open questions

- **Provisional results public** — confirm the proposal (agenda item since 28.09.).
- **Board window** — 7 days ahead? Do today's finished fixtures stay on the board until midnight?
- **Start page for admins** — the board always, or keep jumping to the last used area for users with roles?
- **Local storage notice** — does remembering the filter on the device need a notice (TTDSG §25)? Likely "strictly
  necessary" for the function the visitor asked for; the data protection officer (Eddie) should confirm.
- **Host name** — the app's final name (SPO-86); a host called "admin" no longer fits a public start page. Who sets
  up DNS?
- **Who builds the dtfb.de link** (SPO-59, owner open) — a plain link per league, or an embedded `<iframe>`?
- **Player names in fixture details** — the SM shows them publicly today; keep that, or teams and scores only?
- **Repo**: the app moves into the separate Sports Hub frontend repo Helmut is extracting (SPO-55) — public pages
  included.
