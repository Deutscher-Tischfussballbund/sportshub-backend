# Result entry & confirmation — who may record a match result, and when it becomes final

> **Decision, 2026-09-21. Backend implemented 2026-09-28 (SPO-15, §Implementation); frontend pending (SPO-57).** A match result is recorded by *either* team and
> becomes official only once the *opposing* team confirms it. Any team member of either side may
> enter or edit; each edit re-opens confirmation for the other side; a two-sided confirmation
> **freezes** the result, after which only a league/federation admin may change it. An admin may
> enter, edit, or confirm at any point in the cycle. The model is lifted from how tournaments
> already run today, and applies to league fixtures as well as `Race to 42`.
>
> **Refined 2026-09-28 (proposal by Marvin, awaiting team confirmation — agenda, SPO-15/57):**
> entering stays open to every member of either team, but **confirming is the captains' job**
> (the `team_admin` role). A result is final once each team's captain has agreed to the current
> version. See §Decision and §Changes against 2026-09-21.
>
> See also: [`02-role-concept.md`](./02-role-concept.md) (roles and scopes),
> [`03-authorization-model.md`](./03-authorization-model.md) (how a decision is computed),
> [`12-matchday-scheduling.md`](./12-matchday-scheduling.md) (where fixtures come from),
> [`06-frontend-team-portal-split.md`](./06-frontend-team-portal-split.md) (the captain-facing surface).
>
> Question raised: who enters a match result — the team captains or the on-site tournament
> management — and at what point is the result binding?
> Short answer: **both teams; binding on mutual confirmation; admin override always available.**

## Context

