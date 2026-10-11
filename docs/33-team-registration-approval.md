# Team registration with approval — one path for every league

> **Discussion — draft for the meeting 12.10., not yet proposed to the team (SPO-51).** Registering a team for a league becomes a request that
> is **approved** before the team counts: the captain (or the club admin) registers the team with its
> roster, the **club admin** confirms a captain's request, and the **federation admin** of the league's
> federation approves or rejects it — the three stages of doc 25 (B-2026-09-28-7), for every league
> including the Bundesliga (B-2026-09-14-7). Until approval the team appears nowhere but in the request
> lists: not on the placement board, not in schedules, tables or the public view. Approving the
> registration also confirms the roster submitted with it; later roster changes keep the existing
> roster path. Today a registration is `ACTIVE` immediately.
>
> See also: [25-player-data-change-approval.md](./25-player-data-change-approval.md) (the three stages),
> [01-competition-and-registration-model.md](./01-competition-and-registration-model.md) (participation and
> roster lifecycle; §7 L3 and §8 deferred exactly this), [11-placement-board.md](./11-placement-board.md),
> [16-root-federation.md](./16-root-federation.md) (Bundesliga = root leagues), [19-category-eligibility.md](./19-category-eligibility.md).
>
> Question raised: how does a team get into a league, and who says yes?
> Short answer: **a registration request, confirmed by the club, approved by the federation; only then is the team in the league.**

## Context

- Decisions: one registration and approval process for all leagues, no DTFB special path
  (B-2026-09-14-7); stays in M1 (B-2026-09-21-16); captain proposes → club admin confirms →
  federation admin approves, explicitly not the league admin (B-2026-09-28-7). Qualification (who may
  play at all) is **not** modelled — only create, fill and approve.
- Today: only clubs create teams (`canCreateTeam`); `POST /v1/team-participations` (league admin or team
  representative) creates a participation that is **`ACTIVE` at once**, unplaced; the roster runs
  `DRAFT → SUBMITTED → CONFIRMED`, confirmed by the team's admins above or a league admin, listed under
  "Confirm rosters" (`GET /v1/team-participations/pending`). Windows: `Season.registrationOpensAt/ClosesAt`.
- `ParticipationStatus` knows only `ACTIVE`/`WITHDRAWN` — no "requested, awaiting approval".
- It replaces the licence application of today (form → PDF → Nicole checks) in the long run.
- The practitioners' view (Helmut knows the Hamburg/Berlin process, back ~25.10.) is still to be gathered;
  this doc fixes the structure so the details can follow.

## Options considered

| | How | Verdict |
|---|-----|---------|
| Keep `ACTIVE` at once, approve only the roster | the existing roster confirmation is the approval | **Rejected** — the team already sits in the league (placement board, schedule) before anyone said yes; B-2026-09-28-7 says it appears in the data only after approval. |
| Separate `RegistrationRequest` entity | a request row that creates the participation on approval | **Rejected for now** — duplicates team/league/roster; the participation already is "this team in this league", it only lacks the states. |
| **States on the participation** | `ParticipationStatus` gains request states; the roster rides along | **Chosen.** One row from request to withdrawal; existing filters and lists keep working by status. |
| Generic change-request engine (also for doc 25's renames) | one workflow table for every proposal | **Not now** — doc 25's renames are a different shape (field changes on a player). Share the *surface* (one inbox), not the table. Revisit when doc 25 is built. |

## Decision

**States**
- Now: `ParticipationStatus` = `REQUESTED` (by a captain, waiting for the club) → `CLUB_CONFIRMED` (waiting
  for the federation) → `ACTIVE`; plus `REJECTED` and `WITHDRAWN` as today.
- Now: who starts where — a **captain** (`TEAM_ADMIN`) creates `REQUESTED`; a **club admin** creates
  `CLUB_CONFIRMED` directly (their own step is done); a **federation admin of the league's federation** or a
  global admin creates `ACTIVE` directly (admins stay open, they just skip their own approval). A team
  **without a club** (hobby teams, agenda "Hobbyligen") skips the club stage.
- Now: only `ACTIVE` participations are placeable, scheduled, counted in tables, visible in the public view
  (doc 32) and in `GET /v1/team-participations` by default; the request lists read the other states.

**Roster with the registration**
- Now: a registration is submitted **with its roster** (`rosterStatus` `SUBMITTED`); the roster is edited in
  `DRAFT` before that, by the captain or club admin, inside the registration window.
- Now: **approving the registration confirms the roster** (`CONFIRMED`) in the same step — one decision for
  the federation, like the licence check today. Roster changes after that keep the existing path
  (reopen → edit → submit → confirm).
- Now: eligibility (doc 19) and club membership (doc 15) are checked at submit, as today.

**Who decides**
- Now: club stage — the club admin of the team's club. Federation stage — the **federation admin of the
  league's federation** (root leagues such as the Bundesliga: the root federation's admins), and global
  admins. **League admins** see the requests of their leagues read-only — see open questions.
- Now: **reject with a reason** (text, shown to the team). A rejected request can be corrected and submitted
  again (same row back to `REQUESTED`/`CLUB_CONFIRMED`) while the window is open.
- Now: a team that **withdrew** in the same season registers again by a new request on the **same row**
  (answers the open point from 27.07.).

**Surface**
- Now: the "Confirm rosters" page becomes **"Registrations & rosters"** — one inbox for the federation
  admin with two kinds: new registrations (team + roster, approve/reject) and roster changes (as today).
  Club admins get the same list for their stage (requests of their club's teams). Counters in the
  navigation; no e-mails (there is no mail infrastructure yet).
- Now: the team area shows the request's state and a rejection's reason.

**Effort (rough)**
- Backend: states + transitions + authz + filters, migration for the enum values: ~3 days. Frontend:
  register dialog → request, inbox for club and federation, state in the team area: ~4 days. Fits the
  December slot of the M1 plan.

## Watch for / triggers to revisit

- **Federation admins as bottleneck** (doc 25's concern): the Damen-Regionalliga is decided centrally by the
  DTFB — eight requests, fine. With every state league on this path the inbox grows; a deadline reminder or
  delegation to league admins may be needed.
- **Licence check needs a document** (open in SPO-51: PDF for the reviewer) — a printable view of the request
  is cheap; add when asked.
- **Registration fees / payment** — out of scope; watch if the federation wants it tied to approval.

## Open questions

- **League admins**: approve too (as they confirm rosters today), or read-only as B-2026-09-28-7 says? Today's
  roster confirmation by league admins would then also move to the federation admin.
- **Damen-Regionalliga in M1**: who is the deciding federation admin — the DTFB (root) with Nicole? (Agenda:
  "wer entscheidet über die Teilnahme".)
- **Club stage mandatory?** If the captain is also the club admin (common in small clubs), the club stage is
  skipped automatically — fine? And is a club stage wanted at all for the Bundesliga?
- **Practitioners' requirements** (Helmut, B-2026-09-21-16): deadlines, required documents, what Nicole checks
  today.
