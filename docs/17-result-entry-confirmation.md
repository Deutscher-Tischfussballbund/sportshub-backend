# Result entry & confirmation — who may record a match result, and when it becomes final

> **Decision, 2026-09-21. Not yet implemented.** A match result is recorded by *either* team and
> becomes official only once the *opposing* team confirms it. Any team member of either side may
> enter or edit; each edit re-opens confirmation for the other side; a two-sided confirmation
> **freezes** the result, after which only a league/federation admin may change it. An admin may
> enter, edit, or confirm at any point in the cycle. The model is lifted from how tournaments
> already run today, and applies to league fixtures as well as `Race to 42`.
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
HOME_SUBMITTED`) and `POST /v1/matchdays/{id}/confirm` (`CONFIRMED`, confirmer ≠ submitter), both
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

- **Who may enter:** any member of either team in the fixture. Not only the captain.
- **Lifecycle:** `entered → awaiting confirmation (by the opposing team) → confirmed`. An edit by
  the opposing side returns the result to `awaiting confirmation`, with the notification now
  pointing at the other team. The cycle repeats until both sides have confirmed.
- **Freeze:** once confirmed by both sides, the result is immutable for team members. Only a
  league admin or federation admin may change it thereafter.
- **Admin authority:** a league/federation admin may enter, edit, or confirm a result at any point,
  including confirming on behalf of a stalled cycle. This is unconditional, not a fallback.
- **Notification:** each transition notifies the side that now has to act (push/message channel
  not yet chosen).
- **Scope:** applies to league fixtures and to `Race to 42` alike — there is no reason to diverge.
- **Not in scope for the first release:** line-ups, substitutions, and who was swapped for whom.
  Those stay on paper for now; only the final result of a fixture is captured digitally. The
  `entity_history` groundwork in [`14-team-player-versioning.md`](./14-team-player-versioning.md)
  means the data model does not block adding them later.

## What did NOT need to change

- **No new role.** League-scoped admin assignment already exists and is assignable per league
  (used today for e.g. a Lübeck league admin who is not a regional admin). The override authority
  in this doc is that existing role, not a new one — see [`02-role-concept.md`](./02-role-concept.md).

## Open questions

- **Notification channel.** "Push notification" was the word used, but the system has no push
  infrastructure; email or in-app notification may be the first implementation.
- **Stalled cycles.** Nothing currently forces a conclusion if the opponent never responds. Does a
  result auto-confirm after a deadline, or does it simply wait for an admin? Undecided.
- **Line-ups on paper.** Revisit once a real weekend has been played: if the paper sheet turns out
  to be the thing people actually want digital, this is where the decision reopens.
- **Rule set dependency.** The scoring rules of the Regionalliga (`Race to 42` or otherwise) are
  not yet known and are being clarified with the competition management; they affect what a
  "result" consists of, not who may record it.

---

Source: DTFB Sports Hub team meeting 2026-09-21, decision `B-2026-09-21-10` (line-ups on paper:
`B-2026-09-21-11`; no tournament-admin role: `B-2026-09-21-12`). Minutes in the
`dtfb-projektmanager` repo under `meetings/2026-09-21/protocol.md`.
