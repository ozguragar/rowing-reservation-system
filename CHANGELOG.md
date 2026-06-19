# Changelog

All notable changes to this project are documented here.

## [Unreleased]

### Tests — business-logic coverage pass

- Added ~50 service-layer tests covering previously-untested business branches:
  - **BookingService** (69%→89%): admin book/move/remove, cancellation status
    transitions, cox-seat validation on every admin path, cox↔rowing-seat moves,
    member-visibility toggles, and `bookSeat` edge branches (unapproved session,
    boat-from-another-session).
  - **LedgerService** (78%→92%), **UserService** (72%→84%), **SessionService**
    (81%→86%), **AvailabilityService** (75%→91%): refunds/ledger history, club-scoped
    search, role/type guardrail permutations, delete-with-active-bookings, capacity
    validation, feature-flag gating, not-found paths.
  - **AutoSchedulerService**: skips users without credit / already booked, skips clubs
    with the scheduler disabled, and verifies credit deduction + lesson increment.
  - **PasswordResetService**: expired-token and used-token rejection.
- Backend suite: 217 → **268 tests**, all green, JaCoCo line gate (≥70%) passing.

### Added — Admin member management on the profile page

- The profile page (`/account/[id]`) now lets a **CLUB_ADMIN / SUPERADMIN** change a
  member's **role** and **member type** inline, and shows every member's **past and
  upcoming reservations** (date, time, boat, status). Trainers can view but not edit.
- New endpoints:
  - `PATCH /api/admin/users/{id}` — update `{ role?, memberType? }`. Guardrails: you
    cannot edit your own role/type; non-superadmins may only edit users in their own club;
    only a SUPERADMIN may grant SUPERADMIN. Audited automatically.
  - `GET /api/users/{id}/bookings` — a user's full reservation history (self-or-admin),
    newest first, with session date/time eagerly fetched (`JOIN FETCH`, no N+1).
- `BookingDto` now carries `sessionDate` / `sessionStartTime` / `sessionEndTime`.

## [Unreleased] — Maintenance pass (2026-06)

Focus: fix the broken registration page, resolve production slowness, and restore
a green, higher-coverage test suite.

### Fixed

- **Registration was completely broken in production.** New users are created in a
  multi-tenant world where `users.club_id` is `NOT NULL`, but `AuthService.register`
  never assigned a club, so every sign-up failed with a database constraint violation.
  Registration now assigns the new member to a club (the one chosen on the form, or the
  primary club by default). Tests passed despite this because the H2 test schema left
  `club_id` nullable while the production Postgres schema (Flyway V2) enforced `NOT NULL`.
- **Severe slowness on the dashboard / booking views (N+1 queries).**
  `SessionService` loaded the upcoming-sessions list by re-fetching each session by id,
  then querying boats per session, then querying bookings **per boat** — on the order of
  1,000+ SQL statements per page load with the seeded data. Replaced with a batched
  loader that fetches all boats for the listed sessions in one query and all their
  bookings (with `JOIN FETCH` on user/boat) in another — a fixed 2 queries regardless
  of session count. Added indexes on the hot FK columns (`boats.session_id`,
  `bookings.boat_id`, `bookings.session_id`) via entity annotations and a Flyway `V3`
  migration.
- **Test suite was effectively red and masking real failures.** The shared in-memory H2
  database (`jdbc:h2:mem:testdb`, `create-drop`) was dropped whenever Spring evicted a
  cached test context, so later test classes saw an empty schema
  (`Table ... not found`). Each Spring context now gets its own isolated in-memory DB
  (`...mem:rowingtest-${random.uuid}`). With the cascade gone, several pre-existing
  failures from the V2 multi-tenant migration surfaced and were fixed (ledger/booking
  test fixtures missing a club, superadmin-has-no-club assumptions, a feature-toggle
  suite that only no-op'd, etc.).
- **Auth rate limiter polluted the test suite.** The JVM-wide limiter (10 req/min/IP)
  tripped spurious `429`s across MockMvc tests that all originate from `127.0.0.1`. The
  limit is now configurable (`app.security.auth-rate-limit-per-minute`) and effectively
  disabled in the test profile; production behaviour is unchanged.

### Changed / Security

- **Public registration can no longer self-assign an elevated role.** The role field
  was removed from the sign-up form, and the backend forces every self-registration to
  `MEMBER`. Trainers/admins are provisioned by an admin. Previously a user could register
  directly as `TRAINER` (and the code would have accepted `SUPERADMIN`).
- **Registration password rule aligned.** The form advertised "min 6 characters" while
  the backend required 8, so short passwords failed server-side validation. The form now
  enforces 8 to match.
- New public endpoint `GET /api/auth/clubs` (id + name only) powers the club picker on
  the registration page; it is shown only when more than one club exists.
- `maven-surefire-plugin` now sets `-Dnet.bytebuddy.experimental=true` so Mockito can run
  on JDKs newer than it officially supports (e.g. JDK 26 locally); no-op on the JDK 21
  used by CI and Docker.

### Tests

- Added: registration club-assignment and role-forcing (backend), the public clubs
  endpoint, batched session assembly + club scoping (backend), and a new
  `RegisterPage` suite (frontend) covering the club picker, the 8-char rule, the absence
  of a role selector, and error handling.
