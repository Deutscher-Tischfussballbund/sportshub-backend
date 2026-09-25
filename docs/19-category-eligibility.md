# Category eligibility — a category is an eligibility profile

> **Implemented (2026-09-25).** `Category` (Herren / Damen / Open, …) is no longer just a label: it
> is an **eligibility profile** — a set of independent, nullable criteria a player must pass to be
> rostered onto a team in one of its leagues. The first criterion is `Category.eligibleSide`
> (`LeagueSide` `MEN` / `WOMEN`, null = open), checked against `PlayerGender.leagueSide()`.
> `RosterService` enforces it on `addPlayer` **and** `submit`, via one `CategoryEligibility`
> component; a refusal is `409 PLAYER_NOT_ELIGIBLE`. Age bounds (U16/U19/seniors) are the planned
> next criterion on the same profile.
>
> See also: [18-player-gender.md](./18-player-gender.md) (the `PlayerGender` enum and its league
> side — this closes its open eligibility question), [09-league-model.md](./09-league-model.md)
> (`League.category`), [15-club-membership.md](./15-club-membership.md) (the other roster-add
> precondition, same error shape).
>
> Question raised: each league should restrict which genders may play in it — where does that
> setting live, and what does "female" mean for a divers player?
> Short answer: **on the league's `Category`, by league side — women's side admits `FEMALE` +
> `DIVERSE_WOMEN`, men's side `MALE` + `DIVERSE_MEN`.**

## Context

Doc 18 stored a divers player's league side (`DIVERSE_MEN` / `DIVERSE_WOMEN`) precisely so league
eligibility could filter on it, but nothing enforced anything: any player could be rostered into
any league. Every `League` already points at exactly one `Category`, and the categories in use
(Herren, Damen, Open) *are* the men's/women's split in all but enforcement. Future classes are
expected too — U16, U19, seniors — which restrict by age, and combine with gender ("U16 female").

## Options considered

Where the criteria live:

| | How | Verdict |
|---|-----|---------|
| **Category = eligibility profile (chosen)** | Nullable criteria on `Category`; every league of that category inherits them. "U16 female" is its own category row. | **Chosen.** A league runs under exactly one competition class in reality; configured once, reused by every league (and every season) of that class. New criteria are new nullable columns + one clause in `CategoryEligibility`. |
| Criteria on each `League` | `League.eligibleSide` etc.; `Category` stays a pure label | Rejected. Every league — and every copy-forwarded season — configured separately, no reuse; a "Damen" league allowing men becomes a representable contradiction. |
| Several categories per league, intersected | `League.category` becomes many-to-many (a gender class + an age class); a player must pass all | Rejected for now. Avoids combination rows, but turns pickers, copy-forward, and the league DTO many-to-many — only worth it if the number of combinations actually explodes. |

What the gender options mean:

| | Choices | Verdict |
|---|-----|---------|
| **By league side (chosen)** | All / men's side / women's side, via `PlayerGender.leagueSide()` | **Chosen.** Uses exactly the distinction doc 18 recorded for this purpose. |
| Exact values | All / male / female / divers; a female league admits `FEMALE` only | Rejected. A divers player could never play a gendered league, and the stored league side would go unused; a "divers-only" league has no real-world meaning. |

## Decision

- **`Category.eligibleSide`** — nullable `LeagueSide` (promoted from a nested enum of
  `PlayerGender` to top-level `player.LeagueSide`, wire values `men`/`women`), stored by name in
  `category.eligible_side` (`V10__category_eligible_side.sql`, plain varchar like V9). Null = open.
  On `CategoryDto`; category CRUD stays `@authz.isAdmin()`.
- **`CategoryEligibility.check(category, player)`** is the single evaluation point. A null category
  or null criterion passes. Reason (`IneligibilityReason`): `WRONG_SIDE`. The original design also
  blocked a player with no gender recorded (`GENDER_MISSING`); that reason was removed the same day
  once `Player.gender`/`birthYear`/`firstName`/`lastName` became mandatory (`NOT NULL`,
  `V11__mandatory_player_fields_unique_category_short_name.sql`, validated in
  `PlayerService.update`) — a gender is now always present, so the case can't arise.
- **Category identity:** `name` and `shortName` are mandatory, and `shortName` is unique,
  case-insensitively (`CategoryService` pre-checks → `409 CATEGORY_SHORT_NAME_TAKEN`; V11 adds the
  `UK_category_short_name` key, case-insensitive under the column's `utf8mb4_0900_ai_ci`). An age
  class like "U16 Damen" therefore needs its own distinct short name.
- **Enforced in `RosterService` on both `addPlayer` and `submit`.** Submit re-checks the whole
  active roster: copy-forward clones roster entries without re-validation (doc 09 §2, same as club
  membership in doc 15), and a category may be restricted after players were added. **No admin
  bypass** — eligibility is a rule, not the registration window that `actingAsAdmin` relaxes.
- **Error shape:** `409 PlayerNotEligibleError { code: "PLAYER_NOT_ELIGIBLE", message, players:
  [{ playerId, name, reason }] }` — every failing player at once (one on add, possibly several on
  submit). One stable code plus a per-player `reason`, so future criteria add reasons, not error
  types. The reason never carries the stored gender or side.
- **Seed:** `access-seed.sql` restricts `cat-herren` → `MEN`, `cat-damen` → `WOMEN`, leaves
  `cat-open` open; `player-test`/`player-club` (roster-fill players used by controller tests)
  gained `MALE`. The VPS `00-bootstrap.sql`'s single Herren category stays **open** on purpose:
  `seed-region.sh` rosters the mixed-gender filler pool onto every region's one league.
- **Frontend:** a new global-admin categories page (`admin/categories`, create/edit, eligibility
  picker — no category admin UI existed before); the roster editor translates the 409 per reason.

## What did NOT need to change

- `League`, `TeamParticipation`, placement, and copy-forward — eligibility is a roster concern
  (teams aren't gendered, players are), so placement stays unrestricted.
- The VPS data: V10 only adds a nullable column, every existing category stays open — no wipe, no
  behaviour change until an admin restricts one.
- `ClubMembership` enforcement (doc 15) — eligibility is an additional, independent precondition
  checked right after it.

## Watch for / triggers to revisit

- **Age classes (next criterion).** Add `minAge`/`maxAge` (nullable) to `Category`, checked in
  `CategoryEligibility` against `Player.birthYear` relative to the league's season (the check then
  needs the season — pass it in). New reason `AGE` (`birthYear` is already mandatory since V11, so no "missing" reason is needed). "U16 female" = a
  category with `eligibleSide = WOMEN, maxAge = 15`. Decide the reference date (season start year
  vs. a cut-off date) when building it.
- **Combination explosion.** If age × gender × other classes produce many near-duplicate category
  rows, reconsider the rejected many-categories-per-league option.
- **Information leak.** A `409` on add inherently tells a `team_admin` that a given player isn't on
  that side — the league side itself is never sent, but the refusal is
  a signal. Accepted: the alternative (silently hiding ineligible players from the roster search)
  leaks the same fact and is harder to explain.
- **Category delete.** The frontend offers no delete: nothing guards the `League → Category` FK, so
  deleting a used category would 500. Add a `CategoryDeletionBlockedException` (same pattern as
  `LeagueDeletionBlockedException`) before exposing delete.
- The error models aren't documented via `@ApiResponse` (same as `RosterSizeError` /
  `PlayerNotClubMemberError`); the frontend types the body locally.
