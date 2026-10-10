# Maintenance notices — announcing planned downtime, optionally read-only

> **Implemented 2026-10-10 (SPO-119).** Global admins schedule maintenance windows in the app: German and English
> text, start, end and an optional "show from". Every logged-in user sees a bar on top of the app from "show from"
> until the end; it can be closed before the window starts, not while it runs. A window can be marked **read-only**:
> while it runs, every change is refused with `423 MAINTENANCE_READ_ONLY` — centrally, in one filter — except for
> global admins, who do the maintenance.
>
> See also: [04-authorization-acls-vs-scoped-rbac.md](./04-authorization-acls-vs-scoped-rbac.md) (global admin),
> [13-vps-test-deployment.md](./13-vps-test-deployment.md) (where maintenance happens),
> [28-importer.md](./28-importer.md) (a long import is the typical reason for a read-only window).
>
> Question raised: how do users learn about planned maintenance, and can changes be stopped meanwhile?
> Short answer: **a bar on top of the app, maintained by global admins; optionally read-only for everyone else.**

## Context

- The frontend already has a `/maintenance` page — shown when the backend can't be reached at all (status
  0/502/503/504). Nothing announced a *planned* window beforehand, and while the backend is up nothing stopped users
  from entering results that a restore or a large import would then overwrite.
- Maintenance is done by global admins (imports, deployments, data fixes); they need to keep working during it.

## Options considered

**Where notices are maintained**

| | How | Verdict |
|---|-----|---------|
| **In the app** | global admins create/edit/delete notices under System → Maintenance notices | **Chosen.** No deployment or server access needed to announce a window. |
| Server configuration | a notice set per environment variable / config file | **Rejected** — needs a restart or server access for a text change. |

**When the bar can be closed**

| | How | Verdict |
|---|-----|---------|
| **Until the window starts** | closable while only announced; remembered in the browser per notice version; shown again when the notice changes; not closable while running | **Chosen.** Early notice without nagging, no missing it during the window. |
| Always | closable at any time | **Rejected** — a user closing it early would be surprised by refused changes later. |
| Never | always visible | **Rejected** — a notice weeks ahead would sit on every page. |

**How changes are refused in a read-only window**

| | How | Verdict |
|---|-----|---------|
| **`423 MAINTENANCE_READ_ONLY`** in one filter after authentication | reads stay open; the frontend shows why in the bar | **Chosen.** |
| `503 Service Unavailable` | the usual "down for maintenance" code | **Rejected** — the frontend treats 503 as "backend unreachable" and leaves for `/maintenance`, although reading still works. |
| A check in every write service | each service refuses on its own | **Rejected** — one forgotten endpoint and the window isn't read-only. |

## Decision

- Now: `maintenance_notice` (`message_de`, `message_en`, `starts_at`, `ends_at`, `announce_from`, `read_only`,
  `updated_at`; V22). `announce_from` defaults to the start and must not be after it; the end must be after the
  start.
- Now: `GET /v1/maintenance-notices/current` for every logged-in user: the notice that is announced and not over,
  the soonest if several; `204` when there is none. CRUD under `/v1/admin/maintenance-notices`, global admin only.
- Now: the frontend polls the current notice every 5 minutes and re-evaluates every 30 seconds whether it is
  announced or running. Closing it is stored per notice and `updated_at`, so an edited notice shows again.
- Now: `MaintenanceReadOnlyFilter` (added after `BearerTokenAuthenticationFilter`, not a bean): while a read-only
  window runs, every request other than `GET`/`HEAD`/`OPTIONS` is refused with `423` — except for global admins and
  the notice endpoints themselves, so an admin can end the window early. API keys are read-only anyway.
- Now: the admin dialog takes a date plus an optional time per field; without a time, a start or "show from" means
  0:00 and an end means the end of that day. Whole-day windows are shown with dates only.
- Watch for: a frontend dialog shows its generic error on a `423`; the specific reason is in the bar above. If users
  find that confusing, map `MAINTENANCE_READ_ONLY` in the shared error handling.
