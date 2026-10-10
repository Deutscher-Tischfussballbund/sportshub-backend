# Historical leagues — old Sports Manager seasons as final, read-only history

> **Decided and built 2026-10-10 (Marvin), SPO-102.** The importer of doc 28 gets a second kind of data:
> finished **team leagues** from the Sports Manager — season, league, teams, rosters, fixtures, every game with its
> players and set scores, and the final table. Historical seasons are written **as already final** by a dedicated
> writer, not through the live workflow, because every live guard refuses the past (registration into an ended
> season, frozen rules, today-only club membership, results by a logged-in user stamped "now"). The SM's own final
> table is **frozen as the official table** of the season, and a league's identity across seasons is **suggested by
> name and confirmed by the admin**. Cups, KO brackets, tournaments and individual competitions stay out.
>
> See also: [28-importer.md](./28-importer.md) (the pipeline this extends),
> [09-league-model.md](./09-league-model.md) (§5: tournaments parked, §7: no tiebreak columns on `Standing`),
> [14-team-player-versioning.md](./14-team-player-versioning.md) (season-scoped teams, team/league identities),
> [17-result-entry-confirmation.md](./17-result-entry-confirmation.md) (`firstFinalAt`: final once, admin-only),
> [21-rule-set-blueprints.md](./21-rule-set-blueprints.md) (snapshots frozen once a season ended),
> [22-fixture-modes.md](./22-fixture-modes.md) (RACE/GAMES, table order),
> [23-lineups-and-substitutions.md](./23-lineups-and-substitutions.md),
> [05-season-archiving-and-deletion.md](./05-season-archiving-and-deletion.md) (seasons with results can't be deleted).
>
> Question raised: can the importer bring in old leagues?
> Short answer: **yes — team leagues, written as final history by their own writer, with the SM's final table frozen
> as the official one.**

## Context

- The SM keeps team leagues since the early 2000s per installation: `saison` (a name, no dates) → `veranstaltung`
  (one league = one group; `modus_id`, `tabellenwertung`, `erster_tag`/`letzter_tag`) → `team` (one row per season;
  `verein_id`; `teamgruppe_id` links a team across seasons; a stored table in `platz`, `gesamtpunkte`, `siege`, …,
  `zusatzpunkte`) → `mitglied_von_team` (roster) → `begegnung` (fixture; `spieltag`, `spieltag_titel`, `zeitpunkt`,
  totals; NULL or 0:0 = not played; an `unbestaetigtes_ergebnis` row = not confirmed) → `teamspiel` (one game; up to
  two players per side, 0 = unknown; game points; `ergebnis_detailliert` set scores like `5:3 2:5`, missing in old
  data). The game plan is the string `teamspiel_modus.modus` (`D1D2H,E1E1,…|links`).
- The SM has no tiers or groups: each `veranstaltung` is one table. Nothing links "Landesliga Nord 2019" to
  "Landesliga Nord 2020" except the name.
- The SM's tables follow its own rules: points per `tabellenwertung`, quotients or differences, Buchholz for Swiss
  leagues, penalty points (`zusatzpunkte`), shared places, manual places (`tabellenwertung` -2). The Sports Hub
  computes tables from results with one fixed order (doc 22) and has no deductions or manual order.
- The live workflow refuses historical data at every step: `SEASON_ENDED` on registration, `RULE_SET_FROZEN` after
  the season end, roster add needs an active club membership *today* and a complete player, result entry needs a
  logged-in user, checks RACE segments and stamps the current time.
- An earlier attempt (Marvin's unmerged SM branch `feature/sportshub-export`, May 2026) exported the competition tree
  in the pre-split format; lessons kept: split `modus` at `|` first, align games by `teamspiel_nummer`, fall back to
  game totals when set details are missing, treat player id 0 as unknown.

## Options considered

**Scope**

| | How | Verdict |
|---|-----|---------|
| **Team leagues only** | round-robin and Swiss leagues with a table | **Chosen.** Everything they need exists in the model. |
| + cups/KO | cup rounds and brackets as fixtures without a table | **Not now** — needs playoffs (SPO-99). Listed as skipped in the preview. |
| Everything | also tournaments and individual competitions | **Not now** — the tournament model is parked (doc 09 §5). |

**Which table counts**

| | How | Verdict |
|---|-----|---------|
| **Freeze the SM table** | the SM's final table becomes the official table of the imported group; the preview compares it with a recomputed one | **Chosen.** What was official stays official, incl. penalties and the SM's tie-breaks. |
| Recompute | only results are imported, the Sports Hub computes the table | **Rejected** — penalties, quotient orders and manual places would be lost; old tables could show other champions. |

**League identity across seasons**

| | How | Verdict |
|---|-----|---------|
| **Suggest by name, admin confirms** | same name (year stripped) → suggested link to that league identity; the admin confirms or changes it in the preview | **Chosen**, same idea as the manual player match of doc 28. |
| Every season separate | each imported league gets its own identity | **Rejected** — league admins (doc 14, SPO-28) and history views need the link. |
| Mapping file | a hand-made table delivered with the import | **Rejected** — one more artefact to maintain per installation. |

**How it is written**

| | How | Verdict |
|---|-----|---------|
| Through the services | registration, roster, result services | **Rejected** — every guard refuses the past (see Context). |
| **Dedicated writer** | `HistoricalImportWriter` writes via repositories, keeping the model's invariants, like copy-forward's roster clone (doc 09) | **Chosen.** |

## Decision

**Mapping**
- Now: `saison` → `Season` in the run's target federation; dates = the earliest `erster_tag` and latest
  `letzter_tag` of its leagues. A season that hasn't ended is rejected — running seasons belong to the live workflow.
- Now: `veranstaltung` → `League` with one `Tier` and one `Group` and its own rule-set snapshot built from
  `teamspiel_modus` + `tabellenwertung` (`D` doubles, `E` singles, `S` result only; points for win/draw; RACE when
  `spielpunkte_bedingung` is set, else GAMES). Leagues with `tabellenwertung` < 0 except -2 (manual) are skipped.
- Now: `team` → a season `Team` (identity from the `teamgruppe_id` root, club via its CLUB reference) with an ACTIVE
  `TeamParticipation`, roster CONFIRMED. `mitglied_von_team` → `RosterEntry` (`addedAt` = season start, `removedAt`
  when `ausgetreten`), without membership or eligibility checks — history is what it was.
- Now: `begegnung` → a `Round` per `spieltag` (named after `spieltag_titel`, else "Spieltag N") and a `MatchDay`.
  Played: CONFIRMED, `decidedAt` = `firstFinalAt` = both confirmations = `zeitpunkt` — final, so admin-only (doc 17).
  Not played: OPEN. Unconfirmed in the SM: SUBMITTED, with a warning; it doesn't count, as in the SM.
- Now: `teamspiel` → `Match` (position = `teamspiel_nummer`, game totals, PLAYED, winner), its sets as `MatchSet`s,
  and the players as a `Lineup` per side with one `LineupEntry` per game and slot. Unknown players (id 0) leave the
  slot empty.
- Now: the team's stored table → **official table** rows of the group (below).
- Now: new record types `SEASON`, `LEAGUE`, `TEAM`, `ROSTER_ENTRY`, `FIXTURE` (a fixture carries its games), matched
  through `external_reference` like master data, so a re-run shows only differences. Players and clubs come from the
  same file's master data.

**Official table**
- Now: `official_table_entry` per group and team (place, played, won, drawn, lost, goals for/against, points, points
  adjustment, source). When a group has such rows, `GET /v1/groups/{id}/standings` serves them instead of computing;
  withdrawn handling stays. The stored `Standing` cache is still recomputed, for the delete guards.
- Watch for: the same rows are the natural home for a manual tie order (lot, penalty shoot-out) and point deductions
  in live seasons later.

**Identity suggestions**
- Now: the preview suggests linking an imported league to an existing league identity with the same name (year
  stripped), and an imported team without a known `teamgruppe_id` link to a team identity of the same club and name.
  The admin confirms or changes the suggestion; the manual match of doc 28 is generalized from players to leagues and
  teams.

## Open questions

- **Undoing an applied historical run.** An imported season has results, so it can't be deleted (doc 05). Test
  imports on the VPS need a way back — proposal: a guarded "remove imported season" while nothing outside the import
  references it.
- **Seasons in several installations** (DTFB and the state SMs) become separate seasons per federation — enough?
- **Names as of then:** the SM only has current player names; per SPO-50 old seasons show current names anyway.
- **Validation needs real data:** a pseudonymized export of one installation, run by someone with SM access.

## Summary of deviations from the original design

- **Link suggestions apply by default.** A league or team linked by name joins that identity unless the admin
  says otherwise in the preview ("keep the link" / "don't link"); leagues of the same name inside one file share a
  new identity. Teams first follow the source's own cross-season link (`teamgruppe_id`) from earlier runs.
- **RACE leagues get running scores.** The SM stores goals per game; in a league with a race target the writer
  adds them up, so a game's score is the running score at its end, as RACE expects (doc 22).
- **Imported dates are fixed** (`SchedulingState.CONFIRMED`): they are history, not proposals.
- **`import_item.link_id`** stores the chosen link, so a stale preview is detected like any other change (V20,
  together with `official_table_entry`).

