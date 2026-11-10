-- Substring search on usernames (leaderboards, player search). pg_trgm is a trusted extension:
-- the database owner can create it (PostgreSQL 13+; available on Amazon RDS).
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX ix_users_username_trgm ON users USING gin (lower(username) gin_trgm_ops);
