# Venue ownership — no nationwide venues

> **Decided 2026-09-28.** A venue never becomes visible nationwide. Every federation maintains its
> own venues, and **duplicates are accepted**: the DTFB keeps its own locations for its big events,
> the state federations keep their home venues for league play, and the same physical hall may exist
> as a row in several federations. DTFB venues are **not** readable in sub-federations and are not
> reused from there. This settles the second half of the region-venues work shipped on 2026-09-27
> (SPO-77, doc 12).
>
> See also: [16-root-federation.md](./16-root-federation.md) (the root federation behaves like a
> normal federation and holds no authority over a sub-federation's own affairs — this decision keeps
> that property), [12-matchday-scheduling.md](./12-matchday-scheduling.md) §4 (venue assignment per
> fixture and the fixed kick-off slots that carry a `locationId`),
> [09-league-model.md](./09-league-model.md) (the league tree the selection lists hang off).
>
> Question raised: how does a venue become visible nationwide — a Bundesliga host venue, or a
> training base several federations use?
> Short answer: **it doesn't. Every federation maintains its own, duplicates and all.**

## Context

A venue belongs to a federation (B-2026-08-17-4) and is maintained on the federation's own "Venues"
page since SPO-77; the selection lists in the schedule only offer the venues of that federation.
Two incompatible notions of "nationwide" existed side by side:

1. A venue on the **DTFB root federation** is visible only to the DTFB (Bundesliga), not in the
   state federations — consistent with doc 16, and therefore not nationwide at all.
2. "Nationwide" technically meant a venue with **no federation**, which only a global admin can
   create through the API. There is no UI for it.

Who actually uses the feature: the federations with decentralized leagues — Hamburg and Berlin
heavily (Berlin even distinguishes a home venue from a non-smoking home venue), possibly Saarland.
Where leagues are centralized, and that is most federal states, venues barely matter; the DTFB level
does not need the feature at all for its own competitions today.

## Options considered

| | How | Verdict |
|---|-----|---------|
| Root-federation venues count as nationwide | Venues on `fed-dtfb` become readable (read-only) in every federation, maintained by the root admins | Rejected — it is the only option that matches doc 16 on paper, but nobody wants to *read* them: a hall has to be booked before it can be played in, and whoever books it can enter it themselves |
| "Nationwide" checkbox for global admins | Keep the federation-less venue, give it a dialog, restrict it to global admins | Rejected — it preserves two competing notions of a venue and puts the DTFB's own venue maintenance behind a global-admin role |
| **No nationwide venues** | Every federation creates its own; duplicates accepted | **Chosen** |

## Decision

- **Now:** every federation maintains its own venues. The DTFB maintains the locations for its big
  events itself; state federations maintain their league home venues.
- **Now:** **duplicates are expected and accepted.** The same hall may exist once per federation,
  and the records will not be complete.
- **Now:** DTFB venues are **not** visible in other federations and are not offered for reuse.
- **Rationale for accepting duplicates:** the alternative couples federations to each other. If
  Saarland hosts a Bundesliga weekend in a venue it never plays in itself, the DTFB could not create
  the fixture until Saarland had entered that venue. Nobody should have to wait on anybody else for
  a row in a table — and league venues are in any case different places from the ones that host a
  national tournament or a cup final, which need far more space.

## Watch for / triggers to revisit

- **A shared training base used by several federations for regular league play** — the one case
  where duplicate rows would drift apart (opening hours, table count, address corrections) rather
  than just being redundant.
- **League fixtures appearing in the DTFB calendar.** If the calendar shows state-league match days
  nationwide, each of them needs a venue, and duplicates become publicly visible as duplicates.
- **A federation asking to import another's venue list** — the read-only variant rejected above,
  arriving as a feature request instead of a model change.

## Open questions

- **May club or league admins create venues, or only federation admins?** The second half of SPO-25,
  untouched by this decision.
- Is there any deduplication at all — a warning on an identical name plus address within a
  federation, say — or is a duplicate simply a duplicate?
