# Scenario — the unique-violation retry cascade (D10)

> A worked example stress-testing the **unique-violation retry** trade-off accepted in
> [DECISIONS.md](./DECISIONS.md) D10. It shows that catching the collision and "advancing to
> the next id" can cascade into a run of consecutive failures, examines how bad that really is
> (accidental vs. deliberate), and lists mitigations.
>
> **Status:** design concern for **F4 (custom aliases, v2)** — *not* an MVP blocker. The MVP
> ships generated-only (F1–F3), so there are no custom aliases to collide with and this cannot
> occur yet. Documented now because the storage decision (D10) is made now.

_Last updated: 2026-08-18_

---

## The premise being tested (from D10)

D10's accepted trade-off:

- `encode(id)` **never** collides with another *generated* alias — Sqids is a bijection over the
  id (see [D1](./DECISIONS.md#d1--short-codes-via-sqids-over-a-db-sequence)).
- `encode(id)` **can** collide with an existing *custom* alias, because both live in the same
  `URL_ALIAS` column under one unique index.
- The fix: catch the unique violation and advance to the next id.

The question: what happens when the collisions aren't isolated but *consecutive*?

---

## The scenario

Assume the table holds **10 rows**. Every row — generated or custom — consumes an id from
`SEQ_URL_ALIAS`, so the sequence has advanced to **10**.

- **2 generated** aliases, minted first, with ids **1 & 2** → their `URL_ALIAS` = `encode(1)`,
  `encode(2)`.
- **8 custom** aliases, ids **3–10**. Their *own* ids don't matter for the string — the user
  chose the string. The catch: the 8 chosen strings happen to equal `encode(11)…encode(18)`,
  i.e. the next eight ids the generator is about to reach.

### Stored rows

| ID (from `SEQ_URL_ALIAS`) | Type      | `URL_ALIAS` value | How the string arose                          |
|---------------------------|-----------|-------------------|-----------------------------------------------|
| 1                         | generated | `encode(1)`       | Sqids of the row's own id                     |
| 2                         | generated | `encode(2)`       | Sqids of the row's own id                     |
| 3                         | custom    | `encode(11)`      | user-chosen string; happens to equal Sqids(11)|
| 4                         | custom    | `encode(12)`      | user-chosen; equals Sqids(12)                 |
| 5                         | custom    | `encode(13)`      | user-chosen; equals Sqids(13)                 |
| 6                         | custom    | `encode(14)`      | user-chosen; equals Sqids(14)                 |
| 7                         | custom    | `encode(15)`      | user-chosen; equals Sqids(15)                 |
| 8                         | custom    | `encode(16)`      | user-chosen; equals Sqids(16)                 |
| 9                         | custom    | `encode(17)`      | user-chosen; equals Sqids(17)                 |
| 10                        | custom    | `encode(18)`      | user-chosen; equals Sqids(18)                 |

> Note: ids 3–10 were *consumed* by the custom rows, so `encode(3)…encode(10)` will simply never
> be minted — harmless gaps in the generated space, no collision there.

### The 11th insert (a generated alias)

The next non-custom insert draws `nextval` → **11**, computes `encode(11)`… which row 3 already
stored. Unique violation. Advance to the next id. Repeat.

| Attempt | id drawn (`nextval`) | `encode(id)` | Already stored as | Result                    |
|---------|----------------------|--------------|-------------------|---------------------------|
| 1       | 11                   | `encode(11)` | custom (row 3)    | `UQ_URL_ALIAS` violation → retry |
| 2       | 12                   | `encode(12)` | custom (row 4)    | violation → retry         |
| 3       | 13                   | `encode(13)` | custom (row 5)    | violation → retry         |
| 4       | 14                   | `encode(14)` | custom (row 6)    | violation → retry         |
| 5       | 15                   | `encode(15)` | custom (row 7)    | violation → retry         |
| 6       | 16                   | `encode(16)` | custom (row 8)    | violation → retry         |
| 7       | 17                   | `encode(17)` | custom (row 9)    | violation → retry         |
| 8       | 18                   | `encode(18)` | custom (row 10)   | violation → retry         |
| 9       | 19                   | `encode(19)` | — free —          | **INSERT succeeds**       |

So a single generated create did **8 failed inserts** and succeeded on the **9th** draw, burning
ids 11–18 (sequence gaps) along the way.

---

## Is this actually a problem? Two very different answers

### Accidentally — negligible, precisely *because* of Sqids

Sqids deliberately scatters consecutive ids ([D1](./DECISIONS.md#d1--short-codes-via-sqids-over-a-db-sequence)),
so `encode(11)…encode(18)` are eight unrelated-looking strings. For eight *randomly chosen*
custom aliases to land on exactly those eight upcoming ids is astronomically unlikely. As an
accidental event, the cascade won't happen; an occasional **single** collision (1 retry) is the
realistic worst case, and that's cheap.

### Deliberately — a genuine griefing / DoS vector

This is where the scenario has teeth. D1 is honest that Sqids is **reversible obfuscation, not a
secret** — the alphabet/salt are recoverable. An attacker who recovers them and observes the
current sequence position can **pre-register the next N generated codes as custom aliases**,
forcing a retry storm on every subsequent generated insert. The contrived table above is exactly
what a motivated attacker can *engineer* even though nature never would.

---

## The concurrency angle (pushing back on the framing)

Multiple concurrent requests do **not** make two *generators* collide with each other. Every create
calls `nextval('SEQ_URL_ALIAS')` itself (see D10), and `nextval` is atomic and never returns the same
value twice — even inside a rolled-back transaction — so each thread holds a distinct id and no two
in-flight generated inserts ever target the same string. Concurrency doesn't multiply the
*custom* collision — it only increases the aggregate count of wasted sequence values when a squat
run exists.

What concurrency **does** expose is the retry *implementation*:

1. **"Advance to the next id" must mean `nextval`, never `id + 1`.** Manually incrementing can
   hand you an id the sequence will later mint on its own → a **future primary-key collision**,
   which is strictly worse than the alias collision you were fixing. Always re-draw from the
   sequence.
2. **A unique violation aborts the Postgres transaction.** Each retry needs a fresh
   transaction / savepoint — you can't loop inside the dead one.
3. **Bound the retries.** A naive `while (true)` under an adversarial squat run is an unbounded
   loop. Cap it (and surface an error / jump the sequence) rather than spin.

---

## Mitigation options

1. **Partition the namespace so the collision is structurally impossible** *(recommended)*.
   Keep generated and custom aliases in the **same column** (so GET stays one indexed lookup —
   D10's whole benefit) but enforce that a custom alias can **never equal a generatable string**:
   e.g. require custom slugs to contain a character outside the Sqids alphabet, or to exceed the
   max generated length. The unique index still guards custom-vs-custom; generated-vs-custom can't
   happen. **The retry mechanism — and the attack — disappear entirely.**
2. **Bounded retry with a sequence jump.** Keep D10's retry but cap attempts; on exhaustion, jump
   the sequence forward past the squatted band. Simpler to ship, but still leaves an attacker a
   (bounded) way to waste ids and slow creates.
3. **Do nothing for MVP** *(current, valid)*. Generated-only means zero customs and zero
   collisions today. Revisit at F4 — but decide *before* customs ship, because option 1 is a
   validation rule that's painful to retrofit once non-conforming customs exist.

---

## Recommendation

Adopt **option 1** when F4 (custom aliases) is designed, and make it a hard input-validation rule
on the custom slug. It preserves every D10 benefit, dissolves the retry trade-off, and closes the
squatting attack in one move. Until then the MVP is unaffected.

> **Follow-ups (not yet applied):** this likely warrants a new risk entry (e.g. **R7 — custom
> alias can squat a generated code**) and a note appended to [D10](./DECISIONS.md#d10--store-the-alias-reverses-d9)
> pointing here. I've left DECISIONS.md untouched pending your call.
