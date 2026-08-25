# Player↔Club membership

> **Implemented.** A player must be an active member of a club (`club_membership`, `joined_at` +
> soft-delete `left_at`) before `RosterService#addPlayer` will roster them onto one of that club's
> teams. Independent of team rosters — a player can belong to several clubs at once.

## The problem

Region/club player list pages always showed zero players, even with real data seeded: `Player.clubs`
was hardcoded to an empty list (`PlayerMapper`), because there was no real `Player ↔ Club`
relationship to derive it from — only the indirect, roster-mediated chain
`Player → RosterEntry → TeamParticipation → Team → Club`.

That chain has the causality backwards for what "club" is supposed to mean here. A player being
rostered onto a team is downstream of a real-world fact: **the player is a member of that team's
club.** Deriving "club" from "which teams happen to roster this player" conflates the two and
gives no way to represent "a club member not currently on any team's roster" or "a member of
several clubs."

## Decision: a first-class `ClubMembership`, enforced as a precondition for rostering

New entity `ClubMembership` (table `club_membership`): `player`, `club`, `joinedAt`, `leftAt`
(nullable — null = active). Same shape as `RosterEntry.addedAt`/`removedAt`: leaving a club is a
soft-delete, not a hard delete, so membership history is kept. A player may hold several active
memberships at once.

- `ClubMembershipService.join`/`leave` — `join` is idempotent (a no-op if already an active
  member). `POST`/`DELETE /v1/admin/clubs/{clubId}/members[/​{playerId}]`, gated by the existing
  `@authz.canManageClub(#clubId)`.
- **`RosterService#addPlayer` enforces it**: refuses (`409`, code `PLAYER_NOT_CLUB_MEMBER`) unless
  `ClubMembershipService.isActiveMember(playerId, team.club.id)`. A player joins a club first, then
  can be rostered onto one of its teams — not the other way around.
- `PlayerMapper.clubs` is no longer hardcoded — `PlayerDirectoryService`/`PlayerService` attach it
  via `ClubMembershipService.clubsByPlayerId` (one bulk query per request, not N+1).
- `GET /v1/admin/players` gained real server-side `clubId`/`regionId`/`q` filtering (via
  `ClubMembershipRepository`'s club-/federation-scoped active-member queries), replacing the
  client-side `clubIds.includes(...)` filtering `region-players.component.ts`/
  `club-players.component.ts` used to do over the unscoped global list — a related, already-flagged
  scaling gap folded into this pass since it's the same endpoint.
- Existing seed data backfilled: every (player, club) pair already implied by a seeded active
  `roster_entry`'s team gets a matching `club_membership` row (`access-seed.sql`), and
  `seed-region.sh` now joins its filler players to each region's club before rostering them (the VPS
  test-deployment seed has no static roster_entry rows to backfill from — rostering happens live via
  the real API there).

## What did NOT change

- Team rosters (`RosterEntry`) still reference `Player`/`TeamParticipation` exactly as before —
  membership is a new precondition layered in front of the existing add-player path, not a
  replacement for it.
- `CopyForwardService.cloneRoster` still carries roster entries over verbatim without re-checking
  membership — the player was already validated when originally added; copy-forward isn't a new
  "add player" action.
