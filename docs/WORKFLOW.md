# Development Workflow

> How we build this project: **test-first (TDD)**, with the database provided by
> containers. The app is *not* run-and-poke-in-Postman as the primary loop.

_Last updated: 2026-07-31_

---

## Prerequisites

- **JDK 21**. Maven itself is optional: `./mvnw` pins **Maven 3.9.11** and downloads it on
  first use, so a fresh clone needs only a JDK. A system `mvn` works too, but every command
  in this project's docs uses `./mvnw` — it's reproducible, and it's the version CI would use.
- **A running container runtime** (Docker Desktop, OrbStack, Colima, Podman, …).
  Testcontainers and the Compose integration both need a live Docker daemon. Check with
  `docker info`.
- **A `.env` file** (see *Local credentials* below). Required before any `docker compose`
  command; not needed for `./mvnw test`, which uses Testcontainers' own random credentials.

---

## Local credentials

Database credentials are **not committed**. `.env` is the single source of truth and is
gitignored; `.env.example` is the committed template. After cloning:

```bash
cp .env.example .env   # then edit the values if you want
direnv allow           # one-off; re-run whenever .envrc changes
```

Two consumers read that one file:

- **Docker Compose** auto-loads `.env` from the project directory, for both compose files.
  The variables are declared `${VAR:?}` (required), so a missing `.env` fails immediately
  with a readable message rather than starting a half-configured database.
- **direnv** exports the same values into your shell via `.envrc`, so `psql` and
  `./mvnw spring-boot:run` see them without you retyping anything.

`./mvnw test` needs none of this — Testcontainers generates throwaway credentials per run.

> **Changing `POSTGRES_USER` or `POSTGRES_DB` later?** Postgres only creates the role and
> database on *first* init against an empty data directory. Because `pgdata` is a persistent
> named volume, editing those values does nothing until you wipe it with
> `docker compose down -v`. Changing `POSTGRES_PASSWORD` has the same caveat.

> **OrbStack / socket gotcha.** If tests fail with *"Could not find a valid Docker
> environment"* even though `docker info` works, Testcontainers can't locate the socket — not
> a version incompatibility. It happened here because `~/.testcontainers.properties` pinned
> `docker.client.strategy=UnixSocketClientProviderStrategy` (hard-targets
> `/var/run/docker.sock`, which became a broken symlink after an OrbStack update). Fix: in
> `~/.testcontainers.properties`, drop that strategy line and set
> `docker.host=unix:///Users/<you>/.orbstack/run/docker.sock`. No Testcontainers upgrade
> needed (2.0.5 works fine with Docker 29).

---

## The three layers

### 1. Inner loop — automated tests (95% of development)

Write the test and the code side by side. Run:

```bash
./mvnw test                       # all tests
./mvnw -Dtest=SomeClassTest test  # one class
```

- **Unit tests** cover pure logic with no Spring context and no DB — fast.
- **Integration tests** use **Testcontainers**: `@SpringBootTest` +
  `@Import(TestcontainersConfiguration.class)` starts a **real, throwaway Postgres**, Spring
  boots against it, and **Liquibase applies our migrations automatically** during startup.
  This exercises the full HTTP → service → real-DB stack. No app run, no Postman.

> Key fact: **Liquibase runs at application startup, not at image build time.** That's why
> simply starting the Spring context in a test is enough to apply (and therefore test) the
> migration.

### 2. Optional manual poking — no image rebuild

When you genuinely want to hit an endpoint by hand:

```bash
./mvnw spring-boot:test-run   # boots the app against a Testcontainers Postgres (throwaway)
./mvnw spring-boot:run        # boots the app against the Compose Postgres (persistent)
```

`spring-boot:test-run` uses `TestUrlShortenerApplication`. `spring-boot:run` uses the
`spring-boot-docker-compose` integration to auto-start `compose.yaml`. Either way you just
restart the process to pick up code changes — **there is no image to rebuild**.

### 3. Containerised backend — for frontend developers

The backend is containerised (multi-stage `Dockerfile`) so a **frontend developer can run it
with only Docker — no JDK, no Maven, no IntelliJ.** They clone `url-shortener-fe` +
`url-shortener-be`, then:

```bash
cp .env.example .env   # first time only — compose refuses to start without it
docker compose -f compose.app.yaml up --build
```

This starts Postgres + the backend; Liquibase applies the migration on app boot; the API is
on `localhost:8080`. The frontend (run separately, e.g. `npm run dev`) calls it there. They
rebuild the image (`--build`) only when they pull new backend code — not part of any inner loop.

> This is **not** the backend dev's loop. Rebuilding an **image** per code change is slow;
> backend devs use layers 1–2 above. Image vs container: an *image* is the built, immutable
> artifact; a *container* is a running instance of one. On a backend code change you rebuild
> the image and recreate the container.

> Note: browser calls from the frontend (`localhost:5173`/`3000`) to the backend (`:8080`)
> will need **CORS** configured on the backend. That's added alongside the first real
> endpoint, not before.

---

## Local persistent database (optional)

`compose.yaml` defines a Postgres with a **named volume (`pgdata`)**, so its data survives
restarts and `docker compose down`. Useful for experimenting with a standing dataset.

```bash
docker compose up -d        # start the standing local DB
docker compose down         # stop, keep the data
docker compose down -v      # stop and WIPE the data
```

Connect with:

```bash
psql "postgresql://$POSTGRES_USER:$POSTGRES_PASSWORD@localhost:5432/$POSTGRES_DB"
```

This relies on direnv having exported the values from `.env` (see *Local credentials*
above). If the variables are empty, you probably haven't run `direnv allow`.

---

## Performance testing

Not done locally, and deliberately so — a laptop Postgres can't produce representative
numbers. The local check that *is* meaningful is the query plan: `EXPLAIN ANALYZE` on the
redirect lookup, confirming it uses `UQ_URL_ALIAS`. Full rationale and what real perf
testing would require: [DECISIONS.md](./DECISIONS.md) D13.