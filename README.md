# URL Shortener — Backend

A Spring Boot service that turns a long URL into a short code, and redirects
visitors from that code back to the original URL.

The scope is deliberately small. The emphasis is on building in independently
testable increments, and on recording *why* each decision was made — the
reasoning lives in [`docs/`](./docs), not just in the commit history.

---

## Status

**v1 is in progress.** The persistence layer is built and tested; the public
endpoints are not finished yet.

| Area | State |
|------|-------|
| Project skeleton, dependencies, container setup | Done |
| Liquibase migration for `TB_URL_ALIAS`, verified by an integration test | Done |
| `UrlAlias` entity + repository, covered by Testcontainers tests | Done |
| Request validation (`@NotBlank`, `@Size(2048)`, `@URL`) + `GlobalExceptionHandler` | Done |
| `POST /api/v1/links` — create a short link | **Not implemented** (placeholder `POST /create` echoes its input) |
| `GET /{alias}` — redirect | **Not implemented** |

Current progress and the next planned increment are tracked in
[`docs/ROADMAP.md`](./docs/ROADMAP.md).

---

## Tech stack

- **Java 21**
- **Spring Boot 4.1** — Web MVC, Data JPA, Validation
- **PostgreSQL 18**
- **Liquibase** for schema migrations, applied at application startup
- **Testcontainers** + JUnit 5 for integration tests against a real Postgres
- **Maven** via the wrapper (`./mvnw`, pinned to 3.9.11)

---

## Getting started

### Prerequisites

- **JDK 21.**
- **A running container runtime** — Docker Desktop, OrbStack, Colima or Podman.
  Both Testcontainers and Docker Compose need a live daemon; check with `docker info`.
- Maven is *not* required — `./mvnw` downloads its own pinned version on first use.
- [direnv](https://direnv.net) is optional, and only convenient for running the
  app or `psql` against the local database.

### Clone

```bash
git clone git@github.com:azharrohiman/url-shortener-be.git
cd url-shortener-be
```

### Run the tests

```bash
./mvnw test
```

This needs **no configuration and no `.env`**. Integration tests start a
throwaway Postgres via Testcontainers with generated credentials, boot the
Spring context against it, and let Liquibase apply the migrations. A fresh
clone with a JDK and a container runtime can run the full suite immediately.

### Run the application

Database credentials are not committed. Copy the template first:

```bash
cp .env.example .env    # edit the values if you want
direnv allow            # one-off, if you use direnv
./mvnw spring-boot:run
```

`spring-boot:run` uses the Spring Boot Docker Compose integration to start the
Postgres defined in `compose.yaml` automatically. The API listens on
`http://localhost:8080`.

To run against a throwaway database instead, with no `.env` required:

```bash
./mvnw spring-boot:test-run
```

### Run the whole stack in containers

For anyone who wants a running backend without a JDK or an IDE — a frontend
developer, for instance:

```bash
cp .env.example .env    # compose refuses to start without it
docker compose -f compose.app.yaml up --build
```

This starts Postgres and the backend together, applies the migrations on boot,
and serves the API on `http://localhost:8080`.

---

## API

The agreed contract, in full, is in [`docs/DESIGN.md`](./docs/DESIGN.md).
Summarised:

| Method | Path              | Purpose                                    |
|--------|-------------------|--------------------------------------------|
| `POST` | `/api/v1/links`   | Create a short link. `201` with the alias. |
| `GET`  | `/{alias}`        | Redirect. `302` to the long URL, or `404`. |

Two namespaces are kept separate on purpose: `/api/v1/...` for the JSON
management API, and a root-level `/{alias}` so short URLs stay short.

> Both endpoints are **still to be built** — see *Status* above. The contract is
> agreed and documented first so the implementation has something to conform to.

---

## Documentation

| Document | Contents |
|----------|----------|
| [`docs/ROADMAP.md`](./docs/ROADMAP.md) | Scope, features, build order, and what is done vs. next |
| [`docs/DESIGN.md`](./docs/DESIGN.md) | API contract, data model, naming conventions |
| [`docs/DECISIONS.md`](./docs/DECISIONS.md) | Append-only log of technical decisions, trade-offs, and open risks |
| [`docs/WORKFLOW.md`](./docs/WORKFLOW.md) | How to develop, test, and run the project locally |

New to the codebase? Read `ROADMAP.md` for the shape of the project, then
`WORKFLOW.md` before running anything.

---

## Licence

[MIT](./LICENSE)
