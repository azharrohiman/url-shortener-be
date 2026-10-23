# Design — API Contract & Data Model

> The agreed contract. Code should conform to this; where current code diverges it's
> noted in [ROADMAP.md](./ROADMAP.md) and [DECISIONS.md](./DECISIONS.md).

_Last updated: 2026-10-23_

---

## API contract (MVP)

Three namespaces, deliberately separate so they never collide:
- **`/api/v1/...`** — the JSON management API.
- **`/{alias}`** — root-level redirect, kept short so short URLs stay short.
- **`/actuator/health`** — the health check (see 3. below). Two path segments, so it can never
  be mistaken for an alias.

### 1. Create a short link

```
POST /api/v1/links
Content-Type: application/json

{ "url": "https://example.com/some/very/long/path" }
```

**201 Created**, with `Location: <shortUrl>`. `shortUrl` is built from the `app.base-url`
property, not from the incoming request.
```json
{
  "alias":    "aB3kZ9q",
  "shortUrl": "http://localhost:8080/aB3kZ9q",
  "longUrl":  "https://example.com/some/very/long/path"
}
```

**400 Bad Request** — missing/blank URL, malformed URL, or longer than 2048 chars. Every failed
check is listed, so one field can appear more than once:
```json
{
  "status":  400,
  "title":   "The payload is invalid",
  "detail":  "The payload has one or more validation errors, please fix them and try again.",
  "validation": [
    { "field": "url", "message": "URL is not valid." },
    { "field": "url", "message": "URL must not exceed 2048 characters." }
  ]
}
```

### 2. Redirect

```
GET /{alias}
```

**302 Found** with `Location: <longUrl>` when the alias exists. No body.

**400 Bad Request** — the alias is longer than 64 characters, the width of `URL_ALIAS`. Rejected
before the database is queried, since no stored alias can match it:
```json
{
  "status":  400,
  "title":   "Alias too long",
  "detail":  "Alias length 65 exceeds limit of 64."
}
```

**404 Not Found** — no row has this alias. Aliases are case-sensitive:
```json
{
  "status":  404,
  "title":   "URL not found",
  "detail":  "No URL found with alias: aB3kZ9q"
}
```

**500 Internal Server Error** — a row exists, but its `LONG_URL` can't be turned into a valid
`Location` header. That is a fault on our side, not the caller's, so it is logged with the alias.
The body is fixed and doesn't echo the stored URL:
```json
{
  "status":  500,
  "title":   "Broken link",
  "detail":  "This link is broken on our side. Let the person who shared it know."
}
```

The alias is **stored** in the `URL_ALIAS` column (see [DECISIONS.md](./DECISIONS.md#d10--store-the-alias-reverses-d9) D10), so
resolution is a single indexed lookup: `findByUrlAlias(alias)` → **302** to `LONG_URL`, else **404**.
No decode/re-encode guard is needed — that was only required by the derived-alias model (D9).

302 (not 301) is intentional — see [DECISIONS.md](./DECISIONS.md#d2--redirect-with-http-302-not-301) D2.

### 3. Health check

```
GET /actuator/health
```

Provided by Spring Boot Actuator, not by our own controller. Answers one question: is the app
running **and** able to reach the database? Meant for whatever needs to know the app is ready —
a load-test script before it starts, or a hosting platform before it sends traffic.

**200 OK** — every check passed, including the database check:
```json
{
  "status": "UP",
  "groups": ["liveness", "readiness"]
}
```

**503 Service Unavailable** — at least one check failed (for example, Postgres can't be reached).
Same shape, with `"status": "DOWN"`.

- **Details are hidden on purpose.** The endpoint is public and anonymous, so the body never says
  *which* checks ran or why one failed — no database product, no disk space, no error messages.
  This is Spring Boot's default (`show-details: never`); keep it.
- **`groups`** lists the names of two extra checks Spring Boot 4 adds by default:
  `liveness` ("is the app running?") and `readiness` ("is it ready to take traffic?"), served at
  `/actuator/health/liveness` and `/actuator/health/readiness`. Hosting platforms use these at
  deployment time. Only their names appear here, not their contents.
- **Not an RFC 9457 error.** A `DOWN` response uses Actuator's own shape and content type, not the
  problem+json body described below. It is a status report, not an error in a request.
- **Only `health` is exposed** over HTTP — Spring Boot's default. Actuator's index page at
  `/actuator` is turned off (`management.endpoints.web.discovery.enabled: false`): it is a single
  path segment, so it would take that path away from `/{alias}`. `GET /actuator` is therefore an
  ordinary alias lookup, and returns the redirect's 404.

### Error responses

Every error from the create and redirect endpoints returns one body shape based on RFC 9457 ("Problem Details for
HTTP APIs"). `status`, `title` and `detail` are standard RFC members; `validation` is an extension
member, which the RFC allows:

| Field        | Type   | Always present? | Meaning |
|--------------|--------|-----------------|---------|
| `status`     | number | yes | The HTTP status code, repeated in the body (`400`, `404`, `500`). |
| `title`      | string | yes | Short summary of the kind of error. Fixed per error type, so it never varies between two occurrences of the same error. |
| `detail`     | string | yes | What caused this occurrence and, where the caller can act on it, how to fix it. |
| `validation` | array  | only on request-validation failures | One entry `{ "field", "message" }` per failed check. Omitted, not empty, on every other error. |

- Error bodies are sent with `Content-Type: application/problem+json`, the media type RFC 9457
  defines, so clients can tell an error body from a success body without parsing it.
- `validation` lists **every** failed check, so the same `field` can appear more than once.
  Entry order is not guaranteed; clients and tests must not rely on it.
- 500 bodies never carry internal detail (stack traces, stored values). The cause goes to the
  log, not the response.

---

## Data model (MVP)

Single table. Nothing to normalise yet; analytics (v2) would add a separate
`clicks` table in a one-to-many relationship rather than touching this one.

### `TB_URL_ALIAS`

> The alias is **stored** (D10). Generated aliases are `encode(id)`; custom aliases (F4, v2) will
> live in this **same column** under the unique index. The column is `VARCHAR(64)` — sized ahead
> for F4's custom slugs (max length 64 per [DECISIONS.md](./DECISIONS.md#d11--partition-the-customgenerated-namespace-by-character-class) D11) so custom aliases
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
[DECISIONS.md](./DECISIONS.md#d15--the-service-returns-a-domain-record-never-the-entity) D15.

| Layer | Type | Package | Purpose |
|-------|------|---------|---------|
| HTTP | `CreateUrlAliasRequestDto` / `CreateUrlAliasResponseDto` | `request` / `response` | The JSON contract above. Validation lives here. |
| Service | `ShortLink` (record: `alias`, `longUrl`) | `model` | What the service returns. Immutable, no persistence concern. |
| Persistence | `UrlAlias` (`@Entity`) | `entity` | Mirrors `TB_URL_ALIAS`. Never leaves the service layer. |

`UrlAliasMapper.toShortLink(entity)` (package `mapper`) is the single crossing between the bottom
two. The controller maps `ShortLink` → response DTO, and derives `shortUrl` there since it is the
only layer that knows the request host.