No frontend lets a captain record anything. The backend has an older two-step flow that falls short
of this decision: `POST /v1/matchdays/{id}/result` (sets the `Match` scores, `resultState =
HOME_SUBMITTED`, since renamed `SUBMITTED`) and `POST /v1/matchdays/{id}/confirm` (`CONFIRMED`, confirmer ≠ submitter), both
gated by `canReportMatchDay` (a team's `team_admin` or an admin above it). It has no edit/re-open
cycle (submit requires `OPEN`), no league-admin access and no notification. *(Update 2026-09-28,
SPO-71: generated fixtures now carry their games from the rule set's game plan, doc 12 §5; before,
there was nothing to score on them.)*
*(Correction 2026-09-26, SPO-103: the first version of this doc said no backend existed.)* The first
production use of the Sports Hub is a Regionalliga weekend, where results have to be captured as the
weekend runs, not afterwards.

The legacy process is paper: both teams write the result on a sheet, both sign it, and the sheet
goes to the on-site tournament management, who transcribes it. Reproducing that digitally would
mean a single privileged transcriber and a dispute process outside the system. The alternative —
letting one team enter a result unilaterally — makes the result unverifiable, since the other side
has no recorded assent.

The tournament side of the federation already runs a working answer to this, and it was adopted
wholesale rather than designed afresh.

## Options considered

| | How | Verdict |
|---|-----|---------|
| **Mutual confirmation (chosen)** | Either team enters; opponent is notified and confirms or edits; an edit restarts the cycle; mutual confirmation freezes the result | **Chosen.** Both sides' assent is recorded, no privileged transcriber, and it already works in tournaments |
| Tournament-management entry only | A designated on-site role transcribes results, as on paper today | Rejected — recreates the paper bottleneck digitally and needs a dedicated role that does not otherwise exist |
| Unilateral entry by the home/first team | One side enters, no confirmation step | Rejected — no recorded assent from the opponent, so disputes have nothing to resolve against |
| Dedicated tournament-admin role for overrides | A new role scoped to a running event | Rejected — a league-scoped admin role already exists and can be assigned per league, so no new role is needed |

## Decision

*(Refined 2026-09-28 — proposal, see the note at the top.)*

**Who counts as what, for one fixture:**

- **Team member** of a side: anyone on that team's current roster in the fixture's league (added
  and not removed — `RosterEntry` → `Player` → `Player.user`), or a **captain** of it.
- **Captain** of a side: a holder of the `team_admin` role for that team (what the club page calls
  "appoint captain"; a team may have several). Compared by team identity, so it holds across seasons.
- **Neutral admin:** a league admin of the fixture's league, an admin of its federation, or a global
  admin. A **club admin** is not neutral — for a fixture of their own club's team they act as that
  team's side, like a team member.
- A person who belongs to **both** sides (e.g. on both rosters) may not act on that fixture as a team
  member — their side is ambiguous. Only people with a login can act at all; a rostered player
  without an account simply doesn't take part.

**Rules:**

- **Who may enter or edit:** any team member of either side, and any neutral admin.
- **Who may confirm:** only a **captain**, for **their own side**, as long as that side hasn't agreed
  to the current version yet — or a neutral admin. A team member who isn't a captain can't confirm.
  Since a captain's own entry already counts as their side's agreement, nobody ends up confirming
  their own entry.
- **Only a decided fixture can become final** (Marvin, 2026-09-28): the entered games must decide it
  under the rule set's **matchday decision** — `ALL_GAMES` (also when none is set): every game has a
  score; `FIRST_TO` N: one side has won N games, the rest may stay unplayed. Before that, captains
  can agree to what's entered, but no confirmation and no admin entry makes it final.
- **When it is final:** once it is decided and **each side's captain has agreed to the current version**. A captain
  agrees by confirming it, or by entering/editing it themselves. So:

  | Who enters | Then needed |
  |---|---|
  | a captain of A | a captain of B confirms |
  | a team member of A who isn't a captain | a captain of A **and** a captain of B confirm |
  | a neutral admin | nothing — final at once (override) |

- **Edits restart the cycle:** any edit by a team member cancels the agreement of the other side
  (a captain's own edit counts as their agreement); the side that now has to act is notified.
- **Freeze:** once final, the result is immutable for team members. Only a neutral admin may change
  it thereafter; an admin change is final at once.
- **Admin authority:** a neutral admin may enter, edit, or confirm a result at any point,
  including confirming on behalf of a stalled cycle. This is unconditional, not a fallback.
- **When captains confirm** (Marvin, 2026-09-28): any time within the match day, typically before
  their team's next match — not necessarily right after the game. There is no technical deadline;
  the result screen should put a captain's open confirmations up front (SPO-57), so they are done
  between matches.
- **Notification:** each transition notifies the side that now has to act (push/message channel
  not yet chosen).
- **Scope:** applies to league fixtures and to `Race to 42` alike — there is no reason to diverge.
- **Not in scope for the first release:** line-ups, substitutions, and who was swapped for whom.
  Those stay on paper for now; only the final result of a fixture is captured digitally. The
  `entity_history` groundwork in [`14-team-player-versioning.md`](./14-team-player-versioning.md)
  means the data model does not block adding them later.

### Changes against 2026-09-21

- Confirming moves from "the opposing team" to **the captains**; entering stays open to every team
  member. This mirrors the paper sheet, where both captains sign.
- "Final" is defined per side (each captain agreed to the current version), so an entry by a
  non-captain no longer counts as its team's word.
- Club admins count as their team's side, not as neutral admins — otherwise a club admin could enter
  a result for their own team and confirm it as "admin".
- A neutral admin's entry or edit is final at once (2026-09-21 only said an admin may enter, edit or
  confirm at any point).

**Consequence for M1:** every team needs at least one captain with a login before the weekend
(SPO-111). If a captain doesn't respond, the result waits for a neutral admin (see "Stalled cycles").

## Implementation (backend, 2026-09-28)

- **States** (`MatchDay.resultState`): `OPEN` → `SUBMITTED` → `CONFIRMED`. The old `HOME_SUBMITTED`
  became `SUBMITTED` (either side can enter); Flyway `V15` renames existing rows and turns the
  column into a plain `varchar`. Each side's agreement is `homeConfirmedAt` / `awayConfirmedAt`
  (null = not agreed to the current version), the last editor `submittedByDtfbId`.
- **Endpoints** (`MatchDayController`, logic in `MatchDayResultService`):
  - `GET /v1/matchdays/{id}/result` — games with scores, both agreements, and for the current user
    `canEdit`, `canConfirm`, `neutralAdmin`, `side`, so the result screen doesn't re-derive the rules;
  - `POST /v1/matchdays/{id}/result` — enter or edit (`{ matches: [{ matchId, homeScore, awayScore }] }`),
    gated by `@authz.canEnterResult`;
  - `POST /v1/matchdays/{id}/confirm` — gated by `@authz.canConfirmResult`.
  Both return the same result view. Refusals: `403` (not allowed / side ambiguous), `409` (final
  result edited by a team, nothing to confirm, own side already agreed), `400` (malformed scores).
- **Who is who** — `AuthorizationService.resultActor`: neutral admin = global admin, region admin of
  the league's federation, or league admin of the league (the same set that organizes the league);
  team member = captain, admin above the team, or `RosterEntryRepository.isOnActiveRoster` (a
  `Player` linked to the login on the team's current roster in that league).
- **Scores** are checked for structure: the games belong to the fixture, none twice, scores present
  and ≥ 0; a game gets `PLAYED` and its `winner`. **Decided** (`MatchDayResultService.isDecided`,
  `ALL_GAMES` / `FIRST_TO` as above) gates every finalization: a confirmation that would finalize an
  undecided result is `409`, and a neutral admin's entry of an undecided result stays `SUBMITTED`.
  The result view carries `gamesEntered`, `gamesTotal` and `decided`; `canConfirm` is false where a
  confirmation would finalize an undecided result. Validity of a single game's score (sets per game,
  points per set, draws in a game) still waits for the Regionalliga format (SPO-58).
- **Standings** are recomputed for the whole group from its `CONFIRMED` fixtures on every
  finalization (`StandingService.recompute`), so an admin correction replaces the old result
  instead of counting twice (SPO-73).
- **Live table** — `GET /v1/groups/{id}/standings?provisional=true` also counts entered but not yet
  confirmed fixtures (`SUBMITTED`) with the games scored so far, computed on request and never
  stored; rows with such a fixture carry `provisional: true`. Since entry accepts some of a
  fixture's games, a team can enter each game as soon as it is played and the live table moves
  with it; the captains confirm at the end. Without the parameter the endpoint returns the official
  table (final results only), as before. A goal-by-goal ticker is a separate, later topic (SPO-116).
  Whether the public display shows provisional results is decided with SPO-110 (proposal: yes,
  clearly marked).
- Tested end-to-end in `MatchDayResultAuthorizationIntegrationTest` (17 cases).
- **Not yet:** notifications (see Open questions), the frontend (SPO-57).

## What did NOT need to change

- **No new role.** League-scoped admin assignment already exists and is assignable per league
  (used today for e.g. a Lübeck league admin who is not a regional admin). The override authority
  in this doc is that existing role, not a new one — see [`02-role-concept.md`](./02-role-concept.md).

## Open questions

- **Notification channel.** "Push notification" was the word used, but the system has no push
  infrastructure; email or in-app notification may be the first implementation.
- **Stalled cycles.** Confirming is expected within the match day (see Decision). Nothing forces a
  conclusion after that: the result stays `SUBMITTED` (it counts in the live table, not the
  official one) until a neutral admin confirms or corrects it. Proposal: no auto-confirm, and the
  league admin gets a list of results still open after the day. Not built yet.
- **Line-ups on paper.** Revisit once a real weekend has been played: if the paper sheet turns out
  to be the thing people actually want digital, this is where the decision reopens.
- **Rule set dependency.** The scoring rules of the Regionalliga (`Race to 42` or otherwise) are
  not yet known and are being clarified with the competition management; they affect what a
  "result" consists of, not who may record it.

---

Source: DTFB Sports Hub team meeting 2026-09-21, decision `B-2026-09-21-10` (line-ups on paper:
`B-2026-09-21-11`; no tournament-admin role: `B-2026-09-21-12`). Minutes in the
`dtfb-projektmanager` repo under `meetings/2026-09-21/protocol.md`. Refinement 2026-09-28: Marvin,
on the agenda for team confirmation (`meetings/backlog.md`).
