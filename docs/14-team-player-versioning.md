# Team versioning, the User/Player split, and entity history

> **Implemented.** `Team` is season-scoped, copied fresh into every season (like `League`/`Tier`/
> `Group` already were). `Player` split into a permanent `User` (Keycloak login identity) plus a
> `Player` (competitor record) — `Player` itself is NOT season-scoped. `Player` and `Club` stay
> single, un-duplicated rows; a generic `entity_history` change log records old/new values per
> field, reconstructible "as of" a point in time. A `LeagueRuleSet` edit is now refused once a
> closed season uses it. See `dbdiagram.io/current_model.txt` for the full schema.

## The problem

Doc 09's copy-forward clones `League → Tier → Group → TeamParticipation` per season, but until
now `Team`, `Player`, `Club`, and `LeagueRuleSet` were single shared rows referenced by ID from
every season. That's the right call for stats continuity (no need to stitch records via an
external key), but it meant:

1. **Renaming a team, player, or club retroactively rewrote every past season's display.** There's
   no record of "the name as it was" for a given season — a rename today changed what a 2023
   roster showed too.
2. **Editing a `LeagueRuleSet`'s fields retroactively changed the rules behind a closed season's
   already-final results.** Rulesets are shared by reference (`League.ruleSet`/`Tier.ruleSet`);
   nothing stopped in-place edits after a season using that ruleset had already closed. This was
   previously just a documented convention ("clone to diverge"), not enforced.

## Decision: copy Team per season; split Player into User + Player; history for Player/Club; enforce the ruleset lock

Two structurally different fixes for the same underlying problem, chosen per entity based on how
that entity is actually queried:

- **Team** is always read in a season's context already (`TeamParticipation`/`RosterEntry` chain
  up through `League.season`), so copying the row per season is free — no reconstruction needed
  anywhere, the correct row is just *there*.
- **Player and Club** are referenced directly by plain FK from places that don't carry a season
  (a `RosterEntry.player`, a `Team.club`) and are shared across arbitrarily many seasons at once.
  Copying them per season would mean duplicating a row for every season a person/club is ever
  touched by, just to answer "what was this called back then" — a lot of duplication for a
  cosmetic problem. Instead: **stay a single row, and log field-level changes with a timestamp.**
  A past display reconstructs the value that was in effect at the relevant moment by walking that
  log backward, instead of reading a season-specific copy. See "Entity history" below.

### `Team` becomes season-scoped, like `League`/`Tier`/`Group`

