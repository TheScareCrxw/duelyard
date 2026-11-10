-- When the user last changed their username; null if never. Used to rate-limit renames.
ALTER TABLE users ADD COLUMN username_changed_at TIMESTAMPTZ;

-- Match history is listed newest-first by end time.
CREATE INDEX ix_matches_ended ON matches (ended_at DESC) WHERE ended_at IS NOT NULL;
