# Load tests (k6)

Three scenarios cover the traffic patterns we'd expect in production:

| Script | What it hits | Why |
|---|---|---|
| `auth.js` | `/api/auth/login` loop | Verifies the rate limiter trips at 30 rpm and login p95 stays healthy under contention |
| `browse.js` | `/api/sessions/upcoming`, `/api/users/me`, `/api/ledger/report` | Read-heavy endpoints the dashboard hits on load |
| `booking.js` | `/api/bookings` create + cancel | Write path that exercises the optimistic lock on `Boat.version` |

## Prerequisites

Install k6: https://k6.io/docs/get-started/installation/

```bash
# Linux
sudo apt-get install k6
# macOS
brew install k6
```

A running stack (against `http://localhost:8081` by default). For a realistic run, use the production overlay but point it at a staging DB — **do not run these against production**.

## Running

```bash
# Default: ~1 minute, ramping to 20 VUs
BASE_URL=http://localhost:8081/api k6 run loadtest/browse.js

# Auth rate-limiter stress (single IP, >30 rpm expected to return 429s)
BASE_URL=http://localhost:8081/api k6 run loadtest/auth.js

# Full booking lifecycle with optimistic-lock contention
BASE_URL=http://localhost:8081/api \
  STUDENT_EMAIL=student1@university.edu \
  STUDENT_PASSWORD=student123 \
  k6 run loadtest/booking.js
```

## Thresholds

Each script sets k6 thresholds that fail the run if hit:

- p(95) latency caps per endpoint class (100 ms reads / 300 ms writes)
- HTTP error rate < 1% (excluding the intentional 429s in `auth.js`)

CI does **not** run k6 — these are operator-run before releases and during capacity planning. See `PRODUCTION.md` → _Pre-release checklist_.
