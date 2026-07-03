# Frontend topology: team portal vs. admin app — decision record

> Status: **decision / discussion** (no code change). Records *where the team-facing roster &
> results surface lives*. Companion to
> [`01-competition-and-registration-model.md`](./01-competition-and-registration-model.md) (the roster
> lifecycle this surface drives) and [`03-authorization-model.md`](./03-authorization-model.md) (the
> tier model that makes the boundary non-obvious).
>
> Question raised: *should team editing (roster + result reporting) be split into its own frontend,
> separate from the current admin app (region / club / general admin)?*
> Short answer: **not yet — build it in-app now, along a clean seam, and split only when audience
> reach forces it.**

## 1. Context

The admin app (`dtfb-frontend-ng`, `projects/admin`) serves the governance tiers
**ADMIN > REGION_ADMIN > CLUB_ADMIN > TEAM_ADMIN**. The next feature is the **team roster editor**
(and, later, match-result reporting) — the surface a **team admin** uses.

Team admins are a different audience from the rest: club volunteers, high headcount, low frequency,
low tech literacy. They log in to do two things — fix their roster, report a result. That prompted
the question of a dedicated **team portal** (`teams.dtfb.de`) separate from the admin backend
(`admin.dtfb.de`).

**Stated driver: audience reach** — getting a large, non-technical club-volunteer population onto a
simple, focused surface.

## 2. What "split" could mean (a spectrum, not a binary)

| | Shape | Verdict |
|---|---|---|
| **(a)** Feature area — `team`-scoped route group + thin layout **inside** the current app | in-app | **chosen for now** |
| **(b)** Second workspace app — new `projects/portal`, shares `@dtfb/api` + `@dtfb/components`, own build / URL / Keycloak client | separate app, one monorepo | the real future option |
| **(c)** Separate repo — independent lifecycle | fully separate | rejected |

(c) is rejected: it would fork or force-publish `@dtfb/api`/`@dtfb/components` and duplicate the
a11y / i18n / API-regen tooling. Because the workspace path aliases point at library **source**, a
second *workspace* app (b) consumes the shared libs with **zero extra build** — so if a split ever
happens, it is (b), never (c).

## 3. The case for splitting (b) — why the instinct is sound

- **Different audience, different job.** Bundling a heavy governance app onto club volunteers means
  they wade through nav that is 90 % irrelevant and forbidden.
- **Focused UX.** A dedicated portal needs no elaborate role-gated nav-hiding — the whole app *is*
  the two tasks. Simpler mental model, smaller surface to keep accessible.
- **Blast radius / exposure.** The admin app stays locked-down, small-audience; the widely-exposed
  portal is a separate, minimal support/attack surface.
- **Independent cadence + branding.** Governance features churn separately from participant-facing
  ones; distinct URLs read clearly.

## 4. The decisive objection — the split line bisects a two-actor workflow

Roster and results are **not** team-admin-exclusive surfaces. They are **two-actor workflows that
cross the tier boundary** (see the authz helpers):

- **Roster lifecycle:** `DRAFT → SUBMITTED` (team admin edits + submits, `canEditRoster` =
  team_admin *or* admin) **→ CONFIRMED** (`canConfirmRoster` = an admin *above* the team, explicitly
  **not** the submitter; + reopen).
- **Match results:** team reports (`canReportMatchDay` = represents a participating team) → an admin
  above the team confirms.

A "team vs. admin" frontend split therefore cuts **through the middle of one workflow**, not between
two independent products. It also mis-draws the line, because **region/club admins legitimately edit
rosters/results within their scope** — the capability spans the hierarchy.

Add the ordinary duplication cost of a second app (app shell, `app.config` Keycloak + bearer
interceptor, area guards, theme bootstrap, i18n setup, a11y-harness scenarios — all kept in sync
against one regenerated `@dtfb/api`), on top of an **unfinished migration**, and doing it *now* is
premature.

## 5. The reconciling design (for when the split does happen)

There **is** a clean seam, and naming it dissolves the §4 objection: **the portal owns editing +
submission; the admin app gets an approvals inbox.** Team admins never touch confirm; admins get a
queue of `SUBMITTED` rosters/results to **confirm or reopen**. That splits the workflow at the
tier handoff (SUBMITTED) instead of down its middle. It is *more* work than a shared editor, so it
only pays off once audience volume justifies two apps.

## 6. Decision

- **Now:** build the roster editor (and result reporting) as a **self-contained feature area inside
  the current admin app** (option a). No second app yet.
- **Build it extraction-ready** so a later move to (b) is a mechanical lift, not a rewrite:
  - Put it under a `team`/`participation` route group with its own **thin layout**, gated so team
    admins land straight in it.
  - Keep all logic in components + a `RosterService` that consume **only** `@dtfb/api` /
    `@dtfb/components` — **no reach-back into admin-only shell internals**. That boundary is exactly
    the cut line for a future `projects/portal`.
  - Model **confirm/reopen as its own admin-facing surface from day one** (even in the same app), so
    the eventual approvals-inbox is already conceptually separated from editing.

## 7. Watch for — the triggers that flip this to (b)

Split into a dedicated portal when **audience reach actually demands it**, concretely:

1. **Migration is done** — no second app while the sportshub migration is unfinished.
2. **Real onboarding of the club-volunteer population** — distinct URL/branding, self-service
   sign-in flows, or support concerns that the bundled admin app makes worse.
3. **Divergent release cadence / exposure** — governance vs. participant surfaces need to ship or be
   locked down independently.

When triggered, extract along the §6 boundary and split the workflow at SUBMITTED per §5 — decided
with a working editor in hand, not on spec.

## 8. Open input needed

If the split is revisited, capture here **what changed** (audience numbers onboarded, a support/UX
pain, a branding/deploy requirement) — that evidence, not aesthetics, is what should trigger (b).
