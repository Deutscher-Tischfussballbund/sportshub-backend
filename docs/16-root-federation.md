# Root federation (DTFB) and narrow cross-federation authority

> **Implemented.** `Federation` becomes a general self-referencing tree (`parentFederation`, nullable
> — the top has none). Exactly one federation is root (seeded `fed-dtfb`, "DTFB"); every other
> federation is a sub-federation under it. A club may field any number of teams; a club may field
> at most one team per root-level league. The root federation's admin gets narrowly-scoped authority
> over root-level teams and their rosters, for any club in any sub-federation — not general authority
> over a sub-federation's own affairs.

## The problem

DTFB runs national-level leagues (e.g. the Bundesliga) using clubs that belong to a Landesverband
(a sub-federation). A club may field a team in a Bundesliga season and a separate team in its own
Landesverband's regional season — independently, possibly sharing players, possibly not. Before this
change, `Federation` was completely flat: no notion of a national body existed as a `Federation` row
at all (only implicitly, as the `GLOBAL`/`ADMIN` scope). Nothing modeled "DTFB" or the
root/sub-federation relationship.

## What did NOT need to change

`Club.federationId`, `Team.club`/`Team.season`, and `Season.federation` already support the core
scenario with **zero schema change**: `TeamService.create` never cross-validates a team's club against
its season's federation, so a club's home federation and a team's season's federation can already
differ freely. A club fielding a Bundesliga team and a regional team is just two `Team` rows sharing
one `clubId`, each with its own `seasonId` — this already worked.

## Data model: a general self-referencing tree, not a fixed two levels

`Federation` gained `parentFederation` (nullable `@ManyToOne`, self-referencing). `isRoot()` is
derived (`parentFederation == null`), not a separate persisted flag — the tree can grow to any depth
later (e.g. a Bezirk under a Landesverband) without another migration; today's real data is just
two levels (DTFB, then the existing Landesverbände).

- `FederationService.create`: a new federation with no explicit `parentFederationId` attaches under
  the existing root automatically (or becomes the root itself if none exists yet) — callers don't
  need to know/pass DTFB's id to create an ordinary sub-federation.
- `FederationService.update`: a `null` `parentFederationId` means "unchanged" (most callers save the
  whole object back without touching this field) — re-parenting is an explicit move, not something
  an omitted field triggers. A cycle guard (`requireNoCycle`) rejects a federation becoming its own
  ancestor.
- `FederationRepository.findByParentFederationIsNull()` returns a `List`, not an `Optional` — an
  unexpected extra rootless row (e.g. test fixtures that construct a bare `Federation()` directly)
  shouldn't hard-fail the lookup with `IncorrectResultSizeDataAccessException`; callers take the
  first one.
- Migration `V5__federation_hierarchy.sql`: adds `parent_federation_id`, inserts `fed-dtfb`, points
  every existing federation at it.

## New business rule: one team per club per root-level league

Regional leagues stay fully unrestricted (a club may field several teams there, as before). A club
may register only one team per ROOT-level league (`TeamParticipationService
#requireSingleRootLeagueTeamPerClub`, checked on create only) — it can still field one team each in
several *different* root-level leagues (e.g. men's and women's Bundesliga).

## Authorization: narrow, not broad

The gap: `AuthorizationService`'s `TEAM` scope (and the private `canRepresent` helper) resolved a
team's manageable region via **the club's home federation** only, never the team's own season's
federation — so a Bavarian club's Bundesliga team resolved to Bayern, never to DTFB, making it
impossible for a DTFB admin to manage it at all (and impossible to even create it, since team
creation was gated purely by `canManageClub`).

**Decision (a broader "region admin of the root federation manages everything everywhere" design was
considered and rejected)**: DTFB's admin gets elevated authority ONLY over teams whose own season
belongs to a federation they administer — never over a club's profile/membership, and never over a
sub-federation's own seasons/leagues/tiers/rule sets/roster confirmations, even for clubs that also
field a root-level team. Running the Bundesliga doesn't imply unilateral authority over a
Landesverband's own regional competition.

Implementation — one additional disjunct (`viaOwnLeagueFederation`, in a shared `canManageTeamRow`
helper) in exactly the two places that resolved team authority via the club's home federation:

- `canManageScope`'s `TEAM` case (backs `canManageTeam`, `canConfirmRoster`, TEAM-scope grant/revoke).
- The private `canRepresent` helper (backs `canEditRoster`, `canReportMatchDay`,
  `canRegisterForLeague`).

Plus a new `canCreateTeam(TeamDto)` for `POST /v1/teams` (no existing row to resolve authority from):
authorized either via the target club (the normal case) OR via the target season's own federation.

This check is **hierarchy-depth-independent** — it only ever compares two specific federations (the
team's own season's federation, and the club's home federation), never walks an ancestor chain, so it
works identically regardless of how deep the tree ever grows.

**Deliberately untouched**: `canManageClub`, `ClubMembershipController` (`join`/`leave` — DTFB rosters
*existing* club members onto a root-level team, it does not enroll new members into a club it doesn't
otherwise administer), `ClubController` CRUD, and every `canManageSeason`/`canManageTier`/
`canManageRuleSet(ById)`/`canManageLocation`/`canOrganize*`/`canManageParticipation`/
`canViewPendingApprovals` call for a federation's own entities (these already correctly resolve via
that federation's own id).

## Nav: the root federation still "behaves like a normal federation"

`AreaService`'s `REGION` case normally expands to the clubs owned by that one federation
(`clubRepository.findByFederationId`). The root federation has none of its own (Bundesliga teams
reuse sub-federation clubs), so its `REGION` role instead expands to every club in the system
(`f.isRoot() ? clubRepository.findAll() : ...`) — nav/read only, distinct from the narrow write
authority above. The nav entry itself stays a plain `"region"` `AreaDto` (not the `"admin"` shape),
which is what satisfies "behaves like a normal federation" for the frontend.

## Frontend

- `region-clubs.component.ts` / `region-players.component.ts`: skip the client-side
  `c.regionId === regionId` filter when the active region is root (looked up via
  the `root` field of `FederationService.getAllFederations()` -- originally via the
  `/v1/regions` alias, removed in SPO-67). The player fetch additionally falls back to the
  unscoped `players()` call when root, since `activePlayerIdsForRegion` matches on
  `club.federationId` and no club's home federation is ever the root.
- `create-team-dialog.component.ts` ("New team" from a club's own page): the season picker now
  includes root-federation seasons alongside the club's own region's seasons, so any club can pick
  a Bundesliga season, not only clubs whose region happens to match.
