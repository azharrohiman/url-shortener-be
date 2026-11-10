# Back-of-the-Envelope Estimation

> Rough numbers for the load and storage a URL shortener at this scale would face.
> The goal is the right order of magnitude, not precision: round freely, and keep each
> step simple enough to check by hand.

_Last updated: 2026-11-10_

---

## Assumptions

| # | Assumption | Value |
|---|------------|-------|
| A1 | New short URLs created per day | **50 million** |
| A2 | Read-to-write ratio (redirects per link created) | **15 : 1** |
| A3 | How long the service runs (and keeps every record) | **10 years** |
| A4 | Average long-URL length | **100 characters** (≈ 100 bytes, 1 byte per ASCII character) |

Handy constant: **1 day = 24 × 60 × 60 = 86,400 seconds**, which we round to **~100,000**
when doing mental maths.

---

## 1. Write traffic

How many short URLs are created each second (`POST /api/v1/links`).

```
50,000,000 URLs / day ÷ 86,400 seconds / day  ≈  578.7  →  ~580 writes / second
```

## 2. Read traffic

How many redirects are served each second (`GET /{alias}`).

```
580 writes / second × 15  =  8,700 reads / second
```

## 3. Total records over 10 years

```
50,000,000 / day × 365 days × 10 years  =  182,500,000,000  →  ~182.5 billion records
```

## 4. Storage

```
182.5 billion records × 100 bytes  =  18,250,000,000,000 bytes  →  ~18.25 TB
```

---

## Summary

| Metric | Calculation | Result |
|--------|-------------|--------|
| Writes per second | 50M ÷ 86,400 | **~580 / s** |
| Reads per second | 580 × 15 | **~8,700 / s** |
| Records over 10 years | 50M × 365 × 10 | **~182.5 billion** |
| Storage over 10 years | 182.5B × 100 bytes | **~18.25 TB** |

---

## What the numbers tell us

- **Reads dominate.** At 15 reads for every write, the redirect path carries most of the
  load. That is the path to measure first and the natural place for a cache.
- **Storage is a lower bound.** The 18.25 TB counts only the long URL. Each row also holds
  the alias, timestamps, and index entries, so real disk usage will be higher.
- **These are averages.** Traffic is not spread evenly across the day, so peak load will
  sit above these per-second figures.
