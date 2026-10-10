# Importer — pluggable sources, one shared preview-and-apply pipeline

> **Decided and built 2026-10-10 (Marvin), SPO-46.** Past seasons followed the same day: doc 29. The importer becomes a framework instead of
> one hard-wired class per entity (the old `importer/` package, deleted in `3ae017b`). Each data source —
> the Sports Manager now, Kickertool (tournament results) later, unknown ones after that — is an
> `ImportSource` adapter that only turns a file into a neutral `ImportBatch`. Everything after parsing is
> shared: matching against existing records via a generic `external_reference` table, validation,
> a **preview** (new / changed / unchanged / conflict / rejected), an explicit **apply** through the
> existing services, and a run log. Imports are **re-runnable**: the same or a newer export shows only
> the differences. Sports Manager data arrives as a new **"Sports Hub export"** written by
> `com_sportsmanager` itself (one UTF-8 JSON with all SM ids), pseudonymized deterministically for the
> test system. Missing data follows **"complete to play, not complete to exist"**; missing player numbers
> are issued by the Sports Hub under their own prefixes and later **linked**, never overwritten.
> Refines [24-player-identity-and-numbers.md](./24-player-identity-and-numbers.md) (matching key,
> number issuing, anonymization on the test system).
>
> See also: [24-player-identity-and-numbers.md](./24-player-identity-and-numbers.md),
> [15-club-membership.md](./15-club-membership.md) (membership before roster),
> [19-category-eligibility.md](./19-category-eligibility.md) (V11 made name, birth year and gender
> mandatory), [14-team-player-versioning.md](./14-team-player-versioning.md) (`entity_history`),
> [25-player-data-change-approval.md](./25-player-data-change-approval.md),
> [27-audit-trail.md](./27-audit-trail.md), [09-league-model.md](./09-league-model.md) §5 (tournaments
> and the old importer parked).
>
> Question raised: with several import sources (Sports Manager, Kickertool, others), should there be a
> common interface — and how does SM data get in, how are bad data, missing player numbers and the test
> system's privacy handled?
> Short answer: **yes — one adapter per source up to a neutral batch, everything after that shared; SM
> delivers its own export file; incomplete players are imported but can't be rostered; the Sports Hub
> issues missing numbers under separate prefixes.**

## Context

- M1 (Regionalliga, 20./21.03.2027) needs the Sports Manager's players, clubs, memberships and
  federations in the Sports Hub; the real import on production is planned for January. Teams and
  rosters of the Regionalliga teams follow. M5 (roadmap) adds historical results.
- Further sources are certain: Kickertool runs tournaments and would bring **tournament results**;
  others are unknown. Tournaments are still parked in the model (doc 09 §5).
- The old importer read a custom JSON (`import-format/importFormat.json`) of the pre-split competition
  tree, matched teams by name, never imported player master data, and had no preview. It is not reusable.
- There are up to five SM installations (DTFB, TFVHH, MTFV, TFVSH, STFVH), each with its own ids.
- The SM's existing downloads are a poor source: the player CSV (`adminExportSpieler`) has no
  `spieler_id`/`verein_id` — clubs only by name and town —, its columns depend on export options, and it
  is Latin-1 encoded. Matching clubs by name breaks on the first rename or typo fix.
- SM data quality varies; records from 2015 and earlier often lack birth year or gender and can no longer
  be completed. Player numbers (`SS-NNNN`, `SS` = federal state) are unique but may be missing — even
  for the same person over time.
- The Sports Hub has no notion of a record's source: no `source`/`externalId` anywhere except the unused
  `League.importId`.

## Options considered

**Shape of the importer**

| | How | Verdict |
|---|-----|---------|
| **One importer per source, end to end** | each source parses, matches and writes itself | **Rejected** — matching, validation, history and preview would be written once per source and drift apart. |
| **Adapter per source + shared pipeline** | `ImportSource.parse()` → neutral `ImportBatch`; planner, preview, apply, run log shared | **Chosen.** A new source is one parser. |
| **Direct writes, no preview** | like the old importer, one transaction | **Rejected** — a re-run against live data needs to show what it would change before it changes it. |

**How SM data arrives**

