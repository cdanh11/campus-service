CREATE TABLE academic_audit_events (
    id UUID PRIMARY KEY,
    actor_user_id UUID NOT NULL REFERENCES identity_users (id),
    resource_type VARCHAR(32) NOT NULL,
    target_id UUID NOT NULL,
    action VARCHAR(32) NOT NULL,
    resource_version BIGINT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT ck_academic_audit_resource CHECK (resource_type IN ('PROGRAM', 'COURSE', 'TERM', 'COURSE_OFFERING', 'CLASS_SECTION', 'ENROLLMENT')),
    CONSTRAINT ck_academic_audit_action CHECK (action IN ('CREATED', 'UPDATED', 'WITHDRAWN', 'REENROLLED')),
    CONSTRAINT ck_academic_audit_enrollment_action CHECK (action NOT IN ('WITHDRAWN', 'REENROLLED') OR resource_type = 'ENROLLMENT'),
    CONSTRAINT ck_academic_audit_version CHECK (resource_version >= 0),
    CONSTRAINT ck_academic_audit_metadata CHECK (jsonb_typeof(metadata) = 'object')
);
CREATE INDEX ix_academic_audit_resource_time ON academic_audit_events (resource_type, target_id, occurred_at);
CREATE INDEX ix_academic_audit_actor_time ON academic_audit_events (actor_user_id, occurred_at);
