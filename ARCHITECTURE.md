# Rowing Club Management — Architecture & Technical Reference

**Status**: Implementation reference as of this revision. All information verified against the actual codebase — no speculative features.

## Table of contents

1. [System overview](#1-system-overview)
2. [Backend architecture](#2-backend-architecture)
3. [Database schema](#3-database-schema)
4. [Security model](#4-security-model)
5. [REST API reference](#5-rest-api-reference)
6. [Business rules (services)](#6-business-rules-services)
7. [Cross-cutting concerns](#7-cross-cutting-concerns)
8. [Frontend architecture](#8-frontend-architecture)
9. [UI / UX details](#9-ui--ux-details)
10. [Deployment & operations](#10-deployment--operations)
11. [Testing](#11-testing)
12. [Known limitations & future work](#12-known-limitations--future-work)

---

## 1. System overview

Three-tier architecture: **browser → Next.js SSR/SPA → Spring Boot REST API → PostgreSQL**. All services run in Docker containers on a single Docker-managed network.

```
┌──────────────────────────────────────────────────────────────────────┐
│                          HOST MACHINE                                │
│                                                                      │
│   localhost:3000 ─────────► ┌─────────────────┐                      │
│                             │ rowing-frontend │  Next.js 14          │
│                             │   node:20       │  Static + SSR        │
│                             │   PORT 3000     │  Tailwind, RSC       │
│                             └────────┬────────┘                      │
│                                      │                               │
│                                      │ HTTPS (dev: HTTP)             │
│                                      │ JWT in Authorization header   │
│                                      ▼                               │
│   localhost:8081 ─────────► ┌─────────────────┐                      │
│                             │ rowing-backend  │  Spring Boot 3.3.5   │
│                             │  temurin-21     │  Java 21             │
│                             │  PORT 8080      │  TZ=Europe/Istanbul  │
│                             └────────┬────────┘                      │
│                                      │                               │
│                                      │ JDBC                          │
│                                      ▼                               │
│   localhost:5432 ─────────► ┌─────────────────┐                      │
│                             │  rowing-db      │  PostgreSQL 16       │
│                             │  postgres:16    │  Persistent volume   │
│                             │  PORT 5432      │  (pgdata)            │
│                             └─────────────────┘                      │
└──────────────────────────────────────────────────────────────────────┘
```

**Request flow**:
1. User logs in via `POST /api/auth/login` → receives `accessToken` (15 min) + `refreshToken` (7 days) in JSON
2. Frontend stores both in `localStorage` plus the `user` DTO
3. Every subsequent request to `/api/**` is intercepted by the axios request interceptor and gets `Authorization: Bearer {accessToken}`
4. On the backend, `JwtAuthenticationFilter` parses the token, validates signature + expiry, and populates `SecurityContextHolder` with a `UsernamePasswordAuthenticationToken` containing the user's role
5. `SecurityConfig` enforces per-path authorization (public / authenticated / admin)
6. If the access token is expired (backend returns 401), the axios response interceptor calls `POST /api/auth/refresh`, stores the new token pair, and retries the original request. If refresh fails, all tokens are cleared and the user is redirected to `/login`.

**Timezone**: Every `LocalDate.now()` / `LocalTime.now()` in `BookingService` uses `ZoneId.of("Europe/Istanbul")` explicitly. The backend container also sets `JAVA_TOOL_OPTIONS=-Duser.timezone=Europe/Istanbul` and `TZ=Europe/Istanbul` for every other call site. This keeps booking cutoffs consistent regardless of the host OS timezone.

---

## 2. Backend architecture

### Package layout

```
com.rowingclub.backend
├── RowingBackendApplication.java   # @SpringBootApplication entry point
├── aspect/
│   └── AuditAspect.java            # @Around logging of all mutating HTTP verbs
├── config/
│   ├── SecurityConfig.java         # HTTP security, JWT filter chain, CORS
│   ├── DataSeeder.java             # @PostConstruct seeding of demo data
│   └── AppConfig.java              # @Bean declarations (PasswordEncoder, etc.)
├── controller/                     # 8 REST controllers (see §5)
├── dto/                            # ~20 request/response DTOs
├── entity/                         # 10 JPA entities (see §3)
├── enums/                          # Role, BookingStatus, BoatType, SessionStatus
├── exception/
│   ├── BusinessException.java      # 400-family domain errors
│   ├── ResourceNotFoundException.java
│   └── GlobalExceptionHandler.java # @RestControllerAdvice mapping to HTTP
├── repository/                     # Spring Data JPA interfaces
├── security/
│   ├── JwtService.java             # Sign, parse, extract claims
│   └── JwtAuthenticationFilter.java # OncePerRequestFilter for every request
└── service/                        # 8 services with business rules (see §6)
```

### Tech stack versions

From `backend/pom.xml`:

| Dependency | Version |
|---|---|
| `spring-boot-starter-parent` | 3.3.5 |
| Java runtime | 21 (Eclipse Temurin) |
| `spring-boot-starter-web` | (inherited) |
| `spring-boot-starter-data-jpa` | (inherited) |
| `spring-boot-starter-security` | (inherited) |
| `spring-boot-starter-validation` | (inherited) |
| `spring-boot-starter-aop` | (inherited) |
| `jjwt-api` / `jjwt-impl` / `jjwt-jackson` | 0.12.6 |
| `org.projectlombok:lombok` | (inherited, optional) |
| `postgresql` (runtime) | (inherited) |
| `com.h2database:h2` (test) | (inherited) |
| `spring-security-test` (test) | (inherited) |
| `jacoco-maven-plugin` | 0.8.12 |

### `application.yml` reference

Production config (`src/main/resources/application.yml`):

```yaml
spring:
  datasource:
    url:      ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/rowingclub}
    username: ${SPRING_DATASOURCE_USERNAME:rowing}
    password: ${SPRING_DATASOURCE_PASSWORD:rowing_secret_2024}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
  jpa:
    hibernate:
      ddl-auto: update                       # Creates/updates schema; never drops
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  jackson:
    serialization:
      write-dates-as-timestamps: false       # Writes as ISO-8601 strings
    date-format: yyyy-MM-dd'T'HH:mm:ss

server:
  port: 8080                                  # Internal port; Docker maps to host 8081

app:
  jwt:
    secret: ${JWT_SECRET:<base64 default>}
    access-token-expiration:  900000          # 15 min in ms
    refresh-token-expiration: 604800000       # 7 days in ms
  booking:
    student-booking-hour: ${STUDENT_BOOKING_HOUR:16}   # Default cutoff (overridable at runtime via app_settings)
    student-next-day-only: false
  cors:
    allowed-origins: http://localhost:3000
```

Test profile (`src/test/resources/application-test.yml`):

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop                   # Fresh schema per test class
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect
app:
  jwt:
    secret: dGVzdC1zZWNyZXQta2V5LWZvci1yb3dpbmctY2x1Yi10ZXN0cy0yMDI0LXNlY3VyZQ==
    access-token-expiration: 900000
    refresh-token-expiration: 604800000
  booking:
    student-booking-hour: 16
```

### `DataSeeder` — seed rules

Runs once at startup if `userRepository.count() == 0`. Idempotent otherwise (early-return).

| Seed set | Count | Details |
|---|---|---|
| Admins | 2 | `admin1@rowingclub.com`, `admin2@rowingclub.com` / `admin123` |
| Students | 50 | `student{1..50}@university.edu` / `student123`; 45/50 finished basic training; 10/50 on school team |
| Club members | 40 | `member{1..40}@rowingclub.com` / `member123`; 36/40 finished basic training |
| Credits | 92 rows | Each non-admin user: random 5–20 credits, 3-month expiration |
| Sessions | 108 | 14 days (skipping Mondays), each day has 4 morning (06:20, 07:20, 08:20, 09:20) + 5 afternoon (16:20, 17:20, 18:20, 19:20, 20:20) sessions |
| Boats | 648 | Each session gets 6 boats: `Coastal 4x A` (basic-training only), `Coastal 4x B`, `Coastal 2x A/B`, `Coastal 1x A/B` |
| App settings | 3 | `student_next_day_only=false`, `show_booked_members=true`, `student_booking_hour=16` |

---

## 3. Database schema

### Entities

| Table | Primary key | Notable fields | Relationships | Constraints |
|---|---|---|---|---|---|
| `clubs` | `id BIGINT` | `name`, `created_at`, `feature_availability_module`, `feature_cancellation_requests`, `feature_auto_scheduler`, `feature_show_booked_members` | — | — |
| `users` | `id BIGINT` | `email (unique)`, `password_hash`, `role (SUPERADMIN/CLUB_ADMIN/TRAINER/MEMBER)`, `member_type (STUDENT/RECREATIONAL)`, `is_finished_basic_training`, `is_on_school_team`, `is_cox`, `lessons_attended`, `refresh_token` | `@ManyToOne club` | `email` UNIQUE, `role` NOT NULL |
| `rowing_sessions` | `id BIGINT` | `date`, `start_time`, `end_time`, `status (DRAFT/APPROVED)` | `@ManyToOne club` (nullable=false) | `club_id` NOT NULL |
| `boats` | `id BIGINT` | `type (COASTAL/OLYMPIC)`, `capacity`, `is_basic_training_boat`, `current_bookings`, `name`, `version (Long, @Version)` | `@ManyToOne session` | Optimistic lock via `@Version` — prevents double-booking race |
| `bookings` | `id BIGINT` | `status (AUTO_ASSIGNED/MANUAL/CANCELLATION_REQUESTED/CANCELED)`, `created_at` | `@ManyToOne user`, `boat`, `session` | `(user_id, session_id)` UNIQUE; `bookings_status_check` CHECK constraint over the 4 enum values |
| `financial_ledger` | `id BIGINT` | `amount DECIMAL`, `reason`, `running_balance DECIMAL`, `timestamp`, `expiration_date` (nullable) | `@ManyToOne user` | Rows are immutable after creation (except `expiration_date` via admin endpoint) |
| `user_availability` | `id BIGINT` | — | `@ManyToOne user`, `session` | `(user_id, session_id)` effectively unique (enforced by service check) |
| `audit_log` | `id BIGINT` | `user_email`, `action`, `endpoint`, `timestamp`, `details` | — | — |
| `admin_messages` | `id BIGINT` | `ledger_entry_id`, `message`, `created_at`, `is_resolved` | `@ManyToOne user` | — |
| `app_settings` | `setting_key STRING` (PK) | `setting_value` | — | — |
| `notification_log` | `id BIGINT` | `subject`, `message`, `sent_at`, `is_read` | `@ManyToOne user` | — |

### Enums

- `Role` — `SUPERADMIN`, `CLUB_ADMIN`, `TRAINER`, `MEMBER`
- `MemberType` — `STUDENT`, `RECREATIONAL`, `DEFAULT`
- `BookingStatus` — `AUTO_ASSIGNED`, `MANUAL`, `CANCELLATION_REQUESTED`, `CANCELED`
- `BoatType` — `COASTAL`, `OLYMPIC`
- `SessionStatus` — `DRAFT`, `APPROVED`

### Important schema nuances

- **`Boat.version`** — JPA optimistic locking. Two concurrent `bookSeat()` calls against the same boat produce an `OptimisticLockException`, which `GlobalExceptionHandler` maps to HTTP 409 "This seat was just taken". Prevents races without pessimistic table locking.
- **`Booking (user_id, session_id)` UNIQUE** — A user can only have one non-canceled booking per session. Enforced at DB level.
- **`bookings_status_check` CHECK constraint** — PostgreSQL CHECK ensuring the `status` column only contains the 4 documented values. When the `CANCELLATION_REQUESTED` status was added mid-development, the constraint had to be dropped and recreated to accept the new value.
- **Running balance** — `FinancialLedger.running_balance` is computed at insert time (last row's balance + this row's amount). The service never updates it afterwards, so queries are O(1) per-row and auditable.
- **Settings as rows** — `app_settings` is a generic key/value table. Entries are created lazily on first PUT if they don't exist.

---

## 4. Security model

### `SecurityConfig.java` — route matrix

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/auth/**").permitAll()       // register, login, refresh
    .requestMatchers("/api/admin/**").hasAnyAuthority("CLUB_ADMIN", "SUPERADMIN", "TRAINER") // admin endpoints
    .requestMatchers("/api/superadmin/**").hasAuthority("SUPERADMIN") // superadmin-only endpoints
    .anyRequest().authenticated()                      // everything else
)
.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
```

Public endpoints:
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`

Admin-only endpoints (CLUB_ADMIN / SUPERADMIN / TRAINER):
- All of `/api/admin/**` (see §5 for the list)

Superadmin-only endpoints:
- All of `/api/superadmin/**` (club CRUD, feature toggles, impersonation)

Everything else is authenticated.

Method-level `@PreAuthorize` fine-tunes access within AdminController — TRAINER can manage sessions/bookings/cancellations but NOT user management, ledger, or settings (those require CLUB_ADMIN or SUPERADMIN).

### JWT

- **Algorithm**: `HS384` (from JJWT 0.12.6; the same secret is used for signing + verification)
- **Secret**: 32+ byte base64-encoded string from `JWT_SECRET` env var; defaults to a placeholder in `application.yml`
- **Access token**: 15-minute lifetime (`app.jwt.access-token-expiration=900000` ms)
  - Subject: user email
  - Claims: `role` (SUPERADMIN/CLUB_ADMIN/TRAINER/MEMBER)
- **Refresh token**: 7-day lifetime (`app.jwt.refresh-token-expiration=604800000` ms)
  - Stored on `users.refresh_token` column
  - Rotated on each `/auth/refresh` call — both tokens are reissued

### `JwtAuthenticationFilter` flow

1. Extract `Authorization: Bearer <token>` header. If absent, `doFilterChain.doFilter()` continues (Spring Security then applies the configured rule for the path).
2. Parse the token with `JwtService.isTokenValid()`. If malformed/expired/tampered, skip authentication — the request continues without a security context, and protected endpoints return 401/403.
3. If valid, extract `email` (subject) and `role` claim.
4. Build a `UsernamePasswordAuthenticationToken` with the bare role string as the authority (e.g., `SUPERADMIN`, `CLUB_ADMIN`) and set it on `SecurityContextHolder`.
5. Continue the filter chain.

### Password hashing

BCrypt via `PasswordEncoder` bean (default strength 10). Hashes are stored in `users.password_hash`.

### CORS

Single allowed origin: `http://localhost:3000` (configurable via `app.cors.allowed-origins`). Credentials are not included — the frontend uses the `Authorization` header, not cookies.

### Stateless session policy

`SessionCreationPolicy.STATELESS` — Spring doesn't create `HttpSession`. Every request stands alone with its JWT.

---

## 5. REST API reference

All paths prefixed `/api`. Body schemas reference DTOs in `src/main/java/com/rowingclub/backend/dto/`.

### 5.1 `AuthController` — `/api/auth` (public)

| Method | Path | Body | Returns | Description |
|---|---|---|---|---|
| `POST` | `/register` | `RegisterRequest { fullName, email, password, role? }` | `AuthResponse { accessToken, refreshToken, user }` | Creates a new user and returns a token pair. Defaults role to STUDENT if absent. Validation: `@NotBlank` on all fields, password `@Size(min=6)`, `@Email`. |
| `POST` | `/login` | `AuthRequest { email, password }` | `AuthResponse` | Validates credentials (BCrypt compare) and returns a new token pair. Rotates refresh token on `User`. |
| `POST` | `/refresh` | `{ refreshToken }` | `AuthResponse` | Validates refresh token, checks it matches `User.refreshToken`, issues new pair. |

### 5.2 `UserController` — `/api/users` (authenticated)

| Method | Path | Body | Returns | Description |
|---|---|---|---|---|
| `GET` | `/me` | — | `UserDto` | Returns current user (with `creditBalance`, `earliestCreditExpiration` populated). |
| `GET` | `/{id}` | — | `UserDto` | Returns any user by id. Backend gated to self or admin (SUPERADMIN/CLUB_ADMIN/TRAINER). |
| `POST` | `/me/password` | `ChangePasswordRequest { currentPassword, newPassword }` | `{ message }` | Changes own password after verifying current. `newPassword` `@Size(min=6)`. |

### 5.3 `BookingController` — `/api/bookings` (authenticated)

| Method | Path | Body | Returns | Description |
|---|---|---|---|---|
| `POST` | `/` | `BookingRequest { boatId, sessionId }` | `BookingDto` | Books a seat. Enforces: session approved, no duplicate booking, boat has capacity, basic-training gate, role-based time window, ≥1 credit. Deducts 1 credit. |
| `DELETE` | `/{id}` | — | `BookingDto` (with `status=CANCELLATION_REQUESTED`) | Requests cancellation (doesn't actually cancel). Blocked if `allow_cancellations=false` or already canceled/requested. Seat remains held. |
| `GET` | `/my` | — | `List<BookingDto>` | Returns caller's non-canceled bookings for today and onward, ordered by session date/time. |
| `GET` | `/boat/{boatId}` | — | `List<BookingDto>` OR `{ message }` | Returns boat's booking roster if `show_booked_members=true`; else returns a message saying member visibility is disabled. |
| `GET` | `/session/{sessionId}` | — | `List<BookingDto>` | Returns all non-canceled bookings for the session. |

### 5.4 `SessionController` — `/api/sessions` (authenticated)

| Method | Path | Body | Returns | Description |
|---|---|---|---|---|
| `GET` | `/upcoming` | — | `List<SessionDto>` | Returns all APPROVED sessions on or after today, each with boats + boat bookings populated. |
| `GET` | `/{id}` | — | `SessionDto` | Returns one session with boats + bookings. Throws 404 if not found. |
| `GET` | `/range?start=YYYY-MM-DD&end=YYYY-MM-DD` | — | `List<SessionDto>` | Filters upcoming approved sessions to the given date range. |

### 5.5 `LedgerController` — `/api/ledger` (authenticated)

| Method | Path | Body | Returns | Description |
|---|---|---|---|---|
| `GET` | `/my` | — | `List<LedgerDto>` | Returns own ledger entries, most recent first. |
| `GET` | `/balance` | — | `{ balance, expirationDate }` | Returns current balance + earliest active-credit expiration (or `"none"`). |
| `POST` | `/report` | `{ ledgerEntryId, message }` | `{ message }` | Creates an `AdminMessage` flagging a ledger entry for admin review. |

### 5.6 `AvailabilityController` — `/api/availability` (authenticated)

| Method | Path | Body | Returns | Description |
|---|---|---|---|---|
| `POST` | `/{sessionId}` | — | `{ message }` | Marks caller as available. Idempotent (no-op if already set). |
| `DELETE` | `/{sessionId}` | — | `{ message }` | Removes availability. |
| `GET` | `/my` | — | `List<Long>` (session IDs) | Returns all session IDs where caller is currently available. |
| `GET` | `/week?weekStart=YYYY-MM-DD` | — | `List<SessionDto>` | Returns all sessions in the 7-day window starting `weekStart`. |

### 5.7 `AdminController` — `/api/admin` (ADMIN only)

Grouped by functional area.

#### Session management

| Method | Path | Body | Returns |
|---|---|---|---|
| `POST` | `/sessions` | `CreateSessionRequest { date, startTime, endTime }` | `SessionDto` |
| `POST` | `/sessions/bulk` | `BulkSessionRequest { sessions: [...] }` | `List<SessionDto>` |
| `POST` | `/sessions/{id}/boats` | `AddBoatRequest { type, capacity, isBasicTrainingBoat?, name? }` | `BoatDto` |
| `PATCH` | `/sessions/{id}/approve` | — | `SessionDto` |
| `POST` | `/sessions/bulk-approve` | `List<Long>` (session IDs) | `List<SessionDto>` |
| `DELETE` | `/sessions/{id}` | — | `{ message }` |
| `POST` | `/sessions/bulk-delete` | `List<Long>` | `{ message }` |
| `DELETE` | `/boats/{id}` | — | `{ message }` |
| `POST` | `/sessions/copy-day` | `CopyDayRequest { sourceDate, targetDate }` | `List<SessionDto>` |
| `POST` | `/sessions/copy-week` | `CopyWeekRequest { sourceWeekStart, targetWeekStart }` | `List<SessionDto>` |
| `GET` | `/sessions?start=...&end=...` | — | `List<SessionDto>` (both DRAFT and APPROVED) |

#### Booking management

| Method | Path | Body | Returns |
|---|---|---|---|
| `POST` | `/bookings` | `AdminBookRequest { userId, boatId, sessionId }` | `BookingDto` (admin-created MANUAL booking; deducts 1 credit, enforces credit check) |
| `DELETE` | `/bookings/{id}` | — | `{ message }` (hard-cancel; refunds credit; decrements boat count) |
| `POST` | `/bookings/move` | `AdminMoveRequest { userId, fromBoatId, toBoatId }` | `BookingDto` |

#### Cancellation workflow

| Method | Path | Body | Returns |
|---|---|---|---|
| `GET` | `/cancellation-requests` | — | `List<BookingDto>` (status=CANCELLATION_REQUESTED) |
| `POST` | `/cancellation-requests/{id}/approve` | — | `BookingDto` (flips to CANCELED, refunds, decrements boat) |
| `POST` | `/cancellation-requests/{id}/deny` | — | `BookingDto` (flips back to MANUAL, no refund) |

#### Ledger management

| Method | Path | Body | Returns |
|---|---|---|---|
| `GET` | `/ledger/{userId}` | — | `List<LedgerDto>` |
| `POST` | `/ledger/{userId}/credit` | `AddCreditRequest { amount, reason?, expirationDate? }` | `LedgerDto` |
| `POST` | `/ledger/{userId}/deduct` | `AddCreditRequest` | `LedgerDto` |
| `PATCH` | `/ledger/entry/{entryId}` | `{ expirationDate }` | `LedgerDto` |

#### User management

| Method | Path | Body | Returns |
|---|---|---|---|
| `GET` | `/users` | — | `List<UserDto>` (all users, with credit balance + earliest expiration) |
| `GET` | `/users/search?q=...` | — | `List<UserDto>` (case-insensitive name/email contains) |
| `PATCH` | `/users/{id}/basic-training` | `{ finished: boolean }` | `UserDto` |

#### Analytics & audit

| Method | Path | Body | Returns |
|---|---|---|---|
| `GET` | `/analytics/occupancy` | — | `List<AnalyticsDto>` (last 7 days, per-session occupancy %) |
| `GET` | `/audit-logs?filter=...` | — | `List<AuditLog>` (most recent first; optional substring filter on `action`) |

#### Admin messages

| Method | Path | Body | Returns |
|---|---|---|---|
| `GET` | `/messages` | — | `List<AdminMessage>` (unresolved only) |
| `PATCH` | `/messages/{id}/resolve` | — | `{ message }` |

#### Settings

| Method | Path | Body | Returns |
|---|---|---|---|
| `GET` | `/settings` | — | `Map<String,String>` (all rows from `app_settings`) |
| `PUT` | `/settings/{key}` | `{ value: String }` | `{ message }` (upserts) |

#### Scheduler

| Method | Path | Body | Returns |
|---|---|---|---|
| `POST` | `/scheduler/run?weekStart=YYYY-MM-DD` | — | `Map<String,Object>` (`totalAssigned`, `assignments[]`) |

All admin endpoints are club-scoped: the authenticated user's club_id is extracted and used to filter queries (`findByClubIdAnd*`). SUPERADMIN users bypass club scoping (null clubId = no filter).

### 5.8 `SuperadminController` — `/api/superadmin` (SUPERADMIN only)

| Method | Path | Body | Returns | Description |
|---|---|---|---|---|
| `POST` | `/login` | `{ email, password }` | `AuthResponse` | Superadmin login (separate endpoint to apply SUPERADMIN scope) |
| `GET` | `/clubs` | — | `List<ClubDto>` | List all clubs with feature toggle states |
| `POST` | `/clubs` | `CreateClubRequest { name }` | `ClubDto` | Create a new club |
| `PUT` | `/clubs/{id}/features` | `Map<String,Boolean>` | `ClubDto` | Toggle any of the 4 feature flags |
| `POST` | `/clubs/{id}/impersonate` | — | `AuthResponse` | Returns JWT for a CLUB_ADMIN of that club (superadmin can act as club admin) |
| `POST` | `/change-password` | `{ currentPassword, newPassword }` | `{ message }` | Change superadmin's own password |

### 5.9 `SettingsController` — `/api/settings`

| Method | Path | Auth | Returns | Description |
|---|---|---|---|---|
| `GET` | `/public` | authenticated | `Map<String,String>` | Returns only the whitelisted public keys: `student_next_day_only`, `student_booking_hour`, `allow_cancellations`, `booking_hour_disabled`, `disable_availability`. Unset keys are omitted from the response. |

---

## 6. Business rules (services)

### `BookingService`

The heaviest service — encodes every booking-time rule.

- **`bookSeat(userEmail, request)`**: session must be `APPROVED`; user can't already have a non-canceled booking in the session; boat must have capacity; basic-training gate (if user hasn't finished training and boat is `isBasicTrainingBoat=false`, reject); `enforceTimeRestrictions()`; `enforceNextDayOnly()`; credit check (balance ≥ 1). On success: increment `boat.currentBookings`, create booking with status `MANUAL`, deduct 1 credit.
- **Cox seat booking**: If `request.isCoxSeat` is true and boat has `hasCoxSeat=true`, checks that caller is cox (or TRAINER) and that cox seat isn't already taken. No credit deduction for cox seat bookings. Cox bookings also don't count toward `currentBookings` (they use a separate `coxBookings` counter on Boat).
- **`cancelBooking(userEmail, bookingId)`**: checks `allow_cancellations` setting; rejects if already canceled/requested; **does not** refund or decrement — only sets status to `CANCELLATION_REQUESTED`. Seat is still held.
- **`approveCancellation(bookingId)`** (admin only): requires status `CANCELLATION_REQUESTED`. Sets to `CANCELED`, decrements boat, refunds 1 credit with reason "Refund: Cancellation approved for <date>".
- **`denyCancellation(bookingId)`**: reverts to `MANUAL` (no refund, seat keeps held).
- **`adminBookUser(userId, boatId, sessionId)`**: same checks as `bookSeat` minus time restrictions, plus credit check. Creates `MANUAL` booking.
- **`adminRemoveBooking(bookingId)`**: hard-cancel by admin. Sets `CANCELED`, decrements boat, refunds. Different from user cancel — no admin-approval loop.
- **`adminMoveUser(userId, fromBoatId, toBoatId)`**: moves between boats within same or different session. Target boat must have capacity.

#### `enforceTimeRestrictions` — the key rule

|              | Before 16:00     | After 16:00                                                           |
|--------------|------------------|-----------------------------------------------------------------------|
| **Student**  | ❌ blocked      | ✓ allowed (optionally tomorrow-only via `student_next_day_only`)       |
| **Member**   | ✓ any session   | ✓ any session **except** tomorrow's (reserved for students)            |

```
if (bookingHourDisabled()) return;   // admin global bypass

now          = LocalTime.now(Europe/Istanbul)
cutoff       = LocalTime.of(studentBookingHour(), 0)   // default 16:00
afterCutoff  = now >= cutoff
tomorrow     = LocalDate.now(Europe/Istanbul).plusDays(1)

if user is STUDENT and !afterCutoff:
    throw "Students can only book sessions after {hour}:00"

if user is CLUB_MEMBER and afterCutoff and session.date == tomorrow:
    throw "After {hour}:00, tomorrow's sessions are reserved for students"
```

Why the member-after-cutoff-blocks-tomorrow rule: after 16:00, tomorrow's slots are reserved for students so members (who have all-day access) don't grab them the moment students become eligible. ADMINs bypass all restrictions.

#### `enforceNextDayOnly` — admin toggle

Orthogonal to the above. If the admin sets `student_next_day_only=true`, **students** (not members) can only book sessions for tomorrow. Members are unaffected by this toggle.

### `LedgerService`

- **`addCredit(userId, amount, reason, expirationDate)`**: creates a positive-amount row. `runningBalance` = (previous balance) + `amount`.
- **`deductCredit(userId, amount, reason)`**: negative-amount row. Rejects if would take balance below zero.
- **`refundCredit(userId, amount, reason)`**: positive-amount row distinct from `addCredit` only by the reason text convention.
- **`getBalance(userId)`**: calls the repository's `calculateBalance` (SUM of amount column).
- **`getActiveCreditsWithExpiration(userId)`**: returns positive-amount rows with `expiration_date > now`, ordered by expiration ASC. Used for the earliest-expiration display.
- **`updateLedgerEntry(entryId, expirationDate)`**: updates only the expiration column (preserving immutability of amount/balance/reason).

Ledger rows are **immutable after creation** except for `expiration_date`. Every booking/refund appends a row; none are ever updated or deleted.

### `SessionService`

- **`createSession(request)`**: saves with status `DRAFT`.
- **`approveSession(id)`**: flips to `APPROVED`.
- **`addBoatToSession(id, request)`**: validates capacity by type — COASTAL: 1/2/4, OLYMPIC: 1/2/4/8. Defaults name to `{type}-{capacity}x` if not provided.
- **`copyDaySessions(sourceDate, targetDate, club)`**: clones every session on `sourceDate` (and their boats) to `targetDate` with `currentBookings=0`, `hasCoxSeat` preserved, and status `DRAFT`. Club-scoped — only copies sessions belonging to the given club (or all if club is null for SUPERADMIN).
- **`copyWeekSessions(sourceWeekStart, targetWeekStart, club)`**: calls `copyDaySessions` for each of the 7 offsets, club-scoped.
- **`deleteSession(id)`**: rejects if any boat has `currentBookings > 0`, else deletes boats and session.
- **`bulkDeleteSessions(ids)`**: same per-session logic in a loop.

### `AvailabilityService`

- **`setAvailability(userEmail, sessionId)`**: idempotent — checks existence before inserting. Avoids unique-constraint failures on repeated calls.
- **`removeAvailability(userEmail, sessionId)`**: `deleteByUserIdAndSessionId`.
- **`getWeekSessions(weekStart)`**: returns sessions with `date` in `[weekStart, weekStart+6]`.

### `AnalyticsService`

- **`getOccupancyLast7Days(clubId)`**: for each session in `[today-7, today]` (club-scoped if clubId not null), computes `totalCapacity` (sum of boat capacities), `totalBooked` (sum of `boat.currentBookings`), `occupancyPercentage` rounded to 2 decimals.

### `AutoSchedulerService`

- **`runScheduler(weekStart)`**: for each session in the week, considers only 4-person coastal boats (ignores 1x, 2x, and OLYMPIC entirely). Pools eligible users: those with availability AND credit ≥ 1 AND basic training complete (or boat is basic-training-only) AND not already booked in that session. Groups by role (never mixes STUDENT + CLUB_MEMBER in the same boat). Assigns up to `capacity` per boat. For each assigned user: creates `AUTO_ASSIGNED` booking + deducts 1 credit + publishes a `SessionAssignmentEvent` (consumed by notification listener). Skips sessions whose club has `featureAutoScheduler=false`.

### `UserService`

- **`changePassword(email, current, new)`**: BCrypt-verifies current, encodes new.
- **`setBasicTrainingFinished(userId, finished)`**: flips the flag; returns enriched DTO.
- **`getAllUsers(clubId)`**: returns users filtered by club (null = all).
- **`searchUsers(clubId, query)`**: club-scoped name/email search.
- **enrichment helper**: every user-returning method populates `creditBalance`, `earliestCreditExpiration`, and club feature flags from `Club` entity.

### `AuthService`

- **`register`**: rejects duplicate email; defaults role to STUDENT when omitted or unknown; BCrypt-hashes password; issues both tokens and stores the refresh token on the user row.
- **`login`**: constant-time-ish BCrypt matcher; issues new tokens and rotates the stored refresh token.
- **`refresh`**: validates token signature/expiry AND compares to stored `user.refreshToken` (preventing use of older refresh tokens after rotation).

---

## 7. Cross-cutting concerns

### `AuditAspect` — automatic audit trail

```java
@Aspect @Component
public class AuditAspect {
    @AfterReturning("within(@org.springframework.web.bind.annotation.RestController *) " +
                    "&& (@annotation(PostMapping) || @annotation(PutMapping) " +
                    " || @annotation(DeleteMapping) || @annotation(PatchMapping))")
    public void logMutation(JoinPoint jp) { ... }
}
```

Every successful mutating HTTP call writes an `AuditLog` row with the authenticated user's email, the method name, the HTTP verb + request URI, timestamp, and a short `details` summary. GET requests are not audited (they don't mutate state).

### `GlobalExceptionHandler` — HTTP status mapping

| Exception | HTTP status | Body |
|---|---|---|
| `BusinessException` | 400 | `{ message, timestamp, status }` |
| `ResourceNotFoundException` | 404 | `{ message, timestamp, status }` |
| `AccessDeniedException` | 403 | `{ message, timestamp, status }` |
| `MethodArgumentNotValidException` (@Valid failures) | 400 | `{ errors: { field: message, ... }, status }` |
| `OptimisticLockingFailureException` / `ObjectOptimisticLockingFailureException` | 409 | `{ message: "This seat was just taken, try again", status }` |
| Anything else | 500 | Generic error |

### Timezone handling

- **JVM-wide**: `JAVA_TOOL_OPTIONS=-Duser.timezone=Europe/Istanbul` set in `docker-compose.yml`, plus `TZ=Europe/Istanbul`. Every `LocalDateTime.now()` (timestamps on `Booking`, `FinancialLedger`, `AuditLog`) resolves to Istanbul local time.
- **Explicit in `BookingService`**: `ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul")`. Used for `LocalTime.now(ISTANBUL)` in `enforceTimeRestrictions` and `LocalDate.now(ISTANBUL)` in `enforceNextDayOnly` and `getUserBookings`. Defensive — even if the JVM timezone is somehow different, booking logic stays correct.

### Optimistic locking

`Boat.version` (JPA `@Version Long`). Two simultaneous bookings on the same boat lose one to the second writer; that loser gets an `OptimisticLockingFailureException` mapped to 409 with the message "This seat was just taken". The frontend re-fetches sessions and the user retries.

---

## 8. Frontend architecture

### Tech stack

- **Next.js 14.2.15** (App Router, `output: 'standalone'` for Docker)
- **React 18.3.1** + **TypeScript 5.6.3**
- **Tailwind CSS 3.4.14** with `darkMode: 'class'`
- **axios 1.7.7** for HTTP
- **lucide-react** for icons
- **recharts** for admin analytics chart
- **Jest 29 + @testing-library/react** for tests

### Route map — every page

| Route | File | Access | Purpose |
|---|---|---|---|
| `/` | `app/page.tsx` | anyone | Auth redirect: pushes `/dashboard` if authed, else `/login` |
| `/login` | `app/login/page.tsx` | anyone | Email/password login; shows demo accounts |
| `/register` | `app/register/page.tsx` | anyone | Registration with role selector (STUDENT / CLUB_MEMBER) |
| `/dashboard` | `app/dashboard/page.tsx` | authenticated | Balance card (links to `/ledger`), lessons, upcoming bookings; admin quick-action chips |
| `/booking` | `app/booking/page.tsx` | authenticated | Week/day view; amber banners for time restrictions; boat grid; book-button with training/credit/time gates |
| `/ledger` | `app/ledger/page.tsx` | authenticated | Tabular ledger view + report-issue modal |
| `/availability` | `app/availability/page.tsx` | authenticated (hidden if `disable_availability=true`) | 7-day grid; toggle sessions |
| `/settings` | `app/settings/page.tsx` | authenticated | Password change + dark-mode toggle |
| `/account/[id]` | `app/account/[id]/page.tsx` | self or admin | Profile card, training warning (only if not finished), admin "Mark complete" button |
| `/admin/planner` | `app/admin/planner/page.tsx` | ADMIN | Day/week planner with drag-drop move, add-user modal, copy-day/week |
| `/admin/cancellations` | `app/admin/cancellations/page.tsx` | ADMIN | Approve/deny pending cancellation list |
| `/admin/analytics` | `app/admin/analytics/page.tsx` | ADMIN | Recharts bar chart of 7-day occupancy |
| `/admin/logs` | `app/admin/logs/page.tsx` | ADMIN | Audit-log viewer with action filter |
| `/admin/ledger` | `app/admin/ledger/page.tsx` | ADMIN | User search → per-user ledger; add/deduct credit with expiration presets; query-param `?user=<id>` auto-selects |
| `/admin/scheduler` | `app/admin/scheduler/page.tsx` | ADMIN | Run auto-scheduler for a selected week |
| `/admin/members` | `app/admin/members/page.tsx` | ADMIN | Card grid with search, role filter, sort by name/lessons/credits/expiration/training; wallet icon → `/admin/ledger?user=<id>` |
| `/admin/messages` | `app/admin/messages/page.tsx` | ADMIN | Resolve member-reported ledger issues |
| `/admin/settings` | `app/admin/settings/page.tsx` | ADMIN | Hour select + toggle switches for every public flag |
| `/superadmin` | `app/superadmin/page.tsx` | SUPERADMIN | Club dashboard with feature toggle switches, impersonation entry point |

### Context layering

`layout.tsx` wraps children in this nesting order (outer → inner):

```tsx
<html suppressHydrationWarning>
  <head>
    <script dangerouslySetInnerHTML={themeInitScript} />   // Synchronous dark-mode class injection
  </head>
  <body>
    <ThemeProvider>         // localStorage-backed theme; must wrap everything so dark class propagates
      <AuthProvider>        // user state; must be above SettingsProvider (which depends on user)
        <SettingsProvider>  // fetches /api/settings/public on user change
          <DialogProvider>  // portal-based toast + confirm; must be inside SettingsProvider so nested pages can call toast()
            <Navbar />
            <main><PageTransition>{children}</PageTransition></main>
          </DialogProvider>
        </SettingsProvider>
      </AuthProvider>
    </ThemeProvider>
  </body>
</html>
```

### API client — `src/lib/api.ts`

Single axios instance:

- **`baseURL`** = `process.env.NEXT_PUBLIC_API_URL` (baked at build time — for the Docker image, set to `http://localhost:8081/api` during `next build`).
- **Request interceptor**: reads `accessToken` from `localStorage`, attaches `Authorization: Bearer <token>`.
- **Response interceptor** (on 401 and not already retrying):
  1. Sets `originalRequest._retry = true`
  2. Calls `POST {baseURL}/auth/refresh` with `refreshToken`
  3. On success: stores new `accessToken`, `refreshToken`, `user`; retries original request with new bearer
  4. On failure: clears all three from localStorage and hard-navigates to `/login` (via `window.location.href`)

### Type system — `src/types/index.ts`

- **`User`** — `{ id, fullName, email, role: 'SUPERADMIN'|'CLUB_ADMIN'|'TRAINER'|'MEMBER', memberType: 'STUDENT'|'RECREATIONAL'|'DEFAULT', isFinishedBasicTraining, isOnSchoolTeam, isCox, lessonsAttended, creditBalance?, clubName?, featureAvailabilityModule?, featureCancellationRequests?, featureAutoScheduler?, featureShowBookedMembers? }`
- **`AuthResponse`** — `{ accessToken, refreshToken, user }`
- **`Session`** — `{ id, date, startTime, endTime, status: 'DRAFT'|'APPROVED', boats? }`
- **`Boat`** — `{ id, sessionId, type: 'COASTAL'|'OLYMPIC', capacity, isBasicTrainingBoat, hasCoxSeat, currentBookings, version, name, bookings? }`
- **`Booking`** — `{ id, userId, userFullName, userEmail, userRole, boatId, boatName, sessionId, status: 'AUTO_ASSIGNED'|'MANUAL'|'CANCELLATION_REQUESTED'|'CANCELED', createdAt, isCoxSeat }`
- **`LedgerEntry`** — `{ id, userId, userFullName, amount, reason, runningBalance, timestamp, expirationDate }`
- **`AuditLog`** — `{ id, userEmail, action, endpoint, timestamp, details }`
- **`Analytics`** — `{ sessionId, date, sessionTime, totalCapacity, totalBooked, occupancyPercentage }`
- **`Club`** — `{ id, name, createdAt, featureAvailabilityModule, featureCancellationRequests, featureAutoScheduler, featureShowBookedMembers }`

### Utilities — `src/lib/dateUtils.ts`

| Function | Signature | Example |
|---|---|---|
| `fmt(d)` | `(d: Date) => string` | `fmt(new Date()) → "2026-04-17"` |
| `tomorrowStr()` | `() => string` | `tomorrowStr() → "2026-04-18"` |
| `getWeekDates(date)` | `(d: Date) => Date[]` | Returns Monday through Sunday of `d`'s week |
| `endOfDay(dateStr)` | `(s: string\|null\|undefined) => string\|null` | `"2026-12-31" → "2026-12-31T23:59:00"`. Passes through existing `T` strings. |
| `toDateInput(iso)` | `(s: string\|null\|undefined) => string` | `"2026-04-30T23:59" → "2026-04-30"`. Empty string for null. |
| `seatCount(session)` | `(s: Session) => { booked, capacity }` | Sums `currentBookings` and `capacity` across all boats in the session |

All utilities are pure (no DOM/BOM access except in `tomorrowStr` for `new Date()`) and unit-tested in `src/__tests__/dateUtils.test.ts`.

---

## 9. UI / UX details

### Tailwind config

- `darkMode: 'class'` — enables dark variants controlled by a class on `<html>`
- Custom `primary` palette (blue 50–900, 500 = `#3b82f6`) and `accent` palette (green 50–700, 500 = `#22c55e`)

### Animations in `globals.css`

All animations disabled under `@media (prefers-reduced-motion: reduce)`.

- **`page-enter`** — opacity 0→1 over 180ms with `cubic-bezier(0.16, 1, 0.3, 1)`. Applied by `PageTransition` component on pathname change via `key={pathname}` remount.
- **`menu-open`** — opacity + max-height 0→600px over 200ms. Applied to the mobile nav dropdown.
- **`menu-close`** — reverse of above over 180ms with ease-in. Component stays rendered during close (via `rendered` state + `onAnimationEnd`).
- **`item-stagger`** — 20ms-stepped entry for children of `.animate-menu-open` (nth-child 1 through 9+).
- **`body:has(.animate-menu-open) main`** — adds `transform: translateY(6px); opacity: 0.92` while the mobile menu is open, making the page content visibly dip. `body:has(.animate-menu-close) main` keeps `main` released in sync with the close animation.
- **Hamburger button** — crossfades `Menu` ↔ `X` icons with rotation + scale transforms over 200ms.
- **Page-transition wrapper** — uses opacity only (no `transform`) so `position: fixed` modals inside the content area are not relative to a transformed ancestor. This fixes the "modals appear above the fold on mobile" bug.

### Pre-hydration theme script

In `layout.tsx`, a synchronous `<script>` in `<head>`:

```html
<script>
  try {
    var t = localStorage.getItem('theme');
    if (t === 'dark') document.documentElement.classList.add('dark');
  } catch (e) {}
</script>
```

Runs before React hydrates, eliminating the "FOUC" where the page would briefly render in light mode before `ThemeContext`'s `useEffect` fired.

### Dialog system — `useDialog()`

Two primitives:

- **`toast(message, type = 'error')`** — queues a toast with 4-second auto-dismiss. Types: `'success'`, `'error'`, `'warning'`. Rendered at top-center in a fixed-positioned stack with `z-[200]`.
- **`confirm(options | string)`** — returns `Promise<boolean>`. Renders a centered modal with cancel + confirm buttons. Supports `{ title, message, confirmLabel, cancelLabel, danger }`. The `danger` flag styles the confirm button red.

Every browser-native `alert()` and `confirm()` in the app has been replaced with these.

### Responsive breakpoints (Tailwind)

- **Admin navbar**: inline links from `xl` (1280px); hamburger below. 9 links plus brand plus right-side cluster needs ~1280px breathing room.
- **Regular user navbar**: inline links from `lg` (1024px); hamburger below. 3–4 links fit easily.
- **Booking page week view**: `grid-cols-2 sm:grid-cols-4 md:grid-cols-7`.

### Icon-only buttons get accessibility labels

Every icon-only button has `aria-label` and `title`. Hover rotations (settings gear `hover:rotate-45`) are decorative only.

---

## 10. Deployment & operations

### `docker-compose.yml`

```yaml
services:
  postgres:
    image: postgres:16-alpine
    container_name: rowing-db
    environment:
      POSTGRES_DB: rowingclub
      POSTGRES_USER: rowing
      POSTGRES_PASSWORD: rowing_secret_2024
    ports: ["5432:5432"]
    volumes: [pgdata:/var/lib/postgresql/data]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U rowing -d rowingclub"]
      interval: 5s
      timeout: 5s
      retries: 10

  backend:
    image: rowing-backend:latest
    container_name: rowing-backend
    ports: ["8081:8080"]                    # Host 8081 → container 8080
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/rowingclub
      SPRING_DATASOURCE_USERNAME: rowing
      SPRING_DATASOURCE_PASSWORD: rowing_secret_2024
      JWT_SECRET: c2VjdXJlLXJvd2luZy1jbHViLWp3dC1zZWNyZXQta2V5LTIwMjQtcHJvZHVjdGlvbi1yZWFkeQ==
      STUDENT_BOOKING_HOUR: "16"
      JAVA_TOOL_OPTIONS: "-Duser.timezone=Europe/Istanbul"
      TZ: Europe/Istanbul
    depends_on:
      postgres:
        condition: service_healthy

  frontend:
    image: rowing-frontend:latest
    container_name: rowing-frontend
    ports: ["3000:3000"]
    environment:
      NEXT_PUBLIC_API_URL: http://localhost:8081/api    # Baked at build time; env here is informational only
    depends_on: [backend]

volumes:
  pgdata:
```

### Backend Dockerfile

Multi-stage:

1. **Build stage**: `FROM rowing-build-cache:latest` (a pre-cached Maven image with `.m2` pre-populated). Copies pom + src, runs `mvn package -Dmaven.test.skip=true -B -o` (offline).
2. **Runtime stage**: `FROM maven:3.9-eclipse-temurin-21`. Copies the jar, exposes 8080, `ENTRYPOINT ["java", "-jar", "app.jar"]`.

Note: production uses `maven:3.9-eclipse-temurin-21` as the runtime base because the same image was already pulled for the build stage — saves an image pull. A smaller JRE-only base (`eclipse-temurin:21-jre-alpine`) would work if disk space matters.

### Frontend Dockerfile

```dockerfile
FROM node:20-alpine
WORKDIR /app
ENV NODE_ENV=production
COPY .next/standalone ./
COPY .next/static ./.next/static
COPY public ./public
EXPOSE 3000
ENV PORT=3000
CMD ["node", "server.js"]
```

The `.next/standalone` directory must be produced by `npx next build` **before** running `docker build`. The frontend `Dockerfile` does **not** run the Next.js build itself — it just packages the pre-built output.

**Critical**: `NEXT_PUBLIC_API_URL` is baked into the bundle at `next build` time, not container run time. To change the API URL, rebuild the frontend image.

### Building each image

```bash
# Backend (requires rowing-build-cache:latest with M2 cache — or swap to maven:3.9-eclipse-temurin-21)
DOCKER_BUILDKIT=0 docker build -t rowing-backend -f backend/Dockerfile backend/

# Frontend (build Next.js first, then package into image)
cd frontend
rm -rf .next
NEXT_PUBLIC_API_URL=http://localhost:8081/api npx next build
cd ..
DOCKER_BUILDKIT=0 docker build -t rowing-frontend -f frontend/Dockerfile frontend/

# Start everything
docker compose up -d
```

### Port mapping

| Service | Host | Container |
|---|---|---|
| PostgreSQL | 5432 | 5432 |
| Spring Boot | **8081** | 8080 |
| Next.js | 3000 | 3000 |

The backend host port is **8081** (not 8080) to avoid conflicts with common proxy/tunnel tools (like mitmdump) that default to 8080. Both the frontend's baked `NEXT_PUBLIC_API_URL` and any direct API consumer (curl, Postman) must use 8081.

### Resetting the database

```bash
docker compose down -v   # -v removes volumes → next `up` re-runs DataSeeder
docker compose up -d
```

### Toggling runtime settings

Every public toggle can be flipped by PUT-ing to `/api/admin/settings/{key}` with `{ value: "true" | "false" | "<int>" }`. Keys:

| Key | Default | Effect |
|---|---|---|
| `student_booking_hour` | `"16"` | Cutoff hour for the per-role time restriction |
| `student_next_day_only` | `"false"` | When `true`, students can only book tomorrow |
| `allow_cancellations` | `"true"` | When `false`, `DELETE /api/bookings/{id}` rejects |
| `booking_hour_disabled` | `"false"` | When `true`, `enforceTimeRestrictions` is bypassed for everyone |
| `disable_availability` | `"false"` | When `true`, frontend hides the Availability tab and `/availability` redirects to `/dashboard` |
| `show_booked_members` | `"true"` | When `false`, `GET /api/bookings/boat/{id}` returns a "disabled" message instead of names |

The frontend's `SettingsProvider` auto-refetches these on every auth-state change, and `/admin/settings` additionally calls `refresh()` after each save so the Navbar/Availability page update instantly.

---

## 11. Testing

### Backend — 195 tests across 26 classes

Run: `mvn test` (runs JaCoCo with ≥70% line coverage threshold; build fails if below).

| Test class | Focus | Count |
|---|---|---|
| `AuthServiceTest` | register/login/refresh happy & error paths | 8 |
| `BookingServiceTest` | book, cancel (request→approve/deny), time restrictions, basic-training, admin book/move, bypass toggle | 20 |
| `LedgerServiceTest` | add/deduct/refund, balance math, expiration query | 9 |
| `SessionServiceTest` | create, approve, copy day/week, delete, boat capacity validation | 10 |
| `AvailabilityServiceTest` | set/remove idempotency, weekly window | 5 |
| `AnalyticsServiceTest` | occupancy math, zero-boat session | 3 |
| `AutoSchedulerServiceTest` | 4-person-only, no mixed roles, skips | 4 |
| `JwtServiceTest` | claims, validity, tamper detection | 6 |
| `AuthControllerTest` (MockMvc) | register/login/refresh HTTP | 4 |
| `BookingControllerTest` | 401 gating, booking lifecycle via HTTP | 5 |
| `AdminControllerTest` | Admin-only access matrix + happy paths | 12 |
| `SettingsControllerTest` | public settings auth + payload | 4 |
| `GlobalExceptionHandlerTest` | BusinessException→400, ResourceNotFoundException→404, validation→400 | 3 |
| `UserServiceTest` | changePassword, setBasicTraining, earliestExpiration | 5 |
| `UserControllerTest` | password change endpoint | 4 |
| `BookingRepositoryTest` (`@DataJpaTest`) | custom queries | 4 |
| `FinancialLedgerRepositoryTest` | calculateBalance, active credits, users-with-positive | 4 |
| `UserRepositoryTest` | case-insensitive name/email search | 5 |

### Frontend — 90 tests across 20 suites

Run: `npm test` for plain run, `npm run test:coverage` with thresholds (branches ≥25%, functions ≥30%, lines ≥40%, statements ≥40%).

| Suite | Focus |
|---|---|
| `dateUtils.test.ts` | every utility, with fake timers at `2026-04-17T10:00:00Z` |
| `AuthContext.test.tsx` | localStorage load, login/logout state |
| `SettingsContext.test.tsx` | fetch on user change, refresh cycle |
| `ThemeContext.test.tsx` | toggle, localStorage persistence |
| `ProtectedRoute.test.tsx` / `AuthRedirect.test.tsx` | access guard redirects |
| `Navbar.test.tsx` | user vs admin link sets, `lg:flex` vs `xl:flex` breakpoint, `whitespace-nowrap`, `/account/<id>` link, settings gear link |
| `PageTransition.test.tsx` | `key={pathname}` remount |
| `LoginPage.test.tsx` | form submit, success push, error display |
| `DashboardPage.test.tsx` | parallel loads, credit card link, pending-cancel badge |
| `BookingPage.test.tsx` | basic-training gating on book buttons |
| `AdminCancellationsPage.test.tsx` | approve/deny endpoints |
| `AdminSettingsPage.test.tsx` | hour select change + save, toggle PUT |
| `AdminLedgerPage.test.tsx` | user search + add-credit payload |
| `AdminMembersPage.test.tsx` | search by email, role filter, wallet link, name link, active count |
| `AccountPage.test.tsx` | owner/admin guard, training warning visibility, admin mark-complete button |
| `UserSettingsPage.test.tsx` | password submit payload, mismatch error, theme toggle |
| `AdminMessagesPage.test.tsx` | settings section is NOT rendered |
| `AdminLogsPage.test.tsx` | renders audit list |
| `AdminSchedulerPage.test.tsx` | smoke render |

### Jest configuration notes

- `next/jest` preset with jsdom
- `moduleNameMapper` maps `@/` to `src/`
- `setupFilesAfterEnv: ['jest.setup.ts']` adds `@testing-library/jest-dom` matchers
- `collectCoverageFrom` excludes `.d.ts`, `src/types/`, `src/app/layout.tsx`, `src/app/page.tsx`

---

## 12. Known limitations & future work

- **No CI/CD** — no `.github/workflows` or equivalent. Tests run locally only.
- **Hardcoded secrets in `docker-compose.yml`** — the JWT secret and DB password are in-line. For production, use Docker secrets or a secret manager.
- **No HTTPS termination** — both the backend and frontend speak HTTP. A reverse proxy (nginx, Caddy, Traefik) is required for public exposure.
- **No rate limiting** — `POST /api/auth/login` has no throttling. Production should front it with a reverse proxy rate limit or a Spring Security-based solution.
- **Multi-tenant** — refactored from single-club to multi-tenant. Each `User`, `RowingSession`, `FinancialLedger`, `AdminMessage`, and `NotificationLog` has a `club_id` FK. SUPERADMIN users (no club) can impersonate clubs and manage all tenants. Club-scoped queries are enforced in repositories (`findByClubIdAnd*`).
- **Cox seat** — `Boat.hasCoxSeat` and `User.isCox` fields added. Cox-specific booking logic: cox users can book cox seats on eligible boats; regular users cannot occupy a cox seat even if it's the last available spot. TRAINER can also book cox seats.
- **Club-level feature toggles** — Four boolean flags on the `Club` entity replace the global `app_settings` equivalents. These are served in the `UserDto` so the frontend can gate UI without extra API calls. Flags: `featureAvailabilityModule`, `featureCancellationRequests`, `featureAutoScheduler`, `featureShowBookedMembers`.
- **`DataSeeder` runs only on empty DB** — not idempotent. Restarts with data don't re-seed. Use `docker compose down -v` to reset.
- **No soft-delete anywhere** — `deleteSession` is allowed only if no bookings exist; there's no archival for historical sessions.
- **No email notifications** — `NotificationLog` entity exists and `AutoSchedulerService` publishes events, but nothing sends actual emails.
- **No i18n** — all UI copy is English.
- **No notifications in UI** — `NotificationLog` entity exists and `AutoSchedulerService` publishes events, but nothing on the frontend surfaces them.
- **Analytics is 7-day only** — no date-range picker, no export.
- **Admin audit logs** — list-only, no export, no row-level drill-down.
- **Frontend API base URL** — baked at build time. A rebuild is required to change hosts. For multi-env deployments consider runtime config via `NEXT_PUBLIC_RUNTIME_CONFIG` or equivalent.
- **Boat model** — hard-coded `COASTAL` / `OLYMPIC` types. Adding a new type requires code + migration.
- **No booking waitlist** — once a boat is full, users can't queue. `OptimisticLockException` maps to a simple retry.

---

For setup instructions, demo accounts, and quick-start commands, see [README.md](./README.md).
