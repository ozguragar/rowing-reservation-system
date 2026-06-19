# Rowing Club Reservation System

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
- **Tests** — 300+ backend (JUnit + MockMvc + `@DataJpaTest`) with JaCoCo ≥70% line coverage; 100+ frontend (Jest + Testing Library) with Jest coverage thresholds

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

**Requirements**: Docker, Docker Compose.

```bash
# 1. Build both images
docker compose build

# 2. Bring everything up
docker compose up -d

# 3. Open the app
open http://localhost:3001
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
│   │   │   ├── controller/               # REST controllers (Auth, User, Booking, Session, Ledger, Availability, Admin, Settings)
│   │   │   ├── service/                  # Business logic services
│   │   │   ├── entity/                   # JPA entities
│   │   │   ├── repository/               # Spring Data JPA interfaces
│   │   │   ├── security/                 # JwtService + JwtAuthenticationFilter + rate limit
│   │   │   ├── aspect/                   # AuditAspect
│   │   │   ├── config/                   # SecurityConfig + DataSeeder
│   │   │   ├── exception/                # BusinessException + GlobalExceptionHandler
│   │   │   ├── enums/                    # Role, BookingStatus, BoatType, SessionStatus
│   │   │   └── dto/                      # Request/response DTOs
│   │   ├── main/resources/
│   │   │   ├── application.yml           # Main config
│   │   │   └── db/migration/             # Flyway migrations
│   │   └── test/                         # 30+ test classes, 300+ tests
│   ├── Dockerfile                        # Self-contained multi-stage build
│   └── pom.xml
├── frontend/                             # Next.js 14 / TypeScript
│   ├── src/
│   │   ├── app/                          # App Router — 17+ routes
│   │   │   ├── page.tsx, layout.tsx
│   │   │   ├── login, register, dashboard, booking, ledger, availability, settings, account/[id]
│   │   │   └── admin/{planner, members, ledger, cancellations, analytics, logs, scheduler, messages, settings}
│   │   ├── components/                   # Navbar, ProtectedRoute, PageTransition
│   │   ├── context/                      # AuthContext, ThemeContext, DialogContext, SettingsContext
│   │   ├── lib/                          # api.ts (axios + interceptors), dateUtils.ts
│   │   ├── types/                        # Shared TS interfaces
│   │   └── __tests__/                    # 20+ Jest suites, 100+ tests
│   ├── Dockerfile                        # Self-contained multi-stage build (NEXT_PUBLIC_API_URL as build arg)
│   ├── jest.config.js
│   ├── next.config.js
│   ├── tailwind.config.js
│   └── package.json
├── docker-compose.yml                    # Postgres + backend + frontend (uses .env)
├── docker-compose.prod.yml               # Production overlay (healthchecks, secrets from .env)
├── .env.example                          # Template for environment secrets
├── README.md                             # You are here
├── ARCHITECTURE.md                       # Deep technical reference
├── PRODUCTION.md                         # Deployment runbook
├── CHANGELOG.md                          # Release history
├── nginx/
│   └── nginx.conf.example                # Reverse-proxy config for HTTPS
├── loadtest/                             # k6 load-test scripts
└── .github/workflows/ci.yml              # CI pipeline (test + docker build + Trivy scan)
```

---

## Running tests

### Backend

```bash
cd backend
mvn test        # Runs all tests + JaCoCo (fails if line coverage < 70%)
```

### Frontend

```bash
cd frontend
npm install
npm test                   # Runs tests without coverage
npm run test:coverage      # With coverage thresholds enforced
```

---

## Ports

| Service | Host port | Container port |
|---|---|---|
| PostgreSQL | `5432` | `5432` |
| Backend (Spring Boot) | **`8081`** | `8080` |
| Frontend (Next.js) | **`3001`** | **`3001`** |

---

## Configuration

### Environment variables

| Variable | Applies to | Default | Notes |
|---|---|---|---|
| `POSTGRES_DB` | postgres | `rowingclub` | |
| `POSTGRES_USER` | postgres | `rowing` | |
| `POSTGRES_PASSWORD` | postgres | `rowing_secret_2024` | Replace before production |
| `SPRING_DATASOURCE_URL` | backend | `jdbc:postgresql://postgres:5432/rowingclub` | |
| `SPRING_DATASOURCE_USERNAME` | backend | `rowing` | |
| `SPRING_DATASOURCE_PASSWORD` | backend | `rowing_secret_2024` | Replace before production |
| `JWT_SECRET` | backend | baked-in placeholder | base64-encoded, ≥32 bytes |
| `STUDENT_BOOKING_HOUR` | backend | `16` | Default cutoff; overridable via admin settings |
| `APP_CORS_ALLOWED_ORIGINS` | backend | `http://localhost:3001` | Comma-separated |
| `JAVA_TOOL_OPTIONS` | backend | `-Duser.timezone=Europe/Istanbul` | |
| `TZ` | backend | `Europe/Istanbul` | |
| `NEXT_PUBLIC_API_URL` | frontend | `http://localhost:8081/api` | Baked at build time — set via build arg |

### Runtime admin toggles (via `/admin/settings`)

| Key | Default | Effect |
|---|---|---|
| `student_booking_hour` | `"16"` | Cutoff hour (0–23). Students book after; club members book-before rules kick in. |
| `student_next_day_only` | `"false"` | When `true`, students can only book sessions for tomorrow |
| `allow_cancellations` | `"true"` | When `false`, cancellation requests are blocked |
| `booking_hour_disabled` | `"false"` | Master switch — when `true`, bypasses all time-of-day booking restrictions |
| `disable_availability` | `"false"` | When `true`, Availability tab is hidden |
| `show_booked_members` | `"true"` | When `false`, members can't see who else is on a boat |

---

## Production deployment

For a real deployment follow **[PRODUCTION.md](./PRODUCTION.md)**. Short version:

```bash
cp .env.example .env && $EDITOR .env
docker compose -f docker-compose.yml -f docker-compose.prod.yml build
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

Place behind the included **[nginx/nginx.conf.example](./nginx/nginx.conf.example)** for HTTPS termination.

---

## License

MIT License — see [LICENSE](./LICENSE).
