# Player identity and numbers — the player number becomes the stable key

> **Decided 2026-09-28, not yet built.** The Sports Manager player number (`spielernr`) becomes the
> stable identity of a `Player` and the reconciliation key of the importer (SPO-46), stored as
> `Player.nationalId`. This only works if *every* player has one, so players who are not registered
> with the DTFB — rookie, pub and regional fun leagues such as the Lübeck-Liga — get a number too,
> from a **dedicated range with its own prefix** that official competitions and official-facing
> search exclude. Real player data may be imported from the Sports Manager **without
> anonymization**, on the test instance and later in production.
>
> See also: [14-team-player-versioning.md](./14-team-player-versioning.md) (the `User`/`Player`
> split — `dtfbId` is the login identity, not the competitor record),
> [08-member-registration.md](./08-member-registration.md) (the DTFB-ID account claim, which will
> have to meet this key), [15-club-membership.md](./15-club-membership.md) (membership is the
> precondition for rostering an imported player),
> [19-category-eligibility.md](./19-category-eligibility.md) (name, birth year and gender became
> mandatory in V11 — the importer must supply them).
>
> Question raised: what is the stable key that lets the importer recognize a player again — on a
> re-run, on a correction, and against records that already exist?
> Short answer: **the player number, made universal by giving the unregistered players their own
> number range.**

## The problem

Nothing in the current model is a usable identity:

- `Player.nationalId`, `Player.internationalId` and `Player.nationalLicense` are all optional, not
  unique, and reconciled nowhere.
- The name is unusable as a key — renames happen (doc 14 records them in `entity_history`) and
  namesakes exist.
- The only genuinely unique value is `User.dtfbId` (Keycloak claim `dtfb_id`), but that is the
  *login*, not the competitor record, and the `Player → User` link is optional (doc 14).

The Sports Manager has `spielernr` and `lizenznr`. `spielernr` is the obvious candidate, and it is
indexed by federal state — `05` Hamburg, `12` Saxony, `13` Saxony-Anhalt, `14` Schleswig-Holstein,
`15` Thuringia. The blocker was coverage: the Sports Manager holds only players registered with the
DTFB, while a state federation's own instance also carries rookie and pub-league players who have
none, and who deliberately want nothing to do with the DTFB.

## Options considered

| | How | Verdict |
|---|-----|---------|
| Name (+ birth year) | Match on the human-readable identity | Rejected — renames and namesakes; doc 14 exists precisely because names move |
| `User.dtfbId` | Reuse the Keycloak login identity as the player key | Rejected as *the* key — it identifies a login, not a competitor, and the `Player → User` link is optional. Still open whether the two should be made equal (see Open questions) |
| `nationalLicense` | Use the licence number | Rejected — optional, and only licensed players have one |
| `spielernr` as-is | Take the player number and accept that some players have none | Rejected — an optional key is not a key; the importer would fall back to name matching for exactly the players it knows least about |
| **`spielernr` for everyone, own range for the unregistered** | Give non-DTFB players a number from a dedicated prefix range, excluded from official contexts | **Chosen** |

## Decision

- **Now:** `spielernr` is the stable player identity and the importer's reconciliation key, carried
  as `Player.nationalId`.
- **Now:** every player gets a number. Players not registered with the DTFB are numbered in a
  **dedicated range with its own prefix**, distinct from the federal-state indices. The prefix need
  not be numeric — two letters (e.g. `XX`) were explicitly floated and accepted.
- **Now:** that range is **excluded** from official-facing contexts — search for officials,
  Bundesliga and comparable official competitions.
- **Now:** if such a player later joins the DTFB or a federation, the prefix makes the old number
  recognizable; the old number is then **linked** to the newly issued one rather than overwritten.
- **Now:** importing real player data from the Sports Manager needs **no anonymization**, neither on
  the test instance nor in production. The DTFB accepts this explicitly: the Sports Manager's own
  staging environment is not anonymized either.

## Open questions

- **Who issues the numbers of the special range, and in which system?** Does the DTFB backfill them
  in the Sports Manager, or does the Sports Hub mint them at import time? Part of this sits with the
  DTFB, not with the team. → SPO-117
- **Is `spielernr` actually unique and never reissued?** The decision assumes both. If a number is
  ever recycled, `nationalId` cannot be a unique column and the importer needs a tie-breaker.
- **Should `dtfb_id` in Keycloak equal `spielernr`,** so that login and competitor record find each
  other automatically — and what does the Keycloak mapper write today, a username or an attribute?
  Never discussed in the meeting; blocks nothing yet, but blocks
  [08-member-registration.md](./08-member-registration.md). → SPO-46, SPO-85
- **Does the prefix live inside the number, or beside it as a flag?** "Exclude this range from
  official contexts" is a rule about a *property* of the player. Encoding it in the key means every
  consumer has to parse the key to learn the property.
- **Does `nationalId` become mandatory and unique?** Implied by "the stable key", not stated. It is
  a migration with real consequences for the existing seed and test data.
