ALTER TABLE identity_users ADD COLUMN display_name VARCHAR(100);
UPDATE identity_users SET display_name = SUBSTRING(email FROM 1 FOR 100) WHERE display_name IS NULL;
ALTER TABLE identity_users ADD CONSTRAINT ck_identity_users_display_name_not_blank CHECK (LENGTH(BTRIM(display_name)) >= 2);
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