| | How | Verdict |
|---|-----|---------|
| **A. Existing SM downloads** | upload player CSV + competition JSON as they are | **Rejected** — no SM ids for clubs/teams, option-dependent columns, Latin-1. |
| **B. Direct DB read** | backend reads the Joomla MySQL read-only | **Rejected for now** — needs DB access to every installation, couples us to the SM schema. |
| **C. New "Sports Hub export" in the SM** | admin action in `com_sportsmanager` writes one JSON in our format, uploaded in the import view | **Chosen.** We maintain and deploy the component; all ids, UTF-8, our format. |
| **D. Pull API on the SM** | the Sports Hub fetches on a schedule | **Later**, if SPO-47 decides on a real sync — only the transport changes, adapter and pipeline stay. |
| **E. SQL script run by someone** | our script against the SM DB, output uploaded | **Rejected** — a manual expert step on every run. |

**Bad or missing data**

| | How | Verdict |
|---|-----|---------|
| **Reject, fix in the SM** | incomplete players not imported | **Rejected as the only rule** — old players can never be fixed and would stay out forever. |
| **Complete in the preview** | admin fills gaps before applying | **Rejected** — Sports Hub and SM diverge, every re-run conflicts. |
| **Import incomplete, block from playing** | missing birth year/gender is a warning; roster add/submit refuses the player | **Chosen** — "complete to play, not complete to exist". |

**Numbers the Sports Hub issues**

| | How | Verdict |
|---|-----|---------|
| **One prefix + reason flag** | e.g. `SH-` for all, the reason beside it | **Rejected** — the reason is known when issuing and is wanted in the number. |
| **Separate prefixes by reason** | `NG-` missing in the SM, a hobby prefix for players outside the DTFB | **Chosen**, with linking to a later real number. |

## Decision

**The seam**
- Now: `ImportSource` (`key()`, `supports()` → `ImportRecordType`s, `parse(InputStream, filename)` →
  `ImportBatch`), Spring beans collected via `List<ImportSource>`. `ImportBatch` = header (`source`,
  `instance`, `exportedAt`, format version, `anonymized`) + neutral records: `ImportedFederation`,
  `ImportedClub`, `ImportedPlayer`, `ImportedMembership`. Teams, roster entries and results are further
  record types added when needed — the format allows them, M1 master data comes first.
- Now: generic `external_reference(source, instance, entity_type, external_id) → entity_id`, unique on the
  first four. Ids are namespaced per source **and installation**.
- Now: `import_run` (source, instance, file, target federation, status `PREVIEWED`/`APPLIED`/`DISCARDED`/
  `FAILED`, who, when, counts) and `import_item` (record type, external id, action `NEW`/`UPDATE`/
  `UNCHANGED`/`CONFLICT`/`REJECTED`, target entity, manual match, field diff, warnings, reason).
- Now: **preview** plans without domain writes; **apply** re-plans in one transaction and refuses with
  `409 IMPORT_STALE` if the result differs from the stored preview. Writes go through `PlayerService`,
  `ClubService`, `ClubMembershipService` so rules and `entity_history` stay in one place; the actor is the
  admin's `dtfb_id`. Endpoints under `/v1/admin/imports`, global admin only for now.
- Now: a frontend import view — run list, upload (source, target federation, file), run page with tabs per
  action and Apply (confirm dialog) / Discard.

**Matching and re-runs**
- Now: lookup order `external_reference` → (players) any current or alias player number → otherwise
  `NEW`. Name + birth year only raise a **duplicate suspicion**; the admin may assign the record to an
  existing player in the preview ("manual match"). There is no automatic name match.
- Now: **absence never deletes.** A player missing from a newer export stays; SM `ausgetreten` sets
  `ClubMembership.leftAt` (doc 15).
