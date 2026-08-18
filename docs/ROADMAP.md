# URL Shortener — Roadmap & Progress

> Single source of truth for **what we're building, in what order, and what's done.**
> Companion docs: [DESIGN.md](./DESIGN.md) (API + schema), [DECISIONS.md](./DECISIONS.md) (decisions + risks).

_Last updated: 2026-08-18_

---

## Vision

A backend service that turns a long URL into a short code and redirects visitors
from the short code back to the original URL.

## Scope

This is a **learning project**. We favour clean, understandable code over feature
completeness, and we build in small, independently testable increments.

### MVP (v1) — locked

- **Audience:** public + anonymous. No accounts, no auth.
- **Codes:** auto-generated, non-sequential (Sqids over a DB sequence — see [DECISIONS.md](./DECISIONS.md)).
- **Two capabilities only:**
  1. Create a short link from a long URL.
  2. Redirect a short code to its original URL (HTTP 302).

### Out of scope for v1 (candidate v2)

- User accounts / authentication / per-user link ownership
- Custom aliases (user-chosen codes)
- Click analytics (counts, timestamps, referrers)
- Link expiry & deletion
- Rate limiting / abuse protection

---

## Features & user stories

| ID  | Feature        | User story                                                                 | Priority |
|-----|----------------|----------------------------------------------------------------------------|----------|
| F1  | Create link    | As a user, I can submit a long URL and get a short code back.              | MVP      |
| F2  | Redirect       | As a visitor, when I open a short code I am redirected to the original URL.| MVP      |
| F3  | Validation     | As a user, if I submit an invalid/oversized URL I get a clear 400 error.   | MVP      |
| F4  | Custom alias   | As a user, I can choose my own short code.                                 | v2       |
| F5  | Analytics      | As an owner, I can see how many times my link was visited.                 | v2       |
| F6  | Expiry/delete  | As an owner, I can delete a link or have it expire.                        | v2       |
| F7  | Accounts/auth  | As a user, I can sign in and manage the links I own.                       | v2       |

---

## Build order (MVP)

Each step is small enough to complete and test on its own.

1. **Migration applies, verified by an integration test** — a Testcontainers-backed
   `@SpringBootTest` boots the context, Liquibase runs, and the test asserts `TB_URL_ALIAS`
   exists. No manual app run. (TDD: see it red, then green — see [WORKFLOW.md](./WORKFLOW.md).)
2. **`TB_URL_ALIAS` table live** — enable the changeset; entity + repository in place.
   Per [D10](./DECISIONS.md), the alias is **stored** in `URL_ALIAS` (unique index); lookups use it.
3. **Create endpoint** — `POST /api/v1/links` computes `encode(id)` (id from the sequence, before
   insert) and persists `id` + `LONG_URL` + `URL_ALIAS` in one INSERT; returns the alias.
4. **Redirect endpoint** — `GET /{alias}` does `findByUrlAlias` → 302 to the long URL, 404 if unknown.
5. **Error-handling polish** — consistent JSON for 400 (bad input) and 404 (unknown code).

---

## Status board

Reflects the actual state of the code as of the date above.

### Done
- Spring Boot 4.1 / Java 21 project skeleton (`pom.xml`, `compose.yaml` Postgres).
- Dependencies wired: Web MVC, Data JPA, Validation, Liquibase, Lombok, Testcontainers.
- Request DTO `CreateUrlAliasRequestDto` with `@NotBlank` / `@Size(2048)` / `@URL` validation.
- `GlobalExceptionHandler` mapping validation failures → 400 with field-error JSON.
- Controller test covering valid / blank / invalid / oversized URL cases.
- **TDD/Testcontainers workflow** established + documented ([WORKFLOW.md](./WORKFLOW.md)).
- **Build-order step 1 (migration applies) — green.** Changeset enabled and fixed
  (`TIMESTAMPTZ`, commas, `URL_ALIAS` widened to `VARCHAR(64)` — see [R3](./DECISIONS.md));
  `UrlShortenerApplicationTests` boots a Testcontainers Postgres, applies Liquibase, and asserts
  `TB_URL_ALIAS` exists. 2 tests pass.
- **Containerised backend** for frontend devs: multi-stage `Dockerfile` + `compose.app.yaml`.
  Verified: `docker compose -f compose.app.yaml up --build` boots DB + app, migration runs,
  API answers on `localhost:8080`. (See [DECISIONS.md](./DECISIONS.md) D8.)
- **Build-order step 2 (`TB_URL_ALIAS` live) — green.** Entity + repository in place and proven
  by `UrlAliasRepositoryTest` (7 tests, Testcontainers, no `@Transactional` so every assertion is
  against committed data):
  - The DB populates `CREATED_AT` (`@Generated(INSERT)`), asserted within a 5s window to absorb
    JVM-vs-container clock skew.
  - `findByUrlAlias` round-trips `urlAlias` + `longUrl`, and returns empty for an unknown alias —
    the 404 branch step 4 will need.
  - `UQ_URL_ALIAS` rejects a duplicate alias; `LONG_URL` is confirmed *not* unique (two aliases may
    point at the same URL — F1 depends on this).
  - Both `NOT NULL` columns rejected. Note these are caught by Hibernate's own `nullable = false`
    metadata *before* SQL is sent, so they pin the entity annotations, not the DB constraint.
- **`UrlShortenerApplicationTests` de-coupled** — the migration test now asserts table existence via
  `information_schema.tables` instead of `count(*) == 0`, which had made it depend on every other
  test class cleaning up (one shared Spring context = one shared Postgres).
