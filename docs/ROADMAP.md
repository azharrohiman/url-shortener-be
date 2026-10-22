# URL Shortener — Roadmap & Progress

> Single source of truth for **what we're building, in what order, and what's done.**
> Companion docs: [DESIGN.md](./DESIGN.md) (API + schema), [DECISIONS.md](./DECISIONS.md) (decisions + risks).

_Last updated: 2026-10-19_

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
- **Operational basics** — not features, but v1 isn't done without them:
  - A **health-check endpoint** that reports whether the app is up and can reach the database.
  - **Structured logging** — log lines as JSON, one named field per piece of information, so
    they can be searched and filtered.

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
- **Entity no longer leaves the service — green.** `createShortLink` returns the `ShortLink` record
  (`model` package) instead of the `UrlAlias` entity, mapped by static `UrlAliasMapper.toShortLink`
  (`mapper` package). See [D15](./DECISIONS.md) for why, and the layer table in
  [DESIGN.md](./DESIGN.md) for the three types and where each stops.
  - `UrlAliasMapperTest` is a plain unit test — no Spring, no Docker.
  - `UrlShortenerServiceTest` now also asserts the returned `ShortLink`s carry exactly the aliases
    that were persisted, so the service can't return something other than what it wrote. Its
    row-level assertions still go through the entity, which is correct for a persistence assertion.
  - Fixed as a side effect: `UrlShortenerControllerTest`'s three valid-URL cases were **erroring**
    with an NPE — the `@MockitoBean` service was never stubbed, so it returned `null`. They now stub
    it and pass. Full suite: **28 tests green.**
- **Entity is construct-once — green.** `UrlAlias` drops `@Setter`; `@NoArgsConstructor` is narrowed
  to `AccessLevel.PROTECTED` (Hibernate needs it to read rows back — see
  [D16](./DECISIONS.md)). The service builds the row in one three-arg constructor call instead of a
  no-arg constructor plus three setters, so a half-populated entity no longer compiles. All tests
  already used the three-arg constructor; `UrlAliasRepositoryTest` is the regression net, since it
  reads entities back out of Postgres. Suite still **28 green**.
- **Step 3c (controller) — green. Step 3 complete.** `POST /api/v1/links` returns **201** with
  `{alias, shortUrl, longUrl}` and a `Location` header pointing at the root-level short URL.
  `shortUrl` is built from the validated `app.base-url` property (`AppProperties`), not the request.
  `UrlShortenerControllerTest` asserts all three body fields, the body size, and `Location`.
  CORS was not added here — still in the backlog.
- **Step 4a (service) — green.** `UrlShortenerService.getLongUrl(alias)` calls `findByUrlAlias` and
  returns the long URL as a `String`, or throws `UrlNotFoundException` (new, unchecked, `exception`
  package). Named `getLongUrl` rather than the `findByUrlAlias` this board planned: it returns the URL,
  not the row, and the entity still stays in the service ([D15](./DECISIONS.md)).
  `UrlShortenerServiceTest` covers it with 3 new tests:
  - **Found:** two rows are seeded straight through the repository — not via `createShortLink`, so a
    bug there can't turn this test red — and the second alias is looked up. A lookup that ignored the
    alias and returned any row would fail.
  - **Aliases are case-sensitive:** `abc` and `aBc` are stored side by side and `abc` resolves to its
    own URL. This matters because the Sqids alphabet mixes upper and lower case, so two aliases that
    differ only in case are different links.
  - **Not found:** an unknown alias throws `UrlNotFoundException`.
  - Not yet decided: where `UrlNotFoundException` becomes a **404** (step 4b / 5).
  - Full suite: **33 tests green.**
- **Step 4b (controller) — green. Step 4 complete.** `GET /{alias}` calls `getLongUrl` and returns
  **302** with `Location: <longUrl>`. Exceptions become responses in `GlobalExceptionHandler`, which
  settles the open question from 4a. The full contract, with bodies, is in [DESIGN.md](./DESIGN.md).
  - **404** — `UrlNotFoundException` → `{ "message": ... }`.
  - **400 for an over-long alias** — `getLongUrl` rejects an alias longer than `ALIAS_LENGTH_LIMIT`
    (64, the width of `URL_ALIAS`) with `AliasLengthLimitException` before querying. The service
    tests cover both sides of the boundary: exactly 64 resolves, 65 throws.
  - **500 for an unusable stored URL** — if `LONG_URL` can't be parsed into a `Location` header, the
    controller throws `BrokenLinkException`. The handler logs it with the alias and returns a fixed
    message that doesn't echo the stored URL.
  - `UrlShortenerControllerTest` covers 302, 404, 400 and 500 for the redirect.
  - **`CreateAndRedirectJourneyTest`** — the first end-to-end test. A real HTTP server on a random
    port, the real service, and a Testcontainers Postgres, with nothing mocked. It creates a link,
    takes the path from the returned `shortUrl`, and follows it, asserting the 302 and `Location`.
    The client is set to never follow redirects, so the 302 itself is what gets asserted. This is the
    only test that proves the alias create stores is the alias redirect finds.
  - Full suite: **40 tests green.**
- **R9 fixed as a side effect.** The service now writes the row with `EntityManager.persist`, not
  `save()`, so create no longer runs a redundant `SELECT` first.
