# Decisions & Risks

> Short log of the technical decisions we've made (with the trade-off behind each)
> and the open risks to address. Append new entries; don't rewrite history.

_Last updated: 2026-07-31_

---

## Decisions

### D1 — Short codes via Sqids over a DB sequence
We encode a `SEQ_URL_ALIAS` value with [Sqids](https://sqids.org) to produce the alias.

- **Why:** it's a bijection over a unique number, so **collisions are impossible** —
  no check-and-retry loop, no unique-violation handling. It also hides the obvious
  `1, 2, 3…` sequentiality.
- **Honest trade-off:** Sqids is **obfuscation, not encryption**. The alphabet/salt is
  not a secret key; the value is reversible by design and a determined attacker can
  recover the alphabet or brute-force short ids. So it is *not* "unguessable." For a
  **public, anonymous** shortener the URLs aren't secret anyway, so enumeration is
  low-stakes — acceptable for our MVP.
- **Implementation note:** Sqids needs the number *first*. Pull the sequence value
  before insert (or insert then encode-and-update). To be decided at build-order step 3.
  > **Resolved via the SEQUENCE strategy (see [D10](#d10--store-the-alias-reverses-d9)).** With
  > `GenerationType.SEQUENCE`, Hibernate fetches the id (`SELECT nextval`) *before* the INSERT,
  > so we `encode(id)` in memory and write `id` + `alias` in a **single INSERT** — no pre-fetch
  > beyond the intrinsic sequence call, and no follow-up update.

### D2 — Redirect with HTTP 302 (not 301)
- **Why:** 301 is cached hard by browsers; if we later add analytics or need to change a
  target, a cached 301 would bypass us. 302 keeps the door open. Costs us nothing now.

### D3 — Migrations via Liquibase
- **Why:** already the project's tool and the one the owner is fluent in. No reason to
  introduce Flyway as a second migration tool.

### D4 — No auth in MVP
- **Why:** audience is public + anonymous. Auth, ownership and multi-tenancy are v2.

### D5 — Two route namespaces
- `/api/v1/**` for the JSON API, `/{alias}` at root for redirects. Keeps short URLs short
  and avoids the API path colliding with alias lookups.

### D6 — TDD with Testcontainers as the development workflow
We develop test-first. The inner loop is automated tests; integration tests use
Testcontainers, which spin up a real throwaway Postgres and apply Liquibase at context
startup. We do **not** run the app + Postman as the primary loop.

- **Why:** fast, reproducible, deterministic feedback; the migration is exercised on every
  integration test run. See [WORKFLOW.md](./WORKFLOW.md) for the full three-layer workflow.
- **Trade-off / correction:** rebuilding a Docker *image* per change just to poke endpoints
  is a slow loop for **backend** devs, so it's not their inner loop. (It's the right tool for
  *frontend* devs though — see D8.) Also note Liquibase runs at app **startup**, not at image
  build.

### D7 — Pin the Postgres version
Use `postgres:18` in both `compose.yaml` and `TestcontainersConfiguration`, not
`postgres:latest`.

- **Why:** reproducibility. `latest` drifts over time and can differ between the test DB and
  the local Compose DB; pinning keeps tests deterministic and the two environments matched.

### D8 — Containerise the backend for the frontend-dev workflow
A multi-stage `Dockerfile` plus a separate `compose.app.yaml` (app + Postgres) let a frontend
developer run the backend with **only Docker** — no JDK/Maven/IntelliJ —
via `docker compose -f compose.app.yaml up --build`.

- **Why:** the sibling `url-shortener-fe` needs a runnable backend without its devs owning the
  Java toolchain. This is a primary use case, so the Dockerfile is a deliverable, not deferred.
- **Why a *separate* compose file:** `compose.yaml` is consumed by the `spring-boot-docker-compose`
  dev integration on `./mvnw spring-boot:run`. Putting the `app` service there would make backend
  devs spin up a containerised app too. `compose.app.yaml` `include:`s `compose.yaml` to reuse
  Postgres without duplication.
- **Why multi-stage:** build with the Maven image (at the time this also sidestepped a broken
  `mvnw` wrapper — since restored; the reasoning below stands on its own), ship a
  slim JRE runtime. Tests are skipped in the image build — they need Docker/Testcontainers and
  belong in the inner loop / CI.
- **Container specifics:** `SPRING_DOCKER_COMPOSE_ENABLED=false` (no Docker inside the
  container) and an explicit `SPRING_DATASOURCE_*` pointing at the `postgres` service.

### D9 — Derive the alias, don't store it
> ⛔️ **SUPERSEDED by [D10](#d10--store-the-alias-reverses-d9)** (2026-07-04). We chose the
> stored-alias model instead. Kept here for the reasoning; the trade-off discussion below still
> explains *why* deriving looked attractive and what it cost.

The alias is **computed from the row's `id`** (Sqids `encode(id)`), not persisted. There is
**no `URL_ALIAS` column** and no unique index on it. Both directions are pure computation:

- **Create:** insert a row with just `LONG_URL`; the `id` comes back from `SEQ_URL_ALIAS` on
  save; `encode(id)` → alias in the response. One insert, no second round-trip.
- **Redirect:** `decode(alias)` → number; **re-encode that number and compare it to the input**
  — if it doesn't match, `404` (see the guard below); otherwise `findById(number)` → long URL.

- **Why:**
  - **Removes a round-trip / the chicken-and-egg.** Sqids needs the number first (D1); by
    reading `id` off the saved entity we avoid pre-fetching the sequence or an insert-then-update.
  - **Fewer moving parts in the schema.** No `URL_ALIAS` column, no `UQ_URL_ALIAS` unique index
    to store or maintain — the redirect reuses the **primary key**.
  - **Faster, narrower lookup.** Redirect is a PK lookup on a `BIGINT` rather than a secondary
    index on a `VARCHAR`.
  - Retires **R3** (alias column width) entirely — no column, no risk.

- **The decode guard (mandatory):** `decode` is a *total, lenient* function — it returns a number
  for **any** input, and non-canonical strings can decode to a number whose canonical encoding is
  different. So the redirect must **re-encode and compare** (`encode(decode(alias)) == alias`),
  which cheaply rejects junk/non-canonical input *before* touching the DB and guarantees one
  canonical alias per URL. A well-formed alias for an id we never minted still fails at
  `findById`. See the "bijection is over *canonical* strings" discussion.

- **Honest trade-offs (accepted):**
  - **Sqids config is now frozen forever.** Because aliases are recomputed rather than stored,
    the **alphabet, salt, and `minLength` become a permanent part of the URL contract** — change
    any of them and *every previously issued link breaks*. These must be chosen once and treated
    as immutable from the first real link. (`minLength` value: **still to be chosen**.)
  - **Custom aliases (F4, v2) will need their own storage.** A user-chosen vanity code can't be
    derived from an `id`, so F4 will reintroduce a small lookup table (string → id). That's the
    natural shape anyway: generated aliases stay derived, vanity ones are the stored exception.

- **Naming fallout (open):** with no alias stored, the `UrlAlias` entity / `TB_URL_ALIAS` table
  are slight misnomers (the row is really a stored URL keyed by id). Rename to a domain noun
  (e.g. `ShortenedUrl`) is **still to be decided**; table rename deferred (migration churn).

### D10 — Store the alias (reverses D9)
We **persist** the alias in a `URL_ALIAS` column with a unique index, and look it up directly.
This reverses [D9](#d9--derive-the-alias-dont-store-it) and restores the schema the pre-D9 code
already had (column + `UQ_URL_ALIAS`).

- **Create:** `SELECT nextval('SEQ_URL_ALIAS')` (Hibernate, SEQUENCE strategy) gives the `id`
  *before* insert → `encode(id)` in memory → **single INSERT** writes `id` + `LONG_URL` + `URL_ALIAS`.
- **Redirect:** `findByUrlAlias(alias)` → **302** to `LONG_URL`, or **404**. One indexed lookup.

- **Why we flipped:**
  - **Custom aliases (F4) are now confirmed coming**, and a stored column makes them *trivial*:
    generated and user-chosen slugs live in the **same column under one unique index**, so GET is
    a single lookup — no "which table do I check?" routing, no separate vanity table.
  - **The unique constraint handles all collisions for free**, including the nasty *future*
    collision (a custom alias squatting on a string the generator will later mint): the generator's
    INSERT simply fails the unique constraint → retry with the next id. No exclusion-set bookkeeping.
  - **Simpler read path — the decode guard disappears entirely.** D9's mandatory
    decode→re-encode→compare dance existed *only* because the alias wasn't stored. Storing it means
    GET is just `findByUrlAlias`.
  - **No extra round-trip** (the fear that pushed us to D9). Because we use a **sequence**, not
    `IDENTITY`, the id is known before the INSERT; the alias rides along in the same INSERT. See the
    resolved note on [D1](#d1--short-codes-via-sqids-over-a-db-sequence). (So **no** need to bump
    `allocationSize`/`INCREMENT` to 50 — that would optimise a non-bottleneck and force the DB
    increment and Hibernate `allocationSize` to move in lockstep. Keep `allocationSize = 1`.)
  - **Sqids config is no longer frozen.** Since aliases are stored, the alphabet/salt/`minLength`
    can change for *new* links without breaking old ones (old aliases are read from the column, not
    recomputed). This retires D9's biggest lock-in; `minLength` becomes a low-stakes, revisable knob.

- **Trade-offs (accepted):**
  - Reintroduces the `URL_ALIAS` column + `UQ_URL_ALIAS` index (storage + a secondary index).
  - Redirect is now an index lookup on a `VARCHAR` rather than a PK lookup on `BIGINT` — still
    O(log n), negligible at our scale (see [WORKFLOW.md](./WORKFLOW.md) on the redirect hot path).
  - Generation needs **unique-violation retry**: `encode(id)` never collides with another
    *generated* alias (bijection over ids), but it *can* collide with an existing *custom* alias —
    catch the violation and advance to the next id.
    > ⚠️ This retry can **cascade** into a run of consecutive failures (and is a deliberate
    > squatting/DoS vector), and "advance to the next id" hides a `nextval`-vs-`id + 1` trap.
    > Worked example, analysis, and mitigations in [SCENARIO.md](./SCENARIO.md); tracked as
    > **[R7](#r7--custom-alias-can-squat-a-generated-code)**.

- **Scope note:** this is a **storage/schema** decision made now to avoid a v2 migration; it does
  **not** pull F4 into the MVP. The MVP still ships generated-only (F1–F3); we simply build it on
  the schema custom aliases will need.

- **Naming (still open):** `UrlAlias` / `TB_URL_ALIAS` are now accurate again (an alias *is*
  stored), so the D9 rename pressure eases. Renaming to `ShortenedUrl` remains a style call — TBD.

### D11 — Partition the custom/generated namespace by character class
Custom and generated aliases share the one `URL_ALIAS` column (D10), but we constrain the two
**value sets to be provably disjoint** so a generated `encode(id)` can *never* equal a stored
custom slug. This dissolves [R7](#r7--custom-alias-can-squat-a-generated-code): the
unique-violation retry, its cascade, and the squatting/DoS vector all disappear because the
collision becomes **structurally impossible**, not merely handled. Full analysis in
[SCENARIO.md](./SCENARIO.md).

- **The invariant (permanent):** the Sqids alphabet is **alphanumeric only** and must *never*
  include the reserved separators `-` or `_`. Every generated alias is therefore a string over
  `[0-9a-zA-Z]` with no separator. This is a hard, forever constraint — see
  [D1](#d1--short-codes-via-sqids-over-a-db-sequence); if the alphabet ever gains `-`/`_` the
  partition silently breaks and R7 returns. Must live as a loud comment beside the Sqids config.

- **Custom-slug validation rules (F4):**
  1. Charset `[0-9a-zA-Z_-]`, length **3–64** (column widens to `VARCHAR(64)` — see
     [R3](#r3--url_alias-column-width--resolved--️-moot-d9--️-back-in-play-d10--resolved-varchar64) —
     **already applied**, the column is `VARCHAR(64)` today).
  2. **Must contain at least one `-` or `_`** — this is what forces the slug out of the generated
     set and guarantees disjointness.
  3. **Cannot start or end with `-`/`_`** — avoids near-invisible duplicates (`myblog` vs
     `myblog_`) and matches standard slug convention.
  4. *(Recommended, revisable)* **No consecutive separators** (`my--blog`) — same readability
     rationale; TBD, safe to drop without affecting disjointness.
  - Reference regex: `^(?=.{3,64}$)[0-9a-zA-Z]+(?:[-_][0-9a-zA-Z]+)+$`. Implement as **separate
    checks** for per-rule error messages, not one opaque pattern.

- **Why this over the alternatives:** length partitioning is fragile (generated code length grows
  with id, so bands eventually overlap); prefix markers pollute the URL or break "keep short URLs
  short" (D5). A character-class rule is O(1), needs no DB round-trip, and `-`/`_` are natural in
  real vanity slugs (`black-friday-2026`).

- **What it preserves:** D10's single column + single-lookup GET (`findByUrlAlias`) is untouched —
  both kinds still live under one `UQ_URL_ALIAS` index. The unique constraint now only guards
  *custom-vs-custom* clashes (→ **409**, no retry).

- **Trade-offs (accepted):** a mild UX constraint (vanity slugs must include a `-`/`_`, needs a
  clear error message); the permanent alphabet lock above; and `VARCHAR(64)` (R3). Tightening
  custom validation never endangers the partition — stricter rules only shrink the accepted set.

- **Scope:** design for **F4 (v2)**. Not built in the MVP, but the rule must be **decided now** —
  it's unenforceable-in-retrospect once non-conforming custom slugs exist in the table.

---

### D12 — Local credentials via `.env` + direnv, never committed

Ahead of `git init`, database credentials were removed from `compose.yaml` and
`compose.app.yaml` and replaced with Compose variable interpolation. `.env` (gitignored) is
the single source of truth; `.env.example` is the committed template.

- **Why, honestly:** the old values (`myuser`/`secret`/`mydatabase`) were Spring Initializr
  defaults on a localhost-only throwaway database — **not** meaningful secrets, and leaking
  them would have cost nothing. The decision is justified by what it buys instead: the
  compose files become environment-driven, which is a prerequisite for ever running this
  anywhere but a laptop. Recorded plainly so we don't later mistake this for a security
  incident that it wasn't.

- **Two consumers, one file:** Docker Compose auto-loads `.env` from the project directory;
  direnv exports the same values into the shell via `.envrc` (committed, one line:
  `dotenv_if_exists .env`) for `psql` and `./mvnw spring-boot:run`. No duplication, so the two
  cannot drift.

- **Fail fast, no defaults:** variables are declared `${VAR:?…}`. A missing `.env` aborts
  `docker compose up` with a readable message instead of silently starting a
  half-configured database. **Trade-off (accepted):** the frontend-dev onboarding path (D8)
  gains a mandatory `cp .env.example .env` step — documented in
  [WORKFLOW.md](./WORKFLOW.md). The alternative (`${VAR:-default}`) would have preserved
  zero-setup clone-and-run but kept the values in git, defeating the point.

- **Unaffected:** `./mvnw test` — Testcontainers generates its own random per-run credentials
  and reads none of this. `src/main/resources/application.yaml` has no datasource block at
  all (the `spring-boot-docker-compose` integration derives it from the running container),
  so no application code changed.

- **Gotcha to remember:** Postgres only creates the role/database on *first* init against an
  empty data directory. Because `pgdata` is a persistent named volume, changing
  `POSTGRES_USER`/`POSTGRES_DB`/`POSTGRES_PASSWORD` has no effect until
  `docker compose down -v`.

- **Also now gitignored:** `target/`, `.idea/`, `.DS_Store`.

---

### D13 — No local performance testing; the local check is the query plan

Performance and scale testing are deliberately out of scope for local development.
Correctness testing ([D6](#d6--tdd-with-testcontainers-as-the-development-workflow)) is the
inner loop; performance work is a separate activity with different tooling and a different
environment, and we don't blur the two.

- **Why not locally:** a single Postgres on a laptop doesn't represent production hardware,
  configuration, or concurrency. Loading millions of rows into the test suite would produce
  numbers that look rigorous and mean nothing — the worst combination, since it also slows
  every test run and invites false confidence.

- **What real performance testing needs**, when it's justified: a dedicated environment
  mirroring production (matched Postgres version and config, comparable instances, real
  connection pooling), seeded with production-like data (`generate_series`, `pgbench`, or
  anonymised samples), driven by a load tool (k6, Gatling, JMeter, Locust), measuring p95/p99
  latency, throughput, connection-pool saturation, lock contention, and slow-query logs.

- **What we do instead:** the only hot path in this app is the redirect lookup by
  `URL_ALIAS`. `UQ_URL_ALIAS` already backs it with a unique index, so the lookup is a
  logarithmic index scan rather than a sequential one. The meaningful local check is
  therefore the *query plan* — `EXPLAIN ANALYZE` confirming index usage — not wall-clock time
  on a laptop.

- **The honest caveat:** logarithmic is not the same as fast forever. Index depth grows
  slowly, but at large row counts the binding constraint becomes whether the index stays
  resident in the buffer cache, not the complexity of the scan. That's a property of the
  deployment rather than the query, which is precisely why it can't be assessed here.

- **Trade-off (accepted):** v1 ships with no measured performance baseline. Acceptable
  because the access pattern is a single indexed point lookup with no joins and no scans —
  the shape of query least likely to hold surprises.

- **Revisit when:** there's an environment worth measuring in, or the access pattern stops
  being a single point lookup. Analytics (F5) would add write amplification to the redirect
  path; that's the first change that would warrant real numbers.

---

## Open risks / things to fix

> Issues surfaced early. R1–R3 and R6 resolved; R4–R5 remain; R7 opened by D10, fix decided in
> D11 (pending F4).

### R1 — Migration is disabled ✅ RESOLVED
The `001_create_url_alias_table` include in `changelog-master.yaml` is enabled; the changeset
now applies (verified in tests and in the container).

### R2 — SQL bugs in `001_create_url_alias_table.sql` ✅ RESOLVED
Fixed: `TIMESTAMPZ`→`TIMESTAMPTZ`, and the missing comma between the `PK_`/`UQ_` constraints.

### R3 — `URL_ALIAS` column width ✅ RESOLVED → ⛔️ MOOT (D9) → ♻️ BACK IN PLAY (D10) → ✅ RESOLVED (`VARCHAR(64)`)
Widened `VARCHAR(7)`→`VARCHAR(12)`→**`VARCHAR(64)`**. Note: the column width is a *ceiling*, not
the code length — Postgres `VARCHAR(n)` doesn't pad, so an unused ceiling costs nothing. Actual
code length is set by Sqids (and an optional `minLength` we'll choose when wiring it up); 12 chars
already covered well beyond a trillion URLs. Collisions are impossible regardless (Sqids is a
bijection over the unique sequence — see D1); a too-small column risks insert *failure*, never
duplication.
> **Settled at `VARCHAR(64)`** in `001_create_url_alias_table.sql`, matching the 3–64 char custom-slug
> rule in [D11](#d11--partition-the-customgenerated-namespace-by-character-class). Done **now**,
> before any real data exists, so F4 needs no widening migration later. Generated aliases are
> unaffected — they use a small fraction of the width.
>
> ⚠️ **Known drift:** `UrlAlias.urlAlias` still carries `@Column(length = 12)`. That attribute only
> drives DDL generation, which we don't use (Liquibase owns the schema), so it's inert at
> runtime — but it now misreports the real column. Align it to 64 when next touching the entity.

### R4 — Controller doesn't match the contract yet
`POST /create` echoes the URL; needs to become `POST /api/v1/links` returning the
documented response (see [DESIGN.md](./DESIGN.md)). Tests reference `/create` and assert
200 on success — both will change when the real endpoint lands.

### R5 — `@URL` accepts `ftp:`/`file:` schemes
The existing test treats `ftp://example` and `file:some-file` as valid. If we want to
restrict to `http`/`https`, we'll need explicit scheme validation. Flagging; not urgent.

### R6 — Code predates D9 (still stores the alias) ✅ RESOLVED (D10)
The entity (`UrlAlias.urlAlias`), repository (`findByUrlAlias`), and migration
(`URL_ALIAS VARCHAR(12)` + `UQ_URL_ALIAS`) model a **stored** alias. Under
[D10](#d10--store-the-alias-reverses-d9) that is exactly what we want — the code no longer
diverges, so no realignment is needed. Remaining Step-2 work is unchanged by this: the
persistence test (D6), and the optional `UrlAlias` → `ShortenedUrl` rename (style, TBD).

### R7 — Custom alias can squat a generated code ⚠️ OPEN (F4 / v2) → 🛠️ FIX DECIDED ([D11](#d11--partition-the-customgenerated-namespace-by-character-class))
D10 puts generated and custom aliases in one `URL_ALIAS` column, so a custom alias can occupy a
string the generator will later mint. The unique constraint catches it, but the **retry can
cascade** across a run of consecutive ids, and — because Sqids is reversible (D1) — an attacker
can *deliberately* pre-register upcoming codes to force a retry storm. Two implementation traps
come with it: "advance to the next id" must use `nextval`, **not** `id + 1` (the latter causes a
*future PK collision*), and a unique violation aborts the transaction (retry needs a fresh
tx/savepoint + a bound).
- **Not an MVP issue:** MVP is generated-only (F1–F3) → no customs → cannot occur yet.
- **Fix (decided — [D11](#d11--partition-the-customgenerated-namespace-by-character-class)):**
  partition the namespace by character class so a custom slug can never equal a generatable string
  (same column, validation rule) — collision becomes structurally impossible and the retry
  mechanism disappears. Rule decided now; **enforced at F4** (unenforceable once non-conforming
  customs exist).
- Full worked example, analysis, and options: [SCENARIO.md](./SCENARIO.md).
