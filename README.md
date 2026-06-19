# Rowing Club Management System

An enterprise-grade web application for managing a rowing club's sessions, members, bookings, and finances. Spring Boot backend, Next.js frontend, PostgreSQL database. Ships with JWT auth, role-based access, credit-ledger booking with time-of-day rules, an auto-scheduler for coastal boats, a cancellation-approval workflow, immutable audit logging, and full dark-mode support.

Deploy the whole stack with `docker compose up`.

---

## Features at a glance

- **Daily & weekly session planner** (admin) — drag-drop members between boats, add/remove boats, bulk-approve, copy a day or a whole week to another date
- **RSVP & booking** (student / club member) — role-based time windows: students can only book after 16:00, members before 16:00 are restricted to tomorrow's sessions; basic-training gate on advanced boats
- **Financial ledger** — every credit/debit is an immutable row with running balance; expiring credits with earliest-expiration display; refund on approved cancellation
- **Cancellation workflow** — user requests cancel, admin approves (refund) or denies (booking restored). No silent refunds.
- **Auto-scheduler** — assigns availability-marked users to 4-person coastal boats; never mixes students and club members; skips users without credit or basic training
- **Admin members directory** — search by name/email, filter by role, sort by name / lessons / credits / nearest expiration / training status, shortcut to per-user ledger
- **Account pages** — `/account/[id]` for owner-or-admin: lessons attended, credits, basic-training status, and full **reservation history** (upcoming + past). Club admins can change a member's **role** and **member type** and mark training complete inline
- **Admin audit trail** — every POST / PUT / PATCH / DELETE call is logged with user, endpoint, timestamp
- **Full dark mode** — persisted, no FOUC on reload, `prefers-reduced-motion` respected
- **Responsive UI** — mobile → xl, animated page transitions, animated mobile nav menu with content-push effect
- **Tests** — 268 backend (JUnit + MockMvc + `@DataJpaTest`) with JaCoCo ≥70% line coverage; 107 frontend (Jest + Testing Library) with Jest coverage thresholds

---

## Tech stack

| Layer | Stack |
|---|---|
| Backend | Java 21, Spring Boot 3.3.5, Spring Security, Spring Data JPA, Lombok, AOP |
| Database | PostgreSQL 16 (H2 in PostgreSQL mode for tests) |
| Auth | JWT (HS384, JJWT 0.12.6), 15-minute access / 7-day refresh with rotation |
| Frontend | Next.js 14.2 (App Router, standalone output), React 18, TypeScript 5 |
| Styling | Tailwind 3 with `darkMode: 'class'`, custom palettes, pure-CSS animations |
| State | React Context (Auth, Theme, Dialog, Settings) |
| HTTP | axios with bearer-attach + auto-refresh-on-401 interceptors |
| Testing | JUnit 5 + Spring Boot Test + spring-security-test + H2 (backend); Jest 29 + @testing-library/react (frontend) |
| Coverage | JaCoCo ≥70% line (backend); Jest branches ≥25 / functions ≥30 / lines ≥40 / statements ≥40 (frontend) |
| Deployment | Docker Compose — 3 services (Postgres, Spring Boot, Next.js) |

---

## Quick start

**Requirements**: Docker, Docker Compose, Node 20, and Java 21 + Maven 3.9 (only needed if you want to run tests locally).

```bash
# 1. Build the backend image
DOCKER_BUILDKIT=0 docker build -t rowing-backend -f backend/Dockerfile backend/

# 2. Build the frontend — Next.js output must exist before docker build
cd frontend
rm -rf .next
NEXT_PUBLIC_API_URL=http://localhost:8081/api npx next build
cd ..
DOCKER_BUILDKIT=0 docker build -t rowing-frontend -f frontend/Dockerfile frontend/

# 3. Bring everything up
docker compose up -d

# 4. Open the app
open http://localhost:3000
```

