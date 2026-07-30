# Design — API Contract & Data Model

> The agreed contract. Code should conform to this; where current code diverges it's
> noted in [ROADMAP.md](./ROADMAP.md) and [DECISIONS.md](./DECISIONS.md).

_Last updated: 2026-07-28_

---

## API contract (MVP)

Two namespaces, deliberately separate so they never collide:
- **`/api/v1/...`** — the JSON management API.
- **`/{alias}`** — root-level redirect, kept short so short URLs stay short.

### 1. Create a short link

```
POST /api/v1/links
Content-Type: application/json

{ "url": "https://example.com/some/very/long/path" }
```

**201 Created**
```json
{
  "alias":    "aB3kZ9q",
  "shortUrl": "http://localhost:8080/aB3kZ9q",
  "longUrl":  "https://example.com/some/very/long/path"
}
```

**400 Bad Request** — missing/blank URL, malformed URL, or longer than 2048 chars:
```json
{ "url": "URL must not exceed 2048 characters" }
```

> Current stub maps `POST /create` and echoes the URL. It will move to `POST /api/v1/links`
> and return the response shape above (build-order step 3).

### 2. Redirect

```
GET /{alias}
```

- **302 Found** with `Location: <longUrl>` when the alias exists.
- **404 Not Found** when it doesn't.

The alias is **stored** in the `URL_ALIAS` column (see [DECISIONS.md](./DECISIONS.md) D10), so
resolution is a single indexed lookup: `findByUrlAlias(alias)` → **302** to `LONG_URL`, else **404**.
No decode/re-encode guard is needed — that was only required by the derived-alias model (D9).

302 (not 301) is intentional — see [DECISIONS.md](./DECISIONS.md) D2.

---

## Data model (MVP)

Single table. Nothing to normalise yet; analytics (v2) would add a separate
`clicks` table in a one-to-many relationship rather than touching this one.

### `TB_URL_ALIAS`

> The alias is **stored** (D10). Generated aliases are `encode(id)`; custom aliases (F4, v2) will
> live in this **same column** under the unique index. The column is `VARCHAR(64)` — sized ahead
> for F4's custom slugs (max length 64 per [DECISIONS.md](./DECISIONS.md) D11) so custom aliases
> land without a migration. Generated codes use only a fraction of that width; `VARCHAR(n)` in
> Postgres doesn't pad, so the extra ceiling costs nothing. See R3.

| Column       | Type           | Notes                                            |
|--------------|----------------|--------------------------------------------------|
| `ID`         | `BIGINT`       | PK, default `nextval('SEQ_URL_ALIAS')`.          |
| `LONG_URL`   | `VARCHAR(2048)`| Original URL. Matches the DTO's 2048 max.        |
| `URL_ALIAS`  | `VARCHAR(64)`  | The short code. **Unique** (`UQ_URL_ALIAS`). For generated codes = `encode(ID)`. Width set by F4's 64-char custom-slug limit (D11). |
| `CREATED_AT` | `TIMESTAMPTZ`  | Defaults to `now()`.                             |

Plus sequence **`SEQ_URL_ALIAS`**, which feeds the PK; a generated alias is `encode(id)` of that PK,
computed before insert (SEQUENCE strategy) and written in the same INSERT.

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
