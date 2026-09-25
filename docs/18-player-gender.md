# Player gender — a fixed enum, with "divers" split by league side

> **Implemented (2026-09-23).** `Player.gender` was free-form text (`"man"` | `"woman"` in
> practice). It is now the `PlayerGender` enum — `MALE`, `FEMALE`, `DIVERSE_MEN` ("divers
> (Herrenligen)"), `DIVERSE_WOMEN` ("divers (Damenligen)"). The league side of a divers player is
> internal: `PlayerDto.genderDetail` (all four values) is populated only for callers with an admin
> role; `PlayerDto.gender` is the public `Gender` projection (`male` / `female` / `diverse`) that
> everyone sees. The league side exists because league eligibility filters on it (doc 19).
>
> See also: [02-role-concept.md](./02-role-concept.md) (the roles "admin role" refers to),
> [09-league-model.md](./09-league-model.md) (the leagues that will filter on this),
> [14-team-player-versioning.md](./14-team-player-versioning.md) (`entity_history`, which tracks
> gender changes).
>
> Question raised: how do we record "divers" when leagues are split into men's and women's
> leagues, without exposing that split publicly?
> Short answer: **store four values, show three — the league side is admin-only.**

## Context

German law recognises a third gender, *divers*. DTFB leagues are nonetheless split into men's
(Herren) and women's (Damen) leagues, so for a divers player we still need to know which side
they compete on — that is what future league eligibility checks will filter on. Publicly, though,
that person's gender is simply "divers"; the league side is an administrative fact, not part of
their public profile.

Before this change the field couldn't carry any of that: `Player.gender` was an unvalidated
`String`, edited through a free-text input in the admin app's player edit dialog, and read
nowhere else in the UI.

## Options considered

| | How | Verdict |
|---|-----|---------|
| **One stored enum with four values + a derived public projection (chosen)** | `PlayerGender` stores the league side inline; `toPublic()` collapses both divers values to `Gender.DIVERSE` | **Chosen.** One column, one dropdown value, one masking step. Matches the four options admins pick from. |
| Two columns: `gender` (male/female/diverse) + `leagueSide` (men/women) | League side stored separately; required only for divers, derived for male/female | Rejected. Its league side is redundant for male/female players and needs a cross-field validity rule. The admin UI would still be a single four-option dropdown mapping onto two fields. |
| Plain "divers" (three values) | No league side recorded | Rejected. League eligibility needs to know which side a divers player competes on. |

Who sees the league side was a separate choice:

| | Who sees `genderDetail` | Verdict |
|---|-----|---------|
| Global admin only | Matches who may *edit* a player (`PUT /v1/admin/players/{id}` is `@authz.isAdmin()`) | Rejected. Region/league/club admins assign players to leagues and need to check eligibility. |
| **Any admin role (chosen)** | `admin`, `region_admin`, `club_admin`, `league_admin` | **Chosen.** Excludes `team_admin`, plain users, and any future public consumer. |
| Any logged-in user | Only a future anonymous consumer gets the masked value | Rejected. Every captain would see it. |

## Decision

- **`PlayerGender`** (`player/PlayerGender.java`, stored by `name()`, wire values lowercase via
  `@JsonValue`/`@JsonCreator` like `Role`): `MALE` / `FEMALE` / `DIVERSE_MEN` / `DIVERSE_WOMEN`.
  - `toPublic()` → `Gender`.
  - `leagueSide()` → `LeagueSide.MEN` / `WOMEN`: `MALE`/`DIVERSE_MEN` compete on the men's side,
    `FEMALE`/`DIVERSE_WOMEN` on the women's side. Category eligibility (doc 19) filters on it.
- **`Gender`** (public): `male` / `female` / `diverse`.
- **`PlayerDto`** carries both fields:
  - `gender` — always populated (the public projection).
  - `genderDetail` — null unless the caller holds an admin role.
  - The write path (`PlayerService.update`) reads `genderDetail` only; `gender` is derived.
- **Masking lives in one place.** `PlayerGenderVisibility` nulls `genderDetail` unless
  `AuthorizationService.hasAnyAdminRole()` (backed by `AccessRoles.hasAnyAdminRole`). It runs at
  the two post-mapping hooks every player read passes through:
  - `PlayerService.withClubs` — covers `GET /v1/players/{id}` and the update response.
  - `PlayerDirectoryService.withClubs` — covers `GET /v1/admin/players` and the roster "add
    player" search.
- `hasAnyAdminRole()` is a read-side visibility check: it returns `false` rather than throwing for
  a token without `dtfb_id` (a service-account JWT). This is unlike the write gates, which throw.
- **Migration `V9__player_gender_enum.sql`** maps `man` → `MALE` and `woman` → `FEMALE`, clears
  anything else, and narrows the column to `varchar(20)`. It stays a varchar rather than a MySQL
  `enum`, following the `tracker_issue.status` precedent (V6), so adding a value never needs a
  column migration. The migration preserves data, so the VPS needs no wipe.
- `entity_history` rows written before V9 keep their original `"man"`/`"woman"` text. The history
  view only displays them, so this is left as is.
- **Frontend:** the player edit dialog's gender field is a `dtfb-select` with the four options (it
  had a "Not specified" option until gender became mandatory in V11); it writes `genderDetail`. The
  player detail page shows it (`genderDetail` for admins, the public `gender` otherwise); lists
  don't yet — deferred to the general view-consistency pass.

## What did NOT need to change

- The player edit authorization (`@authz.isAdmin()`) and the history endpoints — gender was
  already a tracked field in `PlayerService.update`'s `ChangeSet`.
- Rosters, club membership, and every other `Player` reference — nothing filtered on gender
  before, so there was no behaviour to migrate.

## Open questions / watch for

- ✅ **League gender eligibility — RESOLVED (2026-09-25), see
  [19-category-eligibility.md](./19-category-eligibility.md).** It lives on the league's `Category`
  (`eligibleSide`, by league side: a women's-side category admits `FEMALE` + `DIVERSE_WOMEN`, a
  men's-side one `MALE` + `DIVERSE_MEN`; no divers-only option), is enforced in `RosterService` on
  add and submit. `Player.gender` (with first/last name and birth year) became mandatory in V11,
  so "not specified" is no longer a valid value. `LeagueSide` moved from a nested
  enum of `PlayerGender` to top-level `player.LeagueSide`.
- **A public read tier** (doc 10 §4) would get `gender` only. `genderDetail` is already null for
  any caller without an admin role, so no extra work is expected there. Re-check this if a
  public endpoint maps `Player` through a path other than the two hooks above.
- **New `PlayerMapper` call sites** must go through `PlayerGenderVisibility`. A direct
  `playerMapper.toDto` in a new service would leak the league side.