**On first boot** (any non-`prod` profile), `DataSeeder` auto-seeds 1 platform super-admin and 3 clubs, each with 1 club admin, 2 trainers, 30 members, ~13 days of sessions (6 boats apiece), and credit balances for every member (see [Demo accounts](#demo-accounts) below).

To reset the database: `docker compose down -v && docker compose up -d`.

---

## Demo accounts

The app is **multi-tenant**: the seeder (`DataSeeder`, active in every non-`prod` profile) creates one platform `SUPERADMIN` plus **3 clubs**, each fully populated. All demo passwords are plain for ease of testing.

The 3 seeded clubs are `Riverside Rowing Club`, `University Rowing Team`, and `Metropolitan Rowing Association`. Per-club emails use the club name lowercased with spaces removed (e.g. `riversiderowingclub.com`).

| Role | Email pattern | Password | Count |
|---|---|---|---|
| Super admin (platform) | `superadmin@rowingclub.com` | `superadmin123` | 1 |
| Club admin | `admin@<club>.com` — e.g. `admin@riversiderowingclub.com` | `admin123` | 1 / club |
| Trainer | `trainer1@<club>.com`, `trainer2@<club>.com` | `trainer123` | 2 / club |
| Member | `member1@<club>.com` … `member30@<club>.com` | `member123` | 30 / club |

Each member starts with a random 5–20 credits and a 3-month expiration, a `memberType` of `STUDENT` / `RECREATIONAL` / `DEFAULT` (cycled), and 27 of 30 have basic training marked complete; the rest start untrained (visible to admins on `/account/[id]`).

**Roles**: `SUPERADMIN` (manages clubs across the platform) › `CLUB_ADMIN` (manages one club) › `TRAINER` (admin-area access within a club) › `MEMBER` (books sessions). Public self-registration always creates a plain `MEMBER` — elevated roles are provisioned by an admin.

---

## Project structure

```
.
├── backend/                              # Spring Boot 3.3.5 / Java 21
│   ├── src/
│   │   ├── main/java/com/rowingclub/backend/
│   │   │   ├── controller/               # 8 REST controllers (Auth, User, Booking, Session, Ledger, Availability, Admin, Settings)
│   │   │   ├── service/                  # 8 services with business rules
│   │   │   ├── entity/                   # 10 JPA entities
│   │   │   ├── repository/               # Spring Data JPA interfaces
│   │   │   ├── security/                 # JwtService + JwtAuthenticationFilter
│   │   │   ├── aspect/                   # AuditAspect
│   │   │   ├── config/                   # SecurityConfig + DataSeeder
│   │   │   ├── exception/                # BusinessException + GlobalExceptionHandler
│   │   │   ├── enums/                    # Role, BookingStatus, BoatType, SessionStatus
│   │   │   └── dto/                      # ~20 request/response DTOs
│   │   ├── main/resources/
│   │   │   └── application.yml           # Main config
│   │   └── test/                         # 28 test classes, 268 tests
│   ├── Dockerfile
│   └── pom.xml
├── frontend/                             # Next.js 14 / TypeScript
│   ├── src/
│   │   ├── app/                          # App Router — 17 routes
│   │   │   ├── page.tsx, layout.tsx
│   │   │   ├── login, register, dashboard, booking, ledger, availability, settings, account/[id]
│   │   │   └── admin/{planner, members, ledger, cancellations, analytics, logs, scheduler, messages, settings}
│   │   ├── components/                   # Navbar, ProtectedRoute, PageTransition
│   │   ├── context/                      # AuthContext, ThemeContext, DialogContext, SettingsContext
│   │   ├── lib/                          # api.ts (axios + interceptors), dateUtils.ts
│   │   ├── types/                        # Shared TS interfaces
│   │   └── __tests__/                    # 21 Jest suites, 107 tests
│   ├── Dockerfile
│   ├── jest.config.js
│   ├── next.config.js
│   ├── tailwind.config.js
│   └── package.json
├── docker-compose.yml                    # Postgres + backend + frontend
├── README.md                             # You are here
└── ARCHITECTURE.md                       # Deep technical reference
```

---

## Running tests

### Backend

```bash
cd backend
mvn test        # Runs all 268 tests + JaCoCo (fails if line coverage < 70%)
open target/site/jacoco/index.html   # Coverage report
```

If you need to run tests in the Docker builder image (avoids local JDK setup):

```bash
docker run --rm \
  -v "$(pwd)/backend:/app" \
  -v "rowing_m2:/root/.m2" \
  -w /app \
  rowing-build-cache:latest \
  mvn test
```

### Frontend

```bash
cd frontend
npm install
npm test                   # Runs 107 tests without coverage
npm run test:coverage      # With coverage thresholds enforced
open coverage/lcov-report/index.html
```

---

## Ports

| Service | Host port | Container port |
|---|---|---|
| PostgreSQL | `5432` | `5432` |
| Backend (Spring Boot) | **`8081`** | `8080` |
| Frontend (Next.js) | `3000` | `3000` |

The backend is deliberately on host port **8081** (not 8080) to avoid conflicts with common proxy/debug tools (`mitmdump`, etc.) that default to 8080. Any direct API consumer (curl, Postman, or a standalone frontend build) must use `http://localhost:8081/api`.

---

## Configuration

### Environment variables (set in `docker-compose.yml`)

| Variable | Applies to | Default | Notes |
|---|---|---|---|
| `SPRING_DATASOURCE_URL` | backend | `jdbc:postgresql://postgres:5432/rowingclub` | |
| `SPRING_DATASOURCE_USERNAME` | backend | `rowing` | |
| `SPRING_DATASOURCE_PASSWORD` | backend | `rowing_secret_2024` | — replace before production |
| `JWT_SECRET` | backend | baked-in placeholder | base64-encoded, ≥32 bytes |
| `STUDENT_BOOKING_HOUR` | backend | `16` | Default cutoff; can be overridden at runtime via admin settings |
| `APP_SECURITY_AUTH_RATE_LIMIT_PER_MINUTE` | backend | `10` | Auth requests/minute/IP before `429` (login, register, refresh) |
| `JAVA_TOOL_OPTIONS` | backend | `-Duser.timezone=Europe/Istanbul` | Enforces timezone across JVM |
| `TZ` | backend | `Europe/Istanbul` | Container-level |
| `NEXT_PUBLIC_API_URL` | frontend | (baked at build time) | **Must be set during `next build`** — not read at container runtime |

### Runtime admin toggles (via `/admin/settings`)

Every toggle is stored in the `app_settings` table as a string. Flip from the admin UI or via `PUT /api/admin/settings/{key}` with `{ "value": "true" | "false" }`.

| Key | Default | Effect |
|---|---|---|
| `student_booking_hour` | `"16"` | Cutoff hour (0–23). Students book after; club members book-before rules kick in. |
| `student_next_day_only` | `"false"` | When `true`, students can only book sessions for tomorrow |
| `allow_cancellations` | `"true"` | When `false`, cancellation requests are blocked |
| `booking_hour_disabled` | `"false"` | Master switch — when `true`, bypasses all time-of-day booking restrictions |
| `disable_availability` | `"false"` | When `true`, Availability tab is hidden and `/availability` redirects to `/dashboard` |
| `show_booked_members` | `"true"` | When `false`, members can't see who else is on a boat |

---

## Common tasks

**Create an admin session**: log in as admin → `/admin/planner` → "New Session" button → pick date/start/end → Create → click the check icon on the session to approve it.

**Give a member credits**: log in as admin → `/admin/members` → click the wallet icon on a member's card → "Add Credit" → enter amount and optional expiration date.

**Approve a cancellation request**: log in as admin → `/admin/cancellations` → Approve or Deny buttons.

**Change the default booking hour**: log in as admin → `/admin/settings` → adjust "Student booking hour" dropdown → Save. Takes effect on the next request — no restart.

**Reset the database**: `docker compose down -v && docker compose up -d`. Volume `pgdata` is destroyed, DataSeeder re-runs.

**Change the frontend API URL**: rebuild the image with a different `NEXT_PUBLIC_API_URL` at `next build` time. The env var in `docker-compose.yml` is cosmetic — Next.js bakes the value into the JS bundle at build.

---

## Contributing / development

```bash
# Backend dev loop (hot-reload with Spring Boot devtools if added, else restart manually)
cd backend
mvn spring-boot:run         # Runs on 8080 locally; point frontend NEXT_PUBLIC_API_URL to http://localhost:8080/api

# Frontend dev loop
cd frontend
npm install
npm run dev                 # Hot-reload dev server on http://localhost:3000
```

Local dev uses the same PostgreSQL container — just make sure `docker compose up -d postgres` is running first.

---

## Production deployment

For a real deployment (HTTPS, secret externalization, rate limiting, health checks, runbook, incident playbook), follow **[PRODUCTION.md](./PRODUCTION.md)**. Short version:

```bash
cp .env.example .env && $EDITOR .env          # Fill in real secrets
docker build -t rowing-backend:prod -f backend/Dockerfile backend/
cd frontend && rm -rf .next && \
  NEXT_PUBLIC_API_URL=https://api.your-domain.example.com/api npx next build && cd ..
docker build -t rowing-frontend:prod -f frontend/Dockerfile frontend/
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

Production mode (`SPRING_PROFILES_ACTIVE=prod`) enables:
- JWT-secret-is-default fail-fast guard
- `DataSeeder` is disabled (start with a real admin, not demo accounts)
- Hibernate `ddl-auto: validate` (schema changes must be explicit)
- JSON structured logging
- Docker healthchecks on backend + frontend
- Postgres port not exposed to the host (Docker network only)

HTTPS termination + security headers + auth rate-limiting (defence in depth) via the included **[nginx/nginx.conf.example](./nginx/nginx.conf.example)**.

## Further reading

For the full technical reference — every API endpoint, database constraint, security detail, business rule, animation keyframe, and deployment nuance — see **[ARCHITECTURE.md](./ARCHITECTURE.md)**.

---

## License

Unlicensed / internal.
