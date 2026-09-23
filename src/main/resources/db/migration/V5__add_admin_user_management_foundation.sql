ALTER TABLE identity_users ADD COLUMN display_name VARCHAR(100);
-- Normalize with exactly space, tab, newline, carriage return, vertical tab, and form feed.
WITH legacy_names AS (
    SELECT id, btrim(left(btrim(email, chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12)), 100),
                     chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12)) AS candidate
    FROM identity_users
)
UPDATE identity_users AS users
SET display_name = CASE
    WHEN char_length(legacy_names.candidate) >= 2 THEN legacy_names.candidate
    ELSE 'User ' || users.id::text
END
FROM legacy_names
WHERE users.id = legacy_names.id AND users.display_name IS NULL;
ALTER TABLE identity_users ADD CONSTRAINT ck_identity_users_display_name_not_blank
    CHECK (char_length(btrim(display_name, chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) >= 2);
ALTER TABLE identity_users ALTER COLUMN display_name SET NOT NULL;
ALTER TABLE identity_users ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE identity_admin_guard (guard_id SMALLINT PRIMARY KEY, CONSTRAINT ck_identity_admin_guard_id CHECK (guard_id = 1));
INSERT INTO identity_admin_guard (guard_id) VALUES (1);

CREATE TABLE identity_admin_audit_events (
    id UUID PRIMARY KEY,
    actor_user_id UUID NOT NULL REFERENCES identity_users (id),
    target_user_id UUID NOT NULL REFERENCES identity_users (id),
    action VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    metadata VARCHAR(1000) NOT NULL DEFAULT '{}'
);
CREATE INDEX ix_identity_admin_audit_target_occurred ON identity_admin_audit_events (target_user_id, occurred_at);
