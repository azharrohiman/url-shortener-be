# Design Deep Dive — Data Model, Layers & Log Contract

> The agreed low-level design. Code should conform to this; where current code diverges it's
> noted in [ROADMAP.md](../ROADMAP.md) and [DECISIONS.md](../DECISIONS.md). The API contract and
> error responses live in [HIGH_LEVEL_DESIGN.md](./HIGH_LEVEL_DESIGN.md#api-contract-mvp).

_Last updated: 2026-11-10_

---

## Data model (MVP)

Single table. Nothing to normalise yet; analytics (v2) would add a separate
`clicks` table in a one-to-many relationship rather than touching this one.

### `TB_URL_ALIAS`

> The alias is **stored** (D10). Generated aliases are `encode(id)`; custom aliases (F4, v2) will
> live in this **same column** under the unique index. The column is `VARCHAR(64)` — sized ahead
> for F4's custom slugs (max length 64 per [DECISIONS.md](../DECISIONS.md#d11--partition-the-customgenerated-namespace-by-character-class) D11) so custom aliases
> land without a migration. Generated codes use only a fraction of that width; `VARCHAR(n)` in
> Postgres doesn't pad, so the extra ceiling costs nothing. See R3.

| Column       | Type           | Notes                                            |
|--------------|----------------|--------------------------------------------------|
| `ID`         | `BIGINT`       | PK, default `nextval('SEQ_URL_ALIAS')`.          |
| `LONG_URL`   | `VARCHAR(2048)`| Original URL. Matches the DTO's 2048 max.        |
| `URL_ALIAS`  | `VARCHAR(64)`  | The short code. **Unique** (`UQ_URL_ALIAS`). For generated codes = `encode(ID)`. Width set by F4's 64-char custom-slug limit (D11). |
| `CREATED_AT` | `TIMESTAMPTZ`  | Defaults to `now()`.                             |

Plus sequence **`SEQ_URL_ALIAS`**, which feeds the PK; a generated alias is `encode(id)` of that PK.
The service reads `nextval` explicitly, encodes, and writes id + alias in the same INSERT (D10).

Because the alias is stored, resolving a redirect is a single indexed lookup:
`findByUrlAlias(alias)` → **302** to `LONG_URL`, else **404**. No decode/re-encode guard is
needed — that was only required by the derived-alias model (D9).

### Naming conventions (established in this repo)

- Tables prefixed `TB_`, sequences prefixed `SEQ_`.
- Constraints prefixed by type: `PK_`, `UQ_` (and `FK_` later).
- SQL identifiers UPPER_SNAKE_CASE.

### API ↔ DB field mapping

| API JSON   | DB column / source |
|------------|--------------------|
| `longUrl`  | `LONG_URL`         |
| `alias`    | `URL_ALIAS` (stored; generated = `encode(ID)`) |
| `shortUrl` | derived (host + `alias`), not stored |

---

## Layers & types

Three types describe the same link, one per boundary. Each stops where the next begins — see
[DECISIONS.md](../DECISIONS.md#d15--the-service-returns-a-domain-record-never-the-entity) D15.

| Layer | Type | Package | Purpose |
|-------|------|---------|---------|
| HTTP | `CreateUrlAliasRequestDto` / `CreateUrlAliasResponseDto` | `request` / `response` | The JSON contract in [HIGH_LEVEL_DESIGN.md](./HIGH_LEVEL_DESIGN.md#api-contract-mvp). Validation lives here. |
| Service | `ShortLink` (record: `alias`, `longUrl`) | `model` | What the service returns. Immutable, no persistence concern. |
| Persistence | `UrlAlias` (`@Entity`) | `entity` | Mirrors `TB_URL_ALIAS`. Never leaves the service layer. |

`UrlAliasMapper.toShortLink(entity)` (package `mapper`) is the single crossing between the bottom
two. The controller maps `ShortLink` → response DTO, and derives `shortUrl` there since it is the
only layer that knows the request host.

---

## Log contract

Each log line is one JSON object in the Elastic Common Schema (ECS) format
([D17](../DECISIONS.md#d17--structured-logging-ecs-json-domain-events-through-a-probe-no-long-urls)).
The names below are fixed, so searches and alerts can rely on them. A dotted name is nested in the
JSON: `log.level` is `{"log": {"level": "INFO"}}`. How to switch to plain text, and how to filter
the JSON, is in [WORKFLOW.md](../WORKFLOW.md#logs-plain-text-or-json).

### Fields on every line

| Field | Example | Meaning |
|-------|---------|---------|
| `@timestamp` | `2026-11-01T07:24:28.692Z` | When the line was written, in UTC. |
| `log.level` | `INFO` | `INFO` for expected events, `ERROR` for faults on our side. |
| `log.logger` | `dev.azhar.url_shortener.service.AliasInstrumentation` | The class that wrote the line. |
| `message` | `Alias created` | Fixed text per event; see the table below. |
| `service.name` | `url-shortener` | From `spring.application.name`. |
| `ecs.version` | `8.11` | The ECS version the format follows. |
| `http.request.id` | `89ae75bb-…` (a UUID) | Only on lines written while a request is being handled. The server generates a new one per request; it is not read from or returned in a header. |

### Events

Our own code logs only these events, all from `AliasInstrumentation`:

| Event | `message` | `log.level` | Extra field |
|-------|-----------|-------------|-------------|
| Link created | `Alias created` | `INFO` | `alias`. Written only after the row is committed ([D18](../DECISIONS.md#d18--alias-created-is-logged-after-commit-via-an-event)). |
| Alias not found (404) | `Alias not found` | `INFO` | `alias` |
| Alias too long (400) | `Alias length exceeded` | `INFO` | `alias.length` (a number) |
| Broken link (500) | `Link broken` | `ERROR` | `alias` |

Successful redirects are not logged: redirect is the hot path, and a line per request would slow
it down and skew load tests.

Spring, Hibernate and Liquibase still write their own lines in the same JSON format, but those
aren't part of this contract.

### Never logged

- **The long URL**, anywhere in a line. It can carry tokens or personal data, and the alias is
  enough to find the row.
- **Request bodies.**
- **An over-long alias.** It comes from the caller and has no length limit, so only its length is
  logged.
