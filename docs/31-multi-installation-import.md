# Multi-installation import — clubs across Sports Manager installations, in any order

> **Discussion — draft for the meeting 12.10., not yet proposed to the team (SPO-126).** There are about 13 Sports Manager installations (roughly one per state, plus the DTFB),
> and they can be imported in **any order** — the DTFB installation most likely first. Every run matches clubs
> against the clubs of all installations (suspicion by name + town, confirmed by hand, then remembered); a new club
> always lands in a **state federation**, never in the root (organiser first, otherwise a suggestion from its
> members' player-number prefixes); and a fixed **precedence of sources** replaces "whoever comes last wins": the
> state installation owns clubs and memberships, the DTFB installation its root-federation leagues.
>
> See also: [28-importer.md](./28-importer.md) (pipeline, matching, conflicts),
> [29-historical-leagues.md](./29-historical-leagues.md) (past seasons, team/league identity links),
> [24-player-identity-and-numbers.md](./24-player-identity-and-numbers.md) (player numbers, state prefixes),
> [16-root-federation.md](./16-root-federation.md) (root-level leagues), [15-club-membership.md](./15-club-membership.md).
>
> Question raised: the DTFB has a Sports Manager too — do its clubs and players land under the DTFB, under their
> state federation, or twice?
> Short answer: **under their state federation, once — whichever installation is imported first.**

## Context

- doc 28 assumed "up to five installations". In fact roughly every state runs its own Sports Manager — **about 13**
  — plus the DTFB's. Each has its own ids (`spieler_id`, `verein_id`, …); `external_reference` namespaces them per
  `source` **and** `instance`.
- **The DTFB has no clubs of its own.** Every club belongs to a state federation. Leagues the DTFB runs are
  root-federation leagues (doc 16), never state leagues. A run's leagues and seasons already go into the run's
  target federation, so a DTFB run (target `fed-dtfb`) and a state run (target = that state) are both right
  for leagues — nothing to change there.
- Players already work across installations: the player number is the second lookup step (doc 28), and
  memberships are deduplicated per (player, club) once both are linked.
- **Clubs don't.** An SM club has no key shared between installations, and the importer deliberately never
  matches by name automatically. A club that plays in a DTFB league exists in the DTFB installation *and* in its
  state's installation: two imports create it twice.
- Worse, a DTFB run creates its clubs **under the root**: the DTFB organiser maps to `fed-dtfb` (since V24 even by
  its abbreviation `DTFB`, doc 28), and the fallback is the target — `fed-dtfb` too.
- **The order can't be planned.** It's not certain that all state installations can be exported before the DTFB's;
  the DTFB installation is most likely imported first.

## Options considered

**Recognising the same club in another installation**

| | How | Verdict |
|---|-----|---------|
| Fixed order: states first, DTFB only links | the DTFB run never creates clubs | **Rejected** — needs every state export before the DTFB's, which isn't realistic; the DTFB comes first. |
| Automatic name match | same normalized name = same club | **Rejected** — renames, typos, "e.V." variants; doc 28 rules out automatic name matching for players for the same reason. |
| Shared club key in the SM | a national club number in every installation | **Rejected** — doesn't exist; would need a change in all ~13 installations and a numbering authority. |
| Mapping file per installation | hand-made `verein_id` ↔ club table shipped with the export | **Rejected** — one more artefact per installation (as in doc 29). |
| **Suspicion + manual confirmation, remembered** | every run suggests existing clubs by name + town; the admin confirms in the preview; the link is stored | **Chosen.** Same mechanism as player duplicate suspicions and league/team identity links (docs 28/29); order-independent. |

**Where a new club goes**

| | How | Verdict |
|---|-----|---------|
| Organiser only (today) | `verein.veranstalter_id` → federation, else the target | **Insufficient** — puts every DTFB club under the root. |
| Under the root for now, moved later | create in `fed-dtfb`, re-parent when the state import arrives | **Rejected** — breaks "no clubs under the root"; root admins would briefly own every club; moves are easy to forget. |
| Town → state lookup | derive the state from `vereinssitz` | **Rejected** — needs geodata, town names are ambiguous and often missing. |
| **Members' player-number prefixes** | numbers are indexed by state (`05` Hamburg, `14` Schleswig-Holstein, doc 24); the most common prefix among the club's members suggests the state | **Chosen as a suggestion**, confirmed by hand. A hint, not a key: transfers and `NG-`/hobby numbers dilute it. |

**When two installations disagree**

| | How | Verdict |
|---|-----|---------|
| Last writer wins | each apply overwrites | **Rejected** — a DTFB re-run would undo what the state installation wrote, and vice versa, depending on the day. |
| First writer wins | later installations only link | **Rejected** — with the DTFB first, its provisional club data would beat the state's own. |
| **Precedence by role** | state installation > DTFB installation for clubs and memberships | **Chosen.** Deterministic, independent of the order. |

## Decision

**Club matching (every run, every installation)**
- Now: lookup order for a club: (1) `external_reference` of this installation → (2) a **duplicate suspicion**
  against the existing clubs of **any** installation by normalized name + town → (3) `NEW`.
- Now: normalization: lower case, trimmed, whitespace collapsed, `ß`→`ss`, legal suffixes dropped (`e.V.`,
  `e. V.`, `eV`). Town missing on either side: the name alone still raises the suspicion.
- Now: a suspicion is never applied by itself. The admin confirms it in the preview (manual match, like players,
  leagues and teams) or keeps the club apart; a confirmed match writes the `external_reference`, so later runs of
  that installation find the club directly. New warning `CLUB_DUPLICATE_SUSPECT`.

**Placement of a new club**
- Now: **a club never belongs to the root federation** — enforced in the club API as well as in the importer.
- Now: the organiser maps to a state federation (link → abbreviation → name, doc 28) → the club goes there.
- Now: otherwise — organiser maps to the root, or to nothing — the importer **suggests** a state from the club's
  members' player-number prefixes. Federations get a list of their prefixes (`federation_number_prefix`; MTFV has
  `12`, `13`, `15`), maintained like the abbreviation. The suggestion is shown (`FEDERATION_SUGGESTED_BY_NUMBERS`,
  with the share of members behind it) and confirmed in the preview.
