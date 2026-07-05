# Member self-service registration — design discussion

> Status: **discussion / decision record** (no code change; nothing built). The flow is
> **deferred** — it is blocked on the [§7 open question](#7-the-blocking-question) below.
>
> Scope: **member account provisioning** — a person claiming their DTFB-ID and getting a login
> account. This is *not* team/roster registration (that is [`01-competition-and-registration-model.md`](./01-competition-and-registration-model.md),
> `TeamParticipation` + `RosterEntry`). The frontend wizard exists but is parked, still wired to the
> old NestJS API.
>
> Short answer: **if we build it, use the "inverted seam" — Keycloak creates the user, the backend
> only *attests* entitlement — never give the backend Keycloak `manage-users`.** But first decide
> whether an authoritative member directory is imported at all (§7).

## 1. What "register" means here

The public self-service wizard (`+register/*`): search your member record → confirm → create a login
account **claiming your DTFB-ID** → success. Zero roles are granted; the outcome is an unprivileged
member account bound to an existing `dtfb_id`.

The DTFB-ID / member portal surface is sketched in [`03-authorization-model.md`](./03-authorization-model.md) §5
(the ":4500" column) with the single undecided row *"Register a DTFB-ID — ⬜ / 🟨"*. This doc is that
row, elaborated.

## 2. The constraints (firm facts)

These bound every possible design:

- **sportshub is a pure OAuth2 resource server** — it validates JWTs and has *zero* Keycloak
  dependency. That decoupling is a deliberate win of the migration.
- **No member directory today.** `Player` rows are created only by lazy auto-provision on first login
  (sparse JWT claims `dtfb_id`/`email`/`given_name`/`family_name`), the bootstrap admin, and the dev
  seed. There is no bulk membership import (`ImportPlayer` is match-lineup data, not a membership feed);
  `PlayerController`/`PlayerAdminController` are read-only.
- **No emails stored** — `Player.email` is nullable and only ever set from the JWT on login.
  ⇒ email-verification-code proof is **permanently off the table**.
- **Only `birthYear`** (nullable `Integer`), not full DOB — low-entropy, brute-forceable.
- **No registration endpoint** — no `RegistrationController`/`RegisterDto`/`RegisterResponseDto` exist.

## 3. The seam problem — who may create a user

A "claim your DTFB-ID → get a login" flow must, at some point, create a Keycloak user. **Who holds
that privilege** is the whole design question.

The obvious approach — and what the **old NestJS backend did** — is to give the backend a Keycloak
service account with `manage-users` and have it call the Keycloak Admin REST API to create the user
(the `KEYCLOAK_REQUEST_FAILED` path). This **reverses the migration's decoupling** and creates a large
blast radius: a compromised internet-facing data backend could then create/delete/modify/password-reset
*any* user in the realm.

## 4. The inverted seam

**Invert who acts.** Keycloak stays the only thing that can create a user; the backend is demoted to a
**passive attestor** that merely signs a statement *"this requester has proven entitlement to
dtfb_id X."* Trust flows Keycloak → *verify the backend's signature*, instead of backend → *wield
Keycloak admin creds*.

### Flow

1. **Anonymous search** — unprivileged public endpoint finds a member record; returns minimal/masked
   info (enough to say "a record exists," not enough to dump the directory).
2. **Prove it** — the unprivileged `verify-claim` step. With no emails, proof is weak: **`birthYear` +
   strict rate-limit + lockout** (the old system's `DTFB_ID_LOCKED` / `retryAfter`). This is the weak
   link, and it is *orthogonal* to the seam (see §5).
3. **Backend mints a claim token — it does NOT create a user.** On successful proof it returns a
   **signed, short-TTL, single-use, dtfb_id-bound** token. It carries *no privilege* — it asserts
   identity entitlement only. Anatomy: signed with the backend's key (so Keycloak can verify), a `jti`
   for single-use, a few-minutes expiry, `dtfb_id` bound in the payload.
4. **Hand off to Keycloak-native registration.** The user completes Keycloak's own signup
   (username/password). Keycloak — the correct authority — creates the account. The claim token rides
   along.
5. **Bind the dtfb_id into the account.** A **Keycloak custom authenticator / required-action (SPI)**
   validates the token (signature + TTL + single-use) and stamps `dtfb_id` as a user attribute; a
   standard protocol mapper then puts `dtfb_id` into every future JWT.
6. **First login reconciles as usual.** When the new user hits sportshub, `PlayerRegistryService` sees
   the `dtfb_id` claim and links to the existing `Player` — the same lazy path every login already
   uses. No new privileged write path in the backend.

### Why it's better — blast radius

| | Backend holds Keycloak admin creds | Inverted seam |
|---|---|---|
| Backend holds | `manage-users` service account | a **signing key** only |
| Compromise yields | full IdP takeover (create/delete/reset any user, escalate) | ability to forge **identity claims** for *unclaimed* members |
| Keycloak coupling in backend | re-introduced | stays zero |

Inverting does not weaken the proof — the claim is only as strong as step 2 either way — but a backend
breach can no longer own the identity provider. The **hard rule** (§6) caps it further: worst case is
impersonating an unprivileged, unclaimed member, never privilege escalation.

## 5. The invite variant — same primitive, stronger issuance

The claim token is the *same primitive* whether earned by weak self-proof or handed out by an admin:

- **Issuance** varies in strength: weak (`birthYear` self-proof) **or** strong (an admin/club, on an
  authenticated action, mints an invite token for a member).
- **Redemption** is identical (Keycloak consumes the signed token → binds `dtfb_id`).

Build the redemption half once; **ship invite-only first** (strong proof, no directory-scraping risk),
then add the weak self-proof path later with no redesign.

## 6. Hard rule (non-negotiable for any design)

Self-registration mints an **unprivileged** member only. A `dtfb_id` that already holds roles
**must not be self-claimable on weak proof** — cap the blast radius to identity, never privilege
escalation. A privileged identity's claim must go through a strong path (admin invite, §5).

## 7. The blocking question

Everything above **assumes the claim model** — a pre-existing authoritative directory record to claim.
That assumption is not yet settled:

> **Is an authoritative DTFB member directory imported into sportshub (with which fields —
> `birthYear`? `nationalId`?), or is Keycloak the sole identity source?**

- **Directory imported** → the claim model holds; proof strength depends on which fields land; the
  inverted seam (§4) applies; `PlayerRegistryService` should *link* to the imported row on first login
  rather than create a sparse one.
- **Keycloak is sole identity source** → there is no record to claim; the inverted seam is moot. Design
  **Keycloak-native signup** + a separate decision on how `dtfb_id` is assigned downstream.

Record the answer here when known — it picks both the proof mechanism and the architecture.

## 8. Costs & open sub-questions (if we build the inverted seam)

- **It moves Keycloak coupling, doesn't eliminate it** — needs a **custom Keycloak SPI** (Java, deployed
  as a JAR into Keycloak). Better *isolation* (Keycloak-specific code lives in Keycloak, not the data
  backend), but real, Keycloak-version-sensitive work.
- **Token transport through the redirect** — query param (short-lived but URL-logged) vs. an opaque
  reference token Keycloak resolves via a callback (keeps payload off the URL, re-adds one
  Keycloak→backend call).
- **Single-use needs shared state** — a `jti` blocklist somewhere (a backend `redeem` endpoint), or
  accept the weaker TTL-only guarantee.
- **Proof strength** for the self-service path is unsolved — `birthYear` is brute-forceable; anything
  strong wants the invite variant (§5).

## 9. Decision

- **Deferred.** Do not build until §7 is answered.
- **If built:** use the **inverted seam** (§4) — backend attests, Keycloak creates. **Never** give the
  backend `manage-users`.
- **Sequencing:** ship **invite-only** redemption first (§5); add weak self-proof later behind rate-limit
  + lockout.
- **Invariant:** the §6 hard rule holds regardless of path.

Related: [`01-competition-and-registration-model.md`](./01-competition-and-registration-model.md),
[`03-authorization-model.md`](./03-authorization-model.md),
[`07-prod-keycloak-and-admin-bootstrap.md`](./07-prod-keycloak-and-admin-bootstrap.md).
