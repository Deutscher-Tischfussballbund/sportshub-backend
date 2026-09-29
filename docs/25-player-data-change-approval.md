# Changing player master data — propose, confirm, approve

> **Decided 2026-09-28, not yet built.** A change to a player's master data travels through three
> stages: the **team captain may only propose** it, the **club admin** confirms the proposal or makes
> the change directly, and the **federation admin** approves it. Explicitly *not* the league admin —
> a league admin governs one league (a rookie or pub league, say), while the federation admin holds
> all rights within the federation. The same path governs renames, gender changes and team
> registration alike. Whether a rename applies **retroactively** is a federation-admin checkbox,
> shown with a hint that the affected person must consent. This is what lets the project stay out of
> the data-protection question entirely: the federation and the person decide, not the platform.
>
> See also: [02-role-concept.md](./02-role-concept.md) (the roles and scopes this uses, and §6.1's
> team self-service question), [03-authorization-model.md](./03-authorization-model.md) (how a
> scoped decision is computed), [14-team-player-versioning.md](./14-team-player-versioning.md)
> (`entity_history` — the change log a retroactive rename rewrites the *display* of, not the record),
> [01-competition-and-registration-model.md](./01-competition-and-registration-model.md) (the roster
> lifecycle the registration variant of this path runs through),
> [08-member-registration.md](./08-member-registration.md) (self-service, which this decision
> deliberately does not open).
>
> Question raised: may a team captain rename a player, or does that have to sit higher up?
> Short answer: **captain proposes, club admin confirms, federation admin approves — and
> retroactivity is a consented checkbox, not a platform policy.**

## Context

SPO-49 ("allow renaming players at club/team level") had been blocked since 2026-08-31 on one
question: which role may rename. The original sketch put it at team level.

The argument against that is concrete: a player can compete in the Bundesliga *and* turn out for a
fun team because they enjoy the game. The captain of the fun team would then be able to rewrite the
name of a Bundesliga player — a name that is shown on live screens during a tournament, with no way
for the player to intervene. A captain is, in the words of the meeting, whoever happens to be
logged in.

What happens today, in both federations represented in the meeting (Berlin and Schleswig-Holstein):
the player approaches the club, the club has the federation change the name — by e-mail, with a new
player list. There is no club-admin rename surface in the Sports Manager at all. The decision below
is a digitization of that existing path, not a new policy.

The retroactivity half comes from a separate thread. B-2026-09-14-5 had already parked a
"retroactive" checkbox for regional admins in version 1.1 (SPO-83). The meeting rediscovered it as
the answer to a question it had been carrying since 2026-09-15 — whether storing and traversing a
name history needs a data-protection sign-off from the board. It does not, *if* the decision is
delegated: the federation admin asks the person and ticks the box. The board letter was dropped as
a result.

## Options considered

| | How | Verdict |
|---|-----|---------|
| Team captain renames directly | Cheapest path; the captain is closest to the player | Rejected — a fun-league captain could rename a Bundesliga player mid-tournament, with no consent and no review |
| Club admin renames directly, no approval | One stage; the club knows its members | Rejected as the *final* stage — a name that appears publicly should get a second pair of eyes. Kept as a middle stage: the club admin may edit and confirm in one step, but a federation approval still follows |
| **Captain proposes → club admin confirms/edits → federation admin approves** | Mirrors the existing e-mail process and the team-registration flow | **Chosen** |
| League admin as final approver | The role closest to the competition the name shows up in | Rejected — a league admin controls only one league; a player appears in several, up to the Bundesliga. The federation admin is the role with authority over all of them |
| Platform-wide retroactivity policy | The Sports Hub decides whether history is rewritten | Rejected — it would make the project the data-protection decision-maker. Delegated to the federation admin plus the person's consent instead |

## Decision

- **Now:** the team captain can only *propose* a change to player master data — never write it.
- **Now:** the club admin confirms a proposal, or makes the change directly; either way a federation
  approval follows.
- **Now:** the **federation admin** is the approving role. Not the league admin.
- **Now:** the same three stages govern renames, gender changes and team registration — a captain
  registers the team and its players, the club admin passes it on or confirms, the federation admin
  decides whether the team is registered as submitted. Only then does it appear in the data.
- **Now:** whether a rename applies retroactively is a **checkbox on the federation admin's dialog**,
  and the proposal may already carry the request through from the captain and club admin.
- **Now:** that dialog shows a **hint that the affected person must be involved and consent**.
- **Now:** with the checkbox in place, the project needs **no data-protection sign-off** from the
  board or the data protection officer for the name history. Without the checkbox, that reasoning is
  a promise rather than a mechanism.
- **Consequence for history:** if the change is not retroactive, past seasons keep displaying the
  old name and the player record stays the same record — the person consented to exactly that. If it
  is retroactive, the display of the whole history follows the new name. The `entity_history` record
  itself (doc 14) is unaffected either way.

## Watch for / triggers to revisit

- **Three stages become a bottleneck.** The normal case is a marriage, not an incident. If federation
  admins are slow or unreachable, renames will queue and captains will work around the system.
- **A club with no reachable federation admin.** The path has no timeout and no escalation.
- **Retroactivity is requested for something other than a rename** — a gender change, a club
  correction. The checkbox is currently scoped to names only.
- **A player wants to act for themselves.** Deliberately not opened here; that is
  [08-member-registration.md](./08-member-registration.md) and SPO-85.
- **`SPO-83` is still in version 1.1.** As long as the checkbox is unbuilt, the data-protection
  reasoning above has no implementation behind it. Revisit the roadmap placement.

## Open questions

- Is the middle stage a real state (`ChangeRequest` with a status) or just a notification? The
  meeting described a workflow, not a schema.
- Does a rejected proposal keep a record and a reason?
- Does the approval path reuse the roster approval surface (`SPO-51`, team registration) or get its
  own?
