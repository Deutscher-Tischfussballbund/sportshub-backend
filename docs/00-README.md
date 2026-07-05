# Design docs

Reference and decision records for sportshub-backend. Read roughly in number order —
domain model first, then authorization, then policies and ops. Living **model** docs are the
source of truth for what the code does; **decision** docs record why a choice was made.

| # | Doc | Kind | What it covers |
|---|-----|------|----------------|
| 01 | [Competition & registration model](./01-competition-and-registration-model.md) | model | The `Season → Competition → Discipline → Stage → Pool → Round → MatchDay` tree; placement (TeamParticipation) vs. roster (RosterEntry) and the roster lifecycle. |
| 02 | [Role concept](./02-role-concept.md) | model | The roles and scopes (admin / region / club / team / competition) and what each may do. |
| 03 | [Authorization model](./03-authorization-model.md) | model | The canonical scoped, hierarchical RBAC we run — how a decision is computed by walking a resource up the domain tree to a matching grant. |
| 04 | [Authorization: ACLs vs. scoped RBAC](./04-authorization-acls-vs-scoped-rbac.md) | decision | Why per-object ACLs are the wrong fit and, if fine-grained authz is ever needed, why a ReBAC engine (not Spring ACL) is the evolution. |
| 05 | [Season archiving & deletion](./05-season-archiving-and-deletion.md) | decision | State-aware delete + archive: hard-delete a result-free season, refuse (409) one with results and archive instead. |
| 06 | [Frontend topology: team portal vs. admin app](./06-frontend-team-portal-split.md) | decision | Build the team roster/results surface in the admin app now (extraction-ready), not as a separate portal yet; the clean seam for a future split. |
| 07 | [Prod Keycloak & admin bootstrap](./07-prod-keycloak-and-admin-bootstrap.md) | ops | Keycloak wiring and the config-driven bootstrap admin for production. |
| 08 | [Member self-service registration](./08-member-registration.md) | decision | Deferred DTFB-ID account-claim flow: the "inverted seam" (backend attests, Keycloak creates the user), the invite variant, and the blocking directory-vs-Keycloak question. |

Numbers are a reading/topic order, not a chronology — renumber on the rare insert.