- **Step 3a (alias generator) — green.** Sqids added to `pom.xml` (pinned via a `sqids.version`
  property — the version is part of the URL contract, so it's explicit and visible). `AliasGenerator`
  wraps it: `encode(id) → String`, one shared immutable `Sqids` instance, alphabet and `minLength`
  as package-private constants per [D14](./DECISIONS.md). `AliasGeneratorTest` is a plain unit test —
  no Spring, no Docker, sub-second:
  - **Generated aliases are alphanumeric only, no `-`/`_`** — parameterised over ids up to
    `Long.MAX_VALUE`. This pins [D11](./DECISIONS.md)'s permanent invariant with a test rather than
    good intentions, so an alphabet change can't silently break F4's namespace partition.
  - The alphabet constant is 62 alphanumeric characters. Distinctness is covered indirectly —
    Sqids' builder rejects duplicates, so 62 + no-duplicates + alphanumeric can only be a
    permutation of all 62.
  - 1000 distinct ids give 1000 distinct aliases (a *sample* of D1's bijection claim, not a proof),
    encoding is deterministic across calls, and `minLength = 7` is honoured as a floor.
  - Ids below 1 are rejected — defensive, since `SEQ_URL_ALIAS` starts at 1.
  - The `minLength` assertion uses the literal `7`, not the constant, deliberately: mirroring the
    constant would let a change to it pass unnoticed, and D14 makes that value revisable *but
    deliberate*.
- **Step 3b (service) — green.** `UrlShortenerService.createShortLink(longUrl)` reads the sequence,
  encodes, and writes `id` + `LONG_URL` + `URL_ALIAS` in one INSERT, inside `@Transactional`.
  `UrlShortenerServiceTest` is a Testcontainers `@SpringBootTest` against the migrated schema:
  - **The id comes from an explicit `SELECT nextval('SEQ_URL_ALIAS')`** — `UrlAliasRepository.getNextId()`
    — *not* from a Hibernate id generator. `UrlAlias.id` has no `@GeneratedValue`; the service assigns
    it. This makes the load-bearing `nextval → encode → INSERT` ordering visible in the method instead
    of resting on JPA lifecycle behaviour. Reasoning and its costs are in
    [D10](./DECISIONS.md#d10--store-the-alias-reverses-d9) under "How the id is assigned".
  - Three creates of the *same* long URL produce three rows with distinct aliases — the F1 behaviour
    the repository test's "`LONG_URL` is not unique" assertion set up.
  - A `null` URL throws `IllegalArgumentException` before touching the DB. Note this is a
    *defensive* guard: `@Valid` on the DTO is the real barrier for HTTP callers (3c).

### In progress / partial
- **Controller** — maps `POST /api/v1/links`, returns **201**, and now calls the service. Two gaps
  remain, both for 3c: the response body only sets `longUrl` (no `alias`, no `shortUrl`), and the
  `Location` URI is built from the *current request* with an empty path segment, so it resolves under
  `/api/v1/links/` rather than root — contradicting D5 and the contract in [DESIGN.md](./DESIGN.md).
  R4 stands until 3c.
- **Controller test** — asserts status codes only; nothing yet checks the response body or the
  `Location` header. Rewritten in 3c.

### Next (immediate) — Step 3: create endpoint

`POST /api/v1/links` → `encode(id)` stored in one INSERT → 201 with the documented body
([DESIGN.md](./DESIGN.md)). Resolves R4. Three increments, each independently testable:

- ~~**3a — Alias generator.**~~ ✅ **Done** — see the status board above.
- ~~**3b — Service.**~~ ✅ **Done** — see the status board above.
- **3c — Controller.** ← *next.* Move `/create` → `POST /api/v1/links`, return **201** with
  `{alias, shortUrl, longUrl}`. Rewrite the existing controller test (it currently asserts 200 on
  `/create`). Add **CORS** here — first real endpoint the frontend will call ([WORKFLOW.md](./WORKFLOW.md)).

**Decisions to make before/while building 3c** (Tech Lead flags):

1. ~~**Sqids alphabet + `minLength`.**~~ ✅ **Settled — [D14](./DECISIONS.md).** Own shuffled
   alphanumeric alphabet, `minLength = 7`, default blocklist, all as constants in `AliasGenerator`.
   Revisable (not frozen) because D10 stores the alias.
2. **Base URL for `shortUrl`.** It's derived, not stored (DESIGN.md), so it needs a config property
   with a sensible local default rather than a hardcoded `localhost:8080`.

### Deferred / low priority
- **`UrlAlias` → `ShortenedUrl` rename** — style only; the name is accurate again under D10.
- **Redundant `SELECT` on create** — `save()` with a pre-assigned id routes through `merge()`, which
  checks for the row before inserting. Correct, just a wasted round-trip on the cold path; fix via
  `Persistable<Long>` or `EntityManager.persist`. See [R9](./DECISIONS.md).
- **Dead assertion in `UrlAliasRepositoryTest.given_findAll_...`** — it asserts the two saved ids
  differ by 1, but both are hardcoded (`1`, `2`) and the entity no longer has an id generator, so it
  can't fail. Either drop it or move a real sequence-step assertion into `UrlShortenerServiceTest`,
  which is where `nextval` is actually exercised now.
- **`spring.jpa.hibernate.ddl-auto: validate`** — cheap boot-time guard against entity/schema drift.

### Backlog
- Build-order steps 3–5 (create endpoint w/ Sqids, redirect, error polish), then v2 features F4–F7.
- **CORS** config for the frontend, alongside the first real endpoint.
