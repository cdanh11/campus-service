-- Unreleased Phase 5B. Retained Student/event membership approved; admission time gate remains application-owned.
CREATE TABLE campus_events (
    id UUID PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(4000) NOT NULL,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    capacity INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_event_code CHECK (char_length(btrim(code,chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) BETWEEN 2 AND 32),
    CONSTRAINT ck_event_title CHECK (char_length(btrim(title,chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) BETWEEN 2 AND 160),
    CONSTRAINT ck_event_description CHECK (char_length(btrim(description,chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) BETWEEN 2 AND 4000),
    CONSTRAINT ck_event_schedule CHECK (starts_at < ends_at),
    CONSTRAINT ck_event_capacity CHECK (capacity > 0),
    CONSTRAINT ck_event_status CHECK (status IN ('DRAFT','OPEN','CLOSED','CANCELLED')),
    CONSTRAINT ck_event_version CHECK (row_version >= 0)
);
CREATE UNIQUE INDEX ux_event_code ON campus_events(lower(code));
CREATE INDEX ix_event_status_start ON campus_events(status,starts_at);

CREATE TABLE event_registrations (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES campus_events(id),
    student_id UUID NOT NULL REFERENCES students(id),
    status VARCHAR(16) NOT NULL DEFAULT 'REGISTERED',
    row_version BIGINT NOT NULL DEFAULT 0,
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    attended_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_event_registration_membership UNIQUE (event_id,student_id),
    CONSTRAINT ck_event_registration_status CHECK (status IN ('REGISTERED','CANCELLED','ATTENDED')),
    CONSTRAINT ck_event_registration_version CHECK (row_version >= 0),
    CONSTRAINT ck_event_registration_history CHECK (
        (status='REGISTERED' AND cancelled_at IS NULL AND attended_at IS NULL) OR
        (status='CANCELLED' AND cancelled_at IS NOT NULL AND cancelled_at>=registered_at AND attended_at IS NULL) OR
        (status='ATTENDED' AND attended_at IS NOT NULL AND attended_at>=registered_at AND cancelled_at IS NULL))
);
CREATE INDEX ix_event_registration_event_status ON event_registrations(event_id,status);
CREATE INDEX ix_event_registration_student_status ON event_registrations(student_id,status);

CREATE TABLE event_audit_events (
    id UUID PRIMARY KEY,
    actor_user_id UUID NOT NULL REFERENCES identity_users(id),
    resource_type VARCHAR(16) NOT NULL,
    target_id UUID NOT NULL,
    action VARCHAR(16) NOT NULL,
    resource_version BIGINT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT ck_event_audit_version CHECK (resource_version >= 0),
    CONSTRAINT ck_event_audit_metadata CHECK (jsonb_typeof(metadata)='object'),
    CONSTRAINT ck_event_audit_policy CHECK (
        (resource_type='EVENT' AND action IN ('CREATED','UPDATED')) OR
        (resource_type='REGISTRATION' AND action IN ('REGISTERED','CANCELLED','ATTENDED','RESTORED')))
);
CREATE INDEX ix_event_audit_target_time ON event_audit_events(resource_type,target_id,occurred_at);
CREATE INDEX ix_event_audit_actor_time ON event_audit_events(actor_user_id,occurred_at);
