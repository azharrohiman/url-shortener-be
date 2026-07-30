# URL Shortener — Roadmap & Progress

> Single source of truth for **what we're building, in what order, and what's done.**
> Companion docs: [DESIGN.md](./DESIGN.md) (API + schema), [DECISIONS.md](./DECISIONS.md) (decisions + risks).

_Last updated: 2026-07-28_

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
  - `SEQ_URL_ALIAS` drives `ID` and steps by exactly 1 — pins `@SequenceGenerator(allocationSize = 1)`
    against the DB's increment, the mismatch D10 warns about.
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

### In progress / partial
- **Controller** — `POST /create` still echoes the URL back; placeholder, not the real contract (R4).
- **Service** — `UrlShortenerService` is an empty shell.

### Next (immediate) — Step 3: create endpoint

`POST /api/v1/links` → `encode(id)` stored in one INSERT → 201 with the documented body
([DESIGN.md](./DESIGN.md)). Resolves R4. Three increments, each independently testable:

- **3a — Alias generator.** Add the Sqids dependency and wrap it in a small component
  (`encode(long id) → String`). Plain unit test, no Spring, no DB. Assert distinct ids give distinct
  aliases and — critically — that output is **alphanumeric with no `-`/`_`**, which is the permanent
  [D11](./DECISIONS.md) invariant the whole custom-alias partition rests on. Put that in a test now,
  while it's cheap, so a future alphabet change can't silently break F4.
- **3b — Service.** `createShortLink(longUrl)` → persist `id` + `LONG_URL` + `URL_ALIAS` in a
  **single INSERT** (D10). Integration test with Testcontainers.
- **3c — Controller.** Move `/create` → `POST /api/v1/links`, return **201** with
  `{alias, shortUrl, longUrl}`. Rewrite the existing controller test (it currently asserts 200 on
  `/create`). Add **CORS** here — first real endpoint the frontend will call ([WORKFLOW.md](./WORKFLOW.md)).

**Decisions to make before/while building 3a–3c** (Tech Lead flags, not yet settled):

1. **Sqids alphabet + `minLength`.** The alphabet is locked to alphanumeric forever by D11; the
   *ordering* and `minLength` are free and revisable (D10 unfroze them). Needs a decision entry.
2. **How the id is known before INSERT.** `persist()` under `GenerationType.SEQUENCE` assigns the id
   immediately, so the alias can be set before flush — but that ordering is load-bearing and subtle.
   Worth an explicit choice (and a comment) rather than something that happens to work.
3. **Base URL for `shortUrl`.** It's derived, not stored (DESIGN.md), so it needs a config property
   with a sensible local default rather than a hardcoded `localhost:8080`.

### Deferred / low priority
- **`UrlAlias` → `ShortenedUrl` rename** — style only; the name is accurate again under D10.
- **`@Column(length = 12)` on `UrlAlias.urlAlias`** — column is really `VARCHAR(64)` (R3). Inert at
  runtime (Liquibase owns the schema) but misleading; fix when next in that file.
- **`spring.jpa.hibernate.ddl-auto: validate`** — cheap boot-time guard against entity/schema drift.

### Backlog
- Build-order steps 3–5 (create endpoint w/ Sqids, redirect, error polish), then v2 features F4–F7.
- **CORS** config for the frontend, alongside the first real endpoint.
