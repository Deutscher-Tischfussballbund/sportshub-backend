# Audit trail — who did what, when, as which role

> **Decided 2026-10-08 (Marvin), not yet built (SPO-118).** Every role acts with its own account so
> that every action can be traced back to a person. Today that only half works: who/when columns on the
> rows (`MatchDay.submittedByDtfbId`, `homeConfirmedAt`/`awayConfirmedAt`, `Lineup.submittedByDtfbId`)
> hold the **current** state and are overwritten by the next action, and `entity_history` covers player
> and club fields only. Decision: an explicit, append-only **`audit_event`** table, written by the
> services in the same transaction as the change; visible to the federation admins of the league's
> federation (and global admins) only; a season's events are deleted **12 months after the season's
> end**. First scope: results, confirmations, admin corrections, line-ups, substitutions (M1).
>
> See also: [17-result-entry-confirmation.md](./17-result-entry-confirmation.md) (the result cycle whose
> steps get recorded), [23-lineups-and-substitutions.md](./23-lineups-and-substitutions.md),
> [14-team-player-versioning.md](./14-team-player-versioning.md) (`entity_history`, point-in-time names),
> [16-root-federation.md](./16-root-federation.md) (each federation keeps its own affairs),
> [03-authorization-model.md](./03-authorization-model.md) (who counts as "above" a league).
>
> Question raised: with one account per role, how do we make every action traceable — a changelog,
> fields on the tables, or log statements?
> Short answer: **an append-only audit table for the trail; table fields stay as display values; logs
> are for operations only.**

## Context

- A result goes through several hands (doc 17): either side enters and edits, both captains confirm,
  any edit cancels all confirmations, an admin corrects. Each save overwrites the game scores and
  `MatchDay.submittedByDtfbId`, and an edit clears `homeConfirmedAt`/`awayConfirmedAt`. After a dispute
  ("we never confirmed 5 : 7") nothing shows who entered which version or who had agreed to what.
- Line-ups (doc 23) keep only `submittedAt`/`submittedByDtfbId` of the latest save; drafts, an admin's
  later edit and removed substitutions leave no trace.
- `entity_history` (`EntityHistoryEntry`: entity type, id, field, old/new value, `changedAt`,
  `changedByDtfbId`) exists for `PLAYER` and `CLUB` only. Its job is reconstructing a value **as of a
  date** (roster names in an ended season), not recording actions.
- Logging is practically absent (a handful of `log.info` calls in startup jobs).

## Options considered

| | How | Verdict |
|---|-----|---------|
| **Log statements** | `log.info("result entered …")` per action | **Rejected as the trail** — rotated away, not in the database backup, not queryable by an admin, not tied to the data. Fine for operations. |
| **Table fields** | more who/when columns on the rows (`enteredBy`, `correctedBy`, …), or Spring Data `@CreatedBy`/`@LastModifiedBy` | **Rejected as the trail** — a row holds one state; the next action overwrites it. Kept as **display values** of the current state. |
| **Hibernate Envers** | automatic revision tables (`*_AUD`) for every audited entity, a revision entity with the user | **Rejected** — records table diffs, not actions ("why did this change?" stays open); every Flyway migration needs its `_AUD` twin; hard to turn into a readable history in the UI; noisy (every flag change is a revision). |
| **Explicit append-only audit table** | one `audit_event` row per business action, written by the service | **Chosen** — records the action and the role it was taken in, survives later edits and deletes, readable as a history, one table and one mechanism. |

## Decision

- **Model**: `audit_event` — `actorDtfbId`; the **role and scope** acted in (e.g. `TEAM_ADMIN` of team
  identity X, `LEAGUE_ADMIN` of league Y, `REGION_ADMIN` of federation Z, `GLOBAL`), from `ResultActor` /
  the role that admitted the action; `action` (enum); target `entityType` + `entityId` (plain ids, no
  FK — like `entity_history`, the trail survives a delete); `federationId` and `seasonId` of the league
  (visibility and retention); `occurredAt`; `details` as JSON.
- **Actions, M1**: `RESULT_ENTERED`, `RESULT_CONFIRMED`, `RESULT_CORRECTED` (an entry after the result
  was final, `MatchDay.firstFinalAt`), `LINEUP_SAVED`, `LINEUP_SUBMITTED`, `SUBSTITUTION_RECORDED`,
  `SUBSTITUTION_REMOVED`. Later: roster (`ROSTER_SUBMITTED`/`CONFIRMED`/`REOPENED`, player added/removed),
  `ROLE_GRANTED`/`REVOKED`, player data changes (doc 25).
- **Details**: data minimisation — **ids only** (player ids, match ids), scores before and after for
  results; **no names, no free text, no IP addresses**. Names are resolved when the history is shown.
- **Written** by the service (`MatchDayResultService`, `LineupService`) in the **same transaction** as the
  change, so the trail never contradicts the data. Rows are never updated or deleted, except by
  retention.
- **Table fields stay** as display values of the current state ("entered by", "confirmed at").
  **`entity_history` stays** for point-in-time reconstruction; it may become one source of audit events
  later. **Logs** are for operations only (errors, performance), optionally mirroring audit events.
- **Visibility**: the **federation admins of the league's federation** (`REGION_ADMIN` of
  `League.season.federation`) and global admins — **each federation separately**: a DTFB root-federation
  admin sees the history of DTFB leagues, not of a state federation's leagues (doc 16). **Not** league
  admins, **not** captains. Shown as a "History" section on the fixture page; endpoint
  `GET /v1/matchdays/{id}/history`, gated accordingly.
- **Retention**: a daily job deletes all events of a season **12 months after `Season.endDate`** — not
  12 months after each event, so a season's trail stays together through protests, the promotion and
  relegation decisions after the last matchday and possible appeals. The results and line-ups
  themselves are the sporting record and stay.

## Open questions

- **Retention confirmed?** 12 months after season end is the proposal; the data protection officer
  (Eddie) should confirm it. Related to B-2026-09-28-10 (the "retroactive" checkbox instead of a
  data-protection sign-off for the name history).
- **Captains**: should they see the history of their own fixtures (who entered/changed what)?
  Decided "no" for now.
- **Seasons without an end date** never expire under this rule — require an end date for leagues with
  recorded actions, or fall back to a fixed age.
- **Before the first deploy**: no backfill — the trail starts when the feature ships; earlier results
  have only their table fields.
