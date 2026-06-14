-- V2: Multi-tenant support with clubs, updated roles, and cox seat features

-- Create clubs table
CREATE TABLE IF NOT EXISTS clubs (
    id                            BIGSERIAL PRIMARY KEY,
    name                          VARCHAR(255) NOT NULL UNIQUE,
    created_at                    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    feature_availability_module   BOOLEAN NOT NULL DEFAULT true,
    feature_cancellation_requests BOOLEAN NOT NULL DEFAULT true,
    feature_auto_scheduler        BOOLEAN NOT NULL DEFAULT true,
    feature_show_booked_members   BOOLEAN NOT NULL DEFAULT true
);

-- Insert default club for existing data
INSERT INTO clubs (name, feature_availability_module, feature_cancellation_requests, feature_auto_scheduler, feature_show_booked_members)
VALUES ('Default Rowing Club', true, true, true, true);

-- Add club_id to users table
ALTER TABLE users ADD COLUMN club_id BIGINT;
ALTER TABLE users ADD CONSTRAINT fk_users_club FOREIGN KEY (club_id) REFERENCES clubs(id);
UPDATE users SET club_id = (SELECT id FROM clubs WHERE name = 'Default Rowing Club' LIMIT 1);
ALTER TABLE users ALTER COLUMN club_id SET NOT NULL;
CREATE INDEX idx_users_club_id ON users(club_id);

-- Add member_type and cox to users table
ALTER TABLE users ADD COLUMN member_type VARCHAR(255);
ALTER TABLE users ADD COLUMN is_cox BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE users ADD CONSTRAINT users_member_type_check CHECK (member_type IN ('STUDENT', 'RECREATIONAL', 'DEFAULT'));

-- Update existing users: STUDENT -> MEMBER with member_type STUDENT
UPDATE users SET member_type = 'STUDENT' WHERE role = 'STUDENT';

-- Update existing users: CLUB_MEMBER -> MEMBER with member_type DEFAULT
UPDATE users SET member_type = 'DEFAULT' WHERE role = 'CLUB_MEMBER';

-- Drop old role constraint and add new one
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('SUPERADMIN', 'CLUB_ADMIN', 'TRAINER', 'MEMBER'));

-- Update existing admins to CLUB_ADMIN
UPDATE users SET role = 'CLUB_ADMIN' WHERE role = 'ADMIN';

-- Update existing STUDENT and CLUB_MEMBER to MEMBER
UPDATE users SET role = 'MEMBER' WHERE role IN ('STUDENT', 'CLUB_MEMBER');

-- Add club_id to rowing_sessions table
ALTER TABLE rowing_sessions ADD COLUMN club_id BIGINT;
ALTER TABLE rowing_sessions ADD CONSTRAINT fk_sessions_club FOREIGN KEY (club_id) REFERENCES clubs(id);
UPDATE rowing_sessions SET club_id = (SELECT id FROM clubs WHERE name = 'Default Rowing Club' LIMIT 1);
ALTER TABLE rowing_sessions ALTER COLUMN club_id SET NOT NULL;
CREATE INDEX idx_sessions_club_id ON rowing_sessions(club_id);

-- Add has_cox_seat to boats table
ALTER TABLE boats ADD COLUMN has_cox_seat BOOLEAN NOT NULL DEFAULT false;

-- Add cox_user_id to bookings table for cox seat assignments
ALTER TABLE bookings ADD COLUMN is_cox_seat BOOLEAN NOT NULL DEFAULT false;

-- Add club_id to financial_ledger table
ALTER TABLE financial_ledger ADD COLUMN club_id BIGINT;
ALTER TABLE financial_ledger ADD CONSTRAINT fk_ledger_club FOREIGN KEY (club_id) REFERENCES clubs(id);
UPDATE financial_ledger SET club_id = (SELECT id FROM clubs WHERE name = 'Default Rowing Club' LIMIT 1);
ALTER TABLE financial_ledger ALTER COLUMN club_id SET NOT NULL;
CREATE INDEX idx_ledger_club_id ON financial_ledger(club_id);

-- Add club_id to admin_messages table
ALTER TABLE admin_messages ADD COLUMN club_id BIGINT;
ALTER TABLE admin_messages ADD CONSTRAINT fk_messages_club FOREIGN KEY (club_id) REFERENCES clubs(id);
UPDATE admin_messages SET club_id = (SELECT id FROM clubs WHERE name = 'Default Rowing Club' LIMIT 1);
ALTER TABLE admin_messages ALTER COLUMN club_id SET NOT NULL;
CREATE INDEX idx_messages_club_id ON admin_messages(club_id);

-- Add club_id to notification_log table
ALTER TABLE notification_log ADD COLUMN club_id BIGINT;
ALTER TABLE notification_log ADD CONSTRAINT fk_notifications_club FOREIGN KEY (club_id) REFERENCES clubs(id);
UPDATE notification_log SET club_id = (SELECT id FROM clubs WHERE name = 'Default Rowing Club' LIMIT 1);
ALTER TABLE notification_log ALTER COLUMN club_id SET NOT NULL;
CREATE INDEX idx_notifications_club_id ON notification_log(club_id);
