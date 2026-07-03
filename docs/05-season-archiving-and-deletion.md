# Season Archiving & Deletion

> Single source of truth for **what happens when a season is deleted**, the **archive**
> (soft-delete) alternative, and how an archived season's data is **hidden**. The backend owns
> and enforces this; frontends only *consume* it (the admin delete dialog reflects this policy).
> Companion to [`01-competition-and-registration-model.md`](./01-competition-and-registration-model.md)
> (the season → competition → … tree) and [`03-authorization-model.md`](./03-authorization-model.md).

## 1. Problem

A season is the root of a deep tree: `Season → Competition → Discipline → Stage → Pool → Round →
MatchDay → Match → MatchSet/MatchEvent`, plus `Standing`. Once a season has been played it holds
**official results** (match-day results, standings) that people reference for years. Two naive
delete strategies are both wrong:

- **Hard cascade delete** — one click irreversibly destroys results/standings/history. Too dangerous.
- **Pure restrict** (delete children manually first) — miserable for a deep tree (hundreds of
  match-days); nobody hand-deletes the spine.

The original implementation did neither on purpose: a plain `repository.delete(season)` hit a
**foreign-key violation → 500**. So a deliberate policy was needed.

## 2. Decision — state-aware delete + archive

| Season state | Behaviour |
|---|---|
| **Result-free** (no played/submitted match-day, no standing) | **Hard delete**, cascading the structure leaf→root in one transaction. It's setup/a mistake — nothing of value is lost. → `204` |
| **Has recorded results** | **Refused** with `409 SEASON_HAS_RESULTS` + a structured body of counts; the caller **archives** instead. |
| **Archive** (any state) | **Soft delete**: set `archivedAt`; data preserved, season hidden. Reversible via **unarchive**. |

"Has results" = any `MatchDay.resultState != OPEN` **or** any `Standing` under the season.

Archiving — not hard delete — is the intended action for a *completed* season: you want it out of
the active UI, not destroyed.

## 3. Hiding an archived season

Archiving hides the season **and its entire subtree** from read endpoints (data is kept, just not
served):

- **Season list** — `GET /v1/seasons` returns active only (`findByArchivedAtIsNull`);
  `GET /v1/seasons/archived` exposes archived ones.
- **Subtree** — every entity below resolves up its `@ManyToOne` spine to exactly one season, so each
  read path filters on `<spine>.season.archivedAt is null`. Repos expose `findAllVisible()` /
  `findVisibleById()`; services route `getAll()` / `get(id)` (and standings' by-pool read) through
  them. Covers competition, discipline, stage, pool, round, match-day, match, match-set, match-event,
  standing.

**Read paths only.** `create`/`update`/`delete`, the `@authz` resolvers, and the cascade/`contentsOf`
queries are deliberately **not** filtered — see §5.

## 4. API surface

| Method | Endpoint | Result |
|---|---|---|
| `GET` | `/v1/seasons` | active seasons |
| `GET` | `/v1/seasons/archived` | archived seasons |
| `POST` | `/v1/seasons/{id}/archive` | soft-delete (idempotent) → `SeasonDto` |
| `POST` | `/v1/seasons/{id}/unarchive` | restore → `SeasonDto` |
| `DELETE` | `/v1/seasons/{id}` | `204` if result-free; `409` if it has results |

The `409` body is a typed contract (documented via `@ApiResponse`, so the generated `@dtfb/api`
client gets the model — not a hand-typed shape on the frontend):

```json
{
  "code": "SEASON_HAS_RESULTS",
  "message": "Season has recorded results and cannot be deleted; archive it instead.",
  "attached": { "competitions": 1, "matchDays": 2, "matchDaysWithResults": 1, "standings": 2 }
}
```

## 5. Implementation notes

- **`SeasonStructure`** centralizes the season-scoped JPQL: `contentsOf(id)` (the counts behind the
  guard + 409 body) and `deleteStructure(id)` (ordered leaf→root bulk deletes, `IN (subquery)` form so
  the deep navigation lives in a SELECT subquery — valid HQL bulk delete). Not JPA `cascade = REMOVE`
  (which would load the whole graph).
- **`@Query` filtering, not `@SQLRestriction`.** A global `@SQLRestriction` would be one annotation
  per entity but: (a) its raw SQL isn't dialect-portable — the reserved word `match` needs different
  quoting on H2 vs MySQL; and (b) it applies to *every* query, which would blind `contentsOf` for an
  already-archived season → a delete-guard hole. Explicit `@Query` on the read methods keeps it
  portable (Hibernate handles dialect) and surgical (writes/authz/cascade untouched).
- **Shared-season invariant.** Every entity in a subtree shares one season, so there's never a
  partial state (visible child under a hidden parent) — filtering each read path is consistent.
- **`archivedAt` is server-managed** — the mapper ignores it on create/update; only archive/unarchive
  set/clear it.

## 6. Frontend

The admin delete-season dialog mirrors this: it attempts the delete and, on `409 SEASON_HAS_RESULTS`,
flips to a blocked state showing the `attached` counts and offers **"Archive instead"**
(`POST …/archive`).

## 7. Known edge & follow-ups

- **Deleting an *already-archived* season:** `contentsOf` is unaffected by the read filters, so the
  guard still works — but this path isn't a normal flow (archived seasons are hidden from the list).
  Revisit if a hard-delete-from-archive UI is added.
- **No archived-seasons view yet** in the frontend — you can archive but not browse/restore archived
  seasons in the UI (the `GET /v1/seasons/archived` + `unarchive` endpoints exist for it).