- Now: `CONFLICT` when a field the source wants to change was edited in the Sports Hub since the last
  applied import (`entity_history` newer than the reference's last apply): not overwritten, shown.
- Now: an applied import is the approval — imports do not go through the captain → club admin →
  federation admin path of doc 25.

**Data rules**
- Now: three levels. *Normalize* silently (whitespace, case, gender codes, encoding). *Warning* —
  imported, shown: no licence, implausible birth year, duplicate suspicion, **missing birth year or
  gender**. *Error* — not imported, with reason: no name, no source id, the same number twice in one
  export, a membership pointing to an unknown or rejected player/club. Nothing is guessed or defaulted.
- Now: `player.birth_year` / `player.gender` become nullable again (partly reverses V11, doc 19); roster
  add and submit refuse an incomplete player with `409 PLAYER_INCOMPLETE`, next to the eligibility
  check. A later export with the data completes the player through a normal update.
- Now: warnings of current players are downloadable per club, so federation admins fix them in the SM
  and the next run picks the fix up.

**Player numbers** (refines doc 24)
- Now: the **matching key is the source's internal id** (`spieler_id` per installation), not the number —
  the number can be missing, arrive later or change. The number stays the cross-source identity: it is
  the second lookup step and links a Kickertool player or another installation to an existing player.
- Now: every player in the Sports Hub has a number. Numbers the Sports Hub issues carry a **prefix by
  reason**: `NG-` for "missing in the SM" (a DTFB member without a number — **not** excluded from
  official contexts), a hobby prefix (doc 24's `XX` range) for players outside the DTFB. One DB sequence
  per prefix, zero-padded to four digits like real numbers (`NG-0001`), but no code assumes the width.
- Now: numbers are **linked, not overwritten**: `player_number(player_id, number, kind REAL/NG/HOBBY,
  valid_from, valid_to)`; `Player.nationalId` is the current one, older ones stay searchable as aliases.
  An SM export that later carries a real number replaces `NG-…`; a hobby player who joins a club appears
  as a new SM player with a real number, the preview flags the duplicate suspicion, the admin assigns
  them, and the hobby number becomes an alias.
- Watch for: duplicates that already exist (created by hand, or applied before anyone noticed) need a
  general **player merge** — memberships, roster entries, line-ups, results, external references and
  numbers move to the surviving player. Separate ticket, not part of the importer.

**Anonymization on the test system** (refines doc 24 / B-2026-09-28-3, which allowed real data there)
- Now: the SM export gets a "pseudonymize for the test system" option, so real data never reaches the VPS.
- Now: **deterministic**, keyed with a secret kept in the SM configuration: the same player gets the same
  fake name, number and birth year in every export — otherwise re-runs can't be tested on the test
  system. Names from name lists by gender; the number keeps its state prefix, digits hashed with a
  collision check; birth year shifted by up to ±2; `spieler_id` hashed. Gender, clubs, federations and
  "has no number" stay unchanged.
- Now: the header carries `anonymized: true`; `importer.require-anonymized` (on for the VPS) refuses real
  exports there, production refuses anonymized ones.

**Not now**
- Teams and roster entries of the Regionalliga teams (next record types, after master data).
- The Kickertool adapter — after the tournament model exists; own ticket.
- A pull transport (option D) and import rights below global admin.
- Audit events for applied runs — once doc 27 (SPO-118) is built.

**Undo** (decided 2026-10-10)
- Now: every apply is journaled (`import_change`: created / field changed old → new / deleted, in order), captured from
  Hibernate's insert/update/delete events, so no writer can forget it. Left out: the importer's own records, the
  standings cache (rebuilt) and the number sequences (issued numbers are never reused).
- Now: an applied run can be undone by a global admin, on every instance: the journal is replayed backwards --
  created records deleted, changed fields restored. All or nothing; `GET …/undo` shows what would happen,
  `POST …/undo` refuses with `409 IMPORT_UNDO_BLOCKED` and the blockers when: a later applied run wrote the same
  records (undo newest first), a changed field was changed again since, a created record is used outside the run
  (every association in the model is checked, plus references of other installations), or the run replaced data.
- Watch for: runs that **replaced** data (a re-import rewriting a fixture's games, docs/29) can't be undone yet --
  the deleted rows aren't restorable generically. Correct those by another re-import.

## Open questions

- **Is a player number ever reissued to another person?** Assumed no (the numbers are unique) → unique
  constraint on `player_number.number`.
- **Name of the hobby prefix, and who may issue hobby numbers** — the Sports Hub on registration, or a
  federation admin? → SPO-117
- **Scope of the M1 import:** all SM players, or only those with an active membership / recent play?
  Historical players only matter once old results are imported (M5).
- **SM membership status:** `mitgliedsstatus` 2 (restricted) and 3 (passive) are imported as active
  members for now; whether they need their own state is SPO-95.
- **Does the stricter test-system rule need a team decision?** It goes beyond B-2026-09-28-3 → mention in
  the meeting.
