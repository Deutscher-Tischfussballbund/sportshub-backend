# Authorization: ACLs vs. scoped RBAC — design discussion

> Status: **discussion / decision record** (no code change). Companion to
> [`03-authorization-model.md`](./03-authorization-model.md), which is the source of truth for the
> model we actually run.
>
> Question raised: *would per-object ACLs be a better approach than what we have?*
> Short answer: **no for the current requirements** — and if fine-grained authz is ever needed,
> the right tool is a relationship engine (ReBAC), not classic ACLs.

## 1. What we actually have

What we built is **not** flat RBAC ("you have role X → you can do action X everywhere"). It is
**scoped, hierarchical RBAC**: a grant is a `(role, scopeNode)` pair (rows in `role_assignment`),
and a decision is computed by **walking the resource up the domain tree** to a node where the caller
holds a matching role.

Example (`@authz.canOrganizeMatch`): `match → matchday → round → pool → stage → discipline → competition`,
then check *"region admin of that competition's region, or `competition_organizer` of that competition."*

Structurally this is a **hand-rolled, domain-specific ReBAC** (relationship-based access control):
a permission is a *function* of `(grant @ node)` and `(the resource's position in the graph)`. It is
never stored per object.

## 2. The real menu — three options, not two

| | How a decision is made | Grants stored as |
|---|---|---|
| **(a) Current** — computed hierarchical RBAC | walk resource → scope in code, check role | a handful of `role_assignment` rows |
| **(b) Classic ACLs** (e.g. Spring Security ACL) | look up an access list *attached to that object* | one+ ACL row **per object**, with parent-inheritance |
| **(c) ReBAC / policy engine** (OpenFGA, SpiceDB, Cedar/OPA) | traverse stored relationship tuples | relationship tuples + a schema |

The user said "ACLs"; the distinction between (b) and (c) is the crux, because they have opposite
fit here.

## 3. Why classic ACLs (b) are a poor fit

Deciding question: **are permissions derived from structure, or are they per-object facts?**
Ours are almost entirely *structural*. Given that:

- **Row explosion.** Nobody grants "edit *this* match." They grant "region_admin of Bavaria," which
  cascades to thousands of teams/competitions/matches/match-competitions. As ACLs you would either materialize an
  entry on every descendant object (millions of rows, re-propagated on every insert and every *move*
  in the tree), or implement **ACL inheritance up the parent chain** — which is *exactly the tree walk
  we already do*, only stored in `acl_*` tables instead of computed. Heavy schema, zero gain.
- **Our grant cardinality is tiny on purpose.** The scope model is the compression: a few
  `role_assignment` rows cover the whole federation. ACLs discard that compression.
- **`COMPETITION_ORGANIZER` already gives per-competition granularity** without object ACLs — because "competition" is a
  first-class scope node.

So for the current requirements, classic ACLs would be a step backwards.

## 4. When this changes — the genuine triggers

ACLs/ReBAC start to win when you get **per-object facts that are not derivable from the tree**:

- **Ad-hoc sharing / exceptions** — "co-organizer added to just *this* tournament," "guest referee may
  edit *this one* match," "this club may see *that* region's draw."
- **Private / visible-to-a-list objects** — partly the upcoming **read tier**: "is this object
  world-readable, or visible only to these principals?" That is closer to a per-object *visibility*
  fact than a role decision. (A simple `public` boolean handles the common case without ACL machinery.)
- **Many independent services** each needing the same answers → a central Zanzibar-style engine
  (OpenFGA/SpiceDB) earns its keep as shared infra. Today there is one backend whose `@authz` component
  already *is* the central decision point, consumed by every frontend via `/auth/me/areas`.

If that day comes, prefer **(c) a relationship engine, not (b) Spring ACL** — it generalizes what we
hand-rolled (and makes *"why can X do Y?"* an introspectable data query) instead of fighting the
hierarchy.

## 5. Recommendation

**Keep the current model.** It is the correct shape for *permission = f(role @ scope, resource
position)*. The real pain points are **not** solved by ACLs:

1. **Per-entity resolver boilerplate** (8 `canOrganize*` + `CompetitionResolver`). Cheap,
   ACL-orthogonal fix: **denormalize a `regionId`/`eventId` onto competition rows** so resolution is an
   O(1) column read instead of an eager walk (also removes the N+1 risk).
2. **Auditability** ("explain this decision") — that is the ReBAC argument, not the ACL one.
3. Whatever actually drives the need: keep the **`resolve(resource) → scope` then `check(role)` seam**
   clean (we have it). That seam is exactly what makes a future swap to OpenFGA feasible without
   rewriting controllers.

## 6. Decision

- **Now:** stay on scoped hierarchical RBAC (option a). Do **not** introduce classic per-object ACLs.
- **Watch for** the section-4 triggers (per-object sharing, private/visibility, multi-service scale).
- **If triggered:** evaluate a ReBAC engine (option c) as an *evolution* of the current resolver seam,
  not Spring ACL.
- **Cheap win available regardless:** denormalized scope keys on competition rows to flatten the walk.

### Open input needed

What is driving the question — resolver boilerplate, a looming need for per-object sharing / private
competitions, the read/public tier specifically, or multi-service scale? Each points to a different one of
the three options; record the answer here when known.
