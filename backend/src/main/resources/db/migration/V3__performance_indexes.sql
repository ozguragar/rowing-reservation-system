-- V3: Indexes on hot foreign-key columns.
-- Postgres does not auto-create indexes for foreign keys, so the session-listing
-- queries (boats by session, bookings by boat) previously did sequential scans.
-- These back the batched lookups in SessionService#buildSessionDtos.

CREATE INDEX IF NOT EXISTS idx_boats_session_id    ON boats (session_id);
CREATE INDEX IF NOT EXISTS idx_bookings_boat_id    ON bookings (boat_id);
CREATE INDEX IF NOT EXISTS idx_bookings_session_id ON bookings (session_id);
