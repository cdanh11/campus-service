CREATE TABLE people_registry_audit_events (
    id UUID PRIMARY KEY,
    actor_user_id UUID NOT NULL REFERENCES identity_users (id),
    resource_type VARCHAR(32) NOT NULL,
    target_id UUID NOT NULL,
    action VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    metadata VARCHAR(1000) NOT NULL DEFAULT '{}',
    CONSTRAINT ck_people_registry_audit_resource_type CHECK (resource_type IN ('ORGANIZATION_UNIT', 'STUDENT', 'FACULTY_STAFF')),
    CONSTRAINT ck_people_registry_audit_action CHECK (action IN ('CREATED', 'UPDATED'))
);
CREATE INDEX ix_people_registry_audit_target_occurred ON people_registry_audit_events (target_id, occurred_at);