- Now: no usable hint (no members, only `NG-`/hobby numbers, a tie) → the club is **blocked**
  (`CLUB_NEEDS_FEDERATION`, an error) until the admin picks a state in the preview. Its teams and memberships are
  blocked with it, as with any rejected record.

**Precedence instead of order**
- Now: an installation's role follows from its runs' target federation: target = root → **national installation**
  (the DTFB's); target = a state → **state installation**. No new configuration.
- Now: for **clubs and memberships**, a state installation ranks above the national one. For **root-federation
  leagues**, only the national installation writes them anyway.
- Now: a lower-ranked run may **link** to a club, but doesn't change a field a higher-ranked installation wrote —
  shown, not applied (new conflict code `OWNED_BY_OTHER_SOURCE`). A DTFB "left" doesn't end a membership the state
  installation has active.
- Now: a higher-ranked run overwrites what a lower-ranked one wrote — the state's data replaces the provisional
  data from the DTFB. Fields written by *imports* don't count as local edits for this (today any `entity_history`
  newer than a reference's last apply raises `CHANGED_LOCALLY`, doc 28); only edits in the Sports Hub do.
- Now: absence still never deletes (doc 28): a membership only the DTFB installation knows stays until someone ends
  it.

**When**
- Watch for: the DTFB import alone needs only the placement part (no other installation to collide with yet). The
  matching and precedence parts must be in place **before the second installation** is imported.

## Open questions

- **Player master data across installations:** which installation wins when the DTFB's and a state's SM disagree on a
  player's name or birth year? Candidate: the state of the player's current club, mirroring clubs.
- **Memberships only the DTFB installation knows:** keep them as they are, or mark them "unconfirmed" until a state
  run has them too?
- **States without an installation of their own:** if any exist, the DTFB installation is their only source; their
  clubs then always go through the manual state choice. Which states are these?
- **Effort of the first runs:** every club suggestion is confirmed once per installation — a few hundred clubs in
  total. Is a bulk "accept all suggestions with name *and* town equal" needed, or is that already automatic matching
  through the back door?