- **Step 5 (error-handling polish) — green. Build order complete.** Error bodies used to come in two
  shapes: validation failures returned `{ "<field>": "<reason>" }`, and every other error returned
  `{ "message": "..." }`. Every error now returns one shape based on RFC 9457 — `status`, `title`,
  `detail`, plus a `validation` list on validation failures only — sent as
  `application/problem+json`. The contract is in [DESIGN.md](./DESIGN.md) under "Error responses".
  - `ErrorResponseDto` + `ValidationErrorDto` replace the three single-field `*ResponseDto`s, and
    every handler in `GlobalExceptionHandler` builds its response through one helper that sets the
    status and content type.
  - **Fixed as a side effect:** the old handler collected validation errors into a `HashMap` keyed by
    field, so when `url` failed two checks the second overwrote the first. They are now a list, and
    a new test sends a URL that fails both `@URL` and `@Size` and asserts both are reported, in any
    order.
  - All three checks on `CreateUrlAliasRequestDto` now carry their own messages instead of the
    framework defaults.
  - `UrlShortenerControllerTest` asserts content type, `status`, `title` and `detail` on every error
    case, and that `validation` is absent on non-validation errors.
  - Full suite: **41 tests green.**
- **Health check — green.** `spring-boot-starter-actuator` added; `GET /actuator/health` returns
  **200** `{ "status": "UP", "groups": ["liveness", "readiness"] }`, or **503** `DOWN` when a check
  fails. The contract is in [DESIGN.md](./DESIGN.md) under "3. Health check".
  - No code of our own — Actuator provides the endpoint. The only setting added turns off its
    index page at `/actuator`, which would otherwise take that path away from `/{alias}`
    ([D5](./DECISIONS.md)).
  - `HealthCheckTest` (Testcontainers, real HTTP server) pins three things:
    - **The body is exactly the JSON above** (strict comparison), so no internal detail — which
      checks ran, the database product — can start leaking from a public endpoint unnoticed.
    - **The database check is included and `UP`.** Asked of the endpoint directly, since the HTTP
      body hides it. This is what makes `UP` mean "can reach Postgres", not just "the JVM is
      running".
    - **`GET /actuator` returns the redirect's 404.** Seen failing with 200 when Actuator was
      added without the setting.
  - Not tested: the 503 path. It needs the database to go away mid-test, which would disturb the
    Postgres container the other test classes share. Spring Boot's own behaviour (any `DOWN` check
    makes the whole response `DOWN`/503) is relied on.
  - Full suite: **44 tests green.**

### In progress / partial
- _Nothing in progress._

### Next (immediate)

The build order is complete, but v1 is not: structured logging, one of the two operational
basics in the v1 scope, is still missing. Load testing comes after them, and **before** Analytics (F5) — F5 adds work to
the redirect path, and its cost can only be measured against a baseline taken without it
([D13](./DECISIONS.md) names F5 as the first change worth measuring).

1. **Finish v1** — each one test-first, each its own PR:
   - ~~**Health check**~~ — ✅ **done** (see Done above).
   - **Structured logging** — JSON log output, so a failed load test can be investigated.
   - **Keep debug logging out of the default config.** SQL and parameter logging
     (`org.hibernate.SQL`, `org.hibernate.orm.jdbc.bind`) and Hibernate statistics belong in a
     `local` profile, switched on with `SPRING_PROFILES_ACTIVE=local`. Left on by default, they
     write several lines per request — a load test would measure the logger, not the app — and
     they copy every submitted long URL into the logs.
2. **Gatling script, run locally as a rehearsal only.** Proves the script works and the app
   returns no errors when many requests arrive at once. The numbers are thrown away, never
   recorded — D13's point that laptop numbers mean nothing still stands. Record this as a new
   decision refining D13 before starting.

Later steps, in order — detail to be planned when we reach them:

3. **Performance environment** — see Backlog.
4. **Baseline run** against realistic data (a few million rows). Record throughput (requests
   per second) and p99 latency (the response time 99 in 100 requests beat).
5. **F5 (Analytics)**, then re-run the same test and compare against the baseline.

### Deferred / low priority
- ~~**`UrlAlias` → `ShortenedUrl` rename**~~ — **dropped.** [D15](./DECISIONS.md) makes the point
  moot: the domain noun now exists as `ShortLink`, and the entity's job is to mirror `TB_URL_ALIAS`,
  so `UrlAlias` is the right name for it.
- ~~**Redundant `SELECT` on create**~~ — ✅ **fixed** by switching to `EntityManager.persist`
  (see Done above, and [R9](./DECISIONS.md)).
- **Dead assertion in `UrlAliasRepositoryTest.given_findAll_...`** — it asserts the two saved ids
  differ by 1, but both are hardcoded (`1`, `2`) and the entity no longer has an id generator, so it
  can't fail. Either drop it or move a real sequence-step assertion into `UrlShortenerServiceTest`,
  which is where `nextval` is actually exercised now.
- **`spring.jpa.hibernate.ddl-auto: validate`** — cheap boot-time guard against entity/schema drift.

### Backlog
- v2 features F4–F7.
- **Performance environment** for load testing — a temporary cloud setup that is created,
  tested against, and deleted afterwards. Ground rules:
  - The **same Docker image** as everywhere else; only settings change, through environment
    variables (as [D12](./DECISIONS.md) already does for credentials). `app.base-url` must be set
    per environment — it defaults to `http://localhost:8080`; the environment variable `APP_BASEURL` overrides it (Spring drops the dash when mapping
    property names to variable names).
  - **Gatling runs on a separate machine** from the app and database, so the two don't compete
    for CPU.
  - Hosting provider and budget not yet decided.
- **CORS** config for the frontend. Still not added, although both endpoints are now live.
