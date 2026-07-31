# URL Shortener — Backend

A service that turns a long URL into a short alias and redirects visitors back to the
original: `POST /api/v1/links` creates a link, `GET /{alias}` redirects.

**Stack:** Java 21, Spring Boot 4.1, PostgreSQL, Liquibase, Maven via `./mvnw`.
Tests use JUnit 5 and Testcontainers.

---

## Documentation

Living docs in `docs/`. Read the relevant one before working in its area, and keep it
current as scope, decisions, and progress change.

| Doc | What it owns | Read before |
|-----|--------------|-------------|
| [`docs/ROADMAP.md`](./docs/ROADMAP.md) | Scope (v1 vs. v2), features, build order, status board | starting any work — it names the current step |
| [`docs/DESIGN.md`](./docs/DESIGN.md) | API contract, data model, naming conventions, API↔DB mapping | touching endpoints, DTOs, entities, or migrations |
| [`docs/DECISIONS.md`](./docs/DECISIONS.md) | Numbered decision log (`D<n>`) and open-risk register (`R<n>`) | revisiting a settled choice, or to check known risks |
| [`docs/WORKFLOW.md`](./docs/WORKFLOW.md) | Local development: tests, running the app, containers, credentials | running tests, the app, or Compose |
| [`docs/SCENARIO.md`](./docs/SCENARIO.md) | Worked example of the unique-violation retry cascade under D10 | working on custom aliases (F4) |

Also: [`README.md`](./README.md) for the public overview, [`CONTRIBUTING.md`](./CONTRIBUTING.md)
for branch naming, PR titles, and what is enforced on `main`.

Keeping the docs honest:

- `DECISIONS.md` is **append-only**. Supersede an old entry with a new one that says what it
  reverses and why; don't rewrite history. (D9 → D10 is the worked example.)
- Reference decisions and risks by their id (`D10`, `R7`) rather than restating them, so
  there's one place to update.
- Update the `ROADMAP.md` status board and each doc's `_Last updated:_` date in the same
  change as the code they describe.

---

## Conventions

The docs above hold the detail. These are the rules that apply to every change.

### Database

- SQL identifiers are `UPPER_SNAKE_CASE`.
- Tables are prefixed `TB_`, sequences `SEQ_`. Constraints are prefixed by type: `PK_`,
  `UQ_`, `FK_`.
- Every schema change goes through a Liquibase changeset. Liquibase owns the schema — it
  runs at application startup, not at image build time. Never hand-edit a live schema, and
  don't rely on Hibernate `ddl-auto` to create anything.

### Testing

- **Test-first.** Write the failing test, then the code that passes it.
- `./mvnw test` is the inner loop (`./mvnw -Dtest=SomeClassTest test` for one class). It
  needs no `.env` — Testcontainers generates throwaway credentials per run.
- Integration tests use Testcontainers: `@SpringBootTest` +
  `@Import(TestcontainersConfiguration.class)` boots a real, disposable Postgres with the
  migrations applied, exercising the full HTTP → service → DB stack.
- Verify behaviour with a test, not by booting the app and poking it by hand. Manual runs
  are for exploration, not for confirming a change works.

### Code

- Conform to the API contract and data model in `DESIGN.md`. Where the code and the contract
  disagree, that is a defect in one of them — say which, and don't silently follow the code.
- Build in small increments, each one independently testable.
- A feature is done when it has tests, input validation, and sensible error handling — not
  when the happy path works.