`Team` gains `season_id` (nullable — a team can exist, e.g. freshly registered, before being
placed into any season) and `team_identity_id` (non-null, generated once, carried forward
verbatim by every copy — the stable key across a team's season-copies). A rename
(`TeamService.update()`) only ever edits the row for the season it's called on — a past season's
row is a physically different entity, so history can't be rewritten by construction. No
lock/guard/business-rule needed, unlike the ruleset case below.

- `TeamService.resolveForSeason(teamId, targetSeason)`: given any historical row for a team's
  identity, returns (or creates) the row belonging to `targetSeason`. Used by team registration
  (a brand-new team, a team returning after a gap year, a second participation in an
  already-copied-forward season) **and** by `CopyForwardService`, one code path for all three. The
  club it carries over (`copy.setClub(source.getClub())`) is the very same club row — Club isn't
  season-scoped, see below.
- `CopyForwardService` builds a `teamBySourceId` map (mirrors `leagueBySourceId`/`groupBySourceId`)
  so a team referenced by two participations in one season is only cloned once per run.
- `GET /v1/teams` (no `seasonId`) collapses to **one row per identity — the latest season's copy**,
  for team pickers. `GET /v1/teams?seasonId=X` returns that season's rows only.
- **Authorization**: `TEAM_ADMIN` role scoping (`RoleAssignment.scopeId`) and the `team` nav area
  are keyed by `team_identity_id`, not row id — a grant survives copy-forward instead of needing
  re-granting every season.
- `Standing`'s team-name display needed no separate fix: it's downstream of
  `TeamParticipation.team`, which is now always season-correct via `resolveForSeason`.
- `TeamDto.clubName` is the club's name reconstructed *as of the team's own season* (see "Entity
  history" below) — a club rename after a team's season must not rewrite that team's historical
  display, even though the underlying `Club` row is shared and unversioned.

**`Club` stays a single row — see "Entity history" below**, not season-scoped like Team. An
earlier version of this doc gave Club the identical season-scoping treatment as Team; that was
reverted once player/club history landed as the general mechanism for exactly this class of
problem (a rename retroactively affecting historical display) without the row duplication.

### `Player` splits into `User` (auth) + `Player` (competitor) — `Player` itself is NOT season-scoped

`Player.dtfbId` used to be the Keycloak-login identity (DB-unique, tied to auth). That's split out
so a person's login identity and their competitor profile are separate concerns:

- New `User` entity (table `app_user` — `user` is a reserved SQL word): the permanent,
  season-independent login identity (`dtfbId`, email, first/last name). `RoleAssignment` now
  points here, not at `Player` — a role belongs to a login identity, not a competitor record.
- `Player` keeps `firstName`/`lastName`/`nationalId`/`internationalId`/etc. and gains a nullable
  `user_id` (a captain-entered athlete may never log in themselves). One row per real person,
  shared across every season they're rostered in — a rename is tracked via `entity_history`
  instead of duplicating the row.
- `UserRegistryService.currentUser(jwt)` replaces `PlayerRegistryService.currentPlayer` as the
  sole "who is logged in" resolver. `PlayerRegistryService` was renamed to `PlayerDirectoryService`
  and repurposed for player directory listing/search (name/nationalId, across the whole table —
  no per-season collapsing needed since there's only one row per person).
- The roster "add player" search and the role-granting search used to be the same endpoint
  (`RoleAdminService.playerSearch`). They're now genuinely different: role-granting searches
  `User` identities (`GET /v1/admin/auth/user-search`); rostering searches the `Player` directory
  (`GET /v1/team-participations/{id}/roster/search`, delegating to `PlayerDirectoryService.search`).
- `CopyForwardService.cloneRoster` carries the source roster's `Player` rows over verbatim (no
  cloning) — the same person is simply rostered again under the new season's `TeamParticipation`.
- **Explicitly out of scope**: a "register a brand-new athlete who's never logged in and has no
  prior row" endpoint. The roster "add player" flow has always assumed the athlete already exists
  as a `Player` row somewhere; this gap is real but deferred.

### Entity history: how Player/Club renames are tracked without duplicating the row

New package `history`: a generic, reusable mechanism (not one table per entity type).

- `EntityHistoryEntry` (table `entity_history`): `entityType` (`PLAYER`/`CLUB`), `entityId`
  (plain id, not a FK), `fieldName`, `oldValue`/`newValue` (as strings), `changedAt`,
  `changedByDtfbId`.
- `ChangeSet.forEntity(type, id).track(field, oldValue, newValue)...`: an update service builds
  one of these per call — `track` is a no-op when the value didn't actually change — and hands it
  to `EntityHistoryService.record(changes, changedByDtfbId)`.
- `PlayerService.update`/`ClubService.update` are the two write paths that call this — `PUT
  /v1/admin/players/{id}` and `PUT /v1/admin/clubs/{id}` (neither existed before this pass; both
  are new, minimal admin endpoints so the mechanism has something to exercise). `GET
  /v1/admin/players/{id}/history` / `GET /v1/admin/clubs/{id}/history` expose the raw log.
- **Point-in-time reconstruction is the actual point** — `EntityHistoryService.fieldsAsOf(type,
  id, fieldNames, asOf)` finds, for each field, the earliest change *after* `asOf` and returns its
  `oldValue` (the value still in effect at `asOf`); a field with no such entry falls back to the
  entity's current value. This only applies to an **ended** season (`Season.hasEnded()`, i.e.
  `endDate` is in the past) — a current/future season is still live, so it always shows today's
  value instead of freezing at some earlier point; otherwise a rename during an in-progress season
  would incorrectly hide itself from that season's own display. Two call sites use this today:
  - `RosterService.getRoster` — for an ended season's roster only — resolves each roster entry's
    player name as of `RosterEntry.addedAt` — the moment that player joined that roster — onto
    `RosterEntryDto.firstName`/`lastName`. A rename after the season ended doesn't rewrite how that
    old roster displays; a rename during a still-open season shows immediately.
  - `TeamService` — for a team whose season has ended only — resolves the team's club name as of
    the team's own `season.startDate` onto `TeamDto.clubName`, for the same reason on the
    Team→Club side.
- Player edit authorization is `@authz.isAdmin()` only (global admin) — `Player` has no
  club/region linkage today (`PlayerMapper` hardcodes `clubs: []`, a known gap), so there's no
  finer scope to gate against yet. Club edit reuses the existing `@authz.canManageClub(id)`.

### `LeagueRuleSet` edit lock (enforced, not just documented)

Reuses the only existing "season is closed" signal in the codebase, `Season.archivedAt != null`
(same one `docs/05` already uses for archived-subtree hiding). A `LeagueRuleSet` update is refused
(`409`, code `RULE_SET_LOCKED_BY_CLOSED_SEASON`) if it changes a **rule-affecting** field
(`playSystem`, points, `setsPerGame`, `pointsToWinSet`, `matchdayDecision`/`matchdayTarget`,
`sideSwitchAllowed`, roster size bounds, scheduling fields, `gamePlan`) and the ruleset is already
referenced by a League/Tier of a closed season. Renaming (`name`/`federationId`) stays allowed
regardless — cosmetic, doesn't affect any computed result. New `POST /v1/league-rule-sets/{id}/clone`
copies a (possibly locked) ruleset into a new, unreferenced, immediately-editable row — the
concrete way to "diverge," now backed by an actual endpoint instead of just a docs convention.

## What did NOT change

- `TeamParticipation`/`RosterEntry` still reference `Team`/`Player` by plain FK; `Team.club` still
  references `Club` by plain FK. Team's row is now guaranteed to be the correct season's copy;
  Player/Club are the same single row every season references, with history filling the gap.
- Copy-forward's overall shape (League→Tier→Group→Participation, optional roster) is unchanged;
  Team cloning slots into the same loop, Player/Club just come along by reference.
- No data-preserving migration was written for the live VPS test deployment — see
  `docs/13-vps-test-deployment.md` and the "reseed from scratch" note below.

## Migration note (VPS test deployment)

Given only ~7 testers and no real production history, the schema change was shipped as
schema-shape-only Flyway migrations (`V2__user_player_split.sql`, `V3__entity_history.sql`) with
**no** data-preserving backfill — the existing seed data is reseeded from scratch
(`00-bootstrap.sql` + `seed-region.sh`) rather than migrated row-by-row.
