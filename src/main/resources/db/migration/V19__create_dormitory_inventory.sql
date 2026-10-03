CREATE TABLE dormitory_buildings (
    id UUID PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(160) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_dormitory_buildings_code CHECK (char_length(btrim(code, chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) BETWEEN 2 AND 32),
    CONSTRAINT ck_dormitory_buildings_name CHECK (char_length(btrim(name, chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) BETWEEN 2 AND 160),
    CONSTRAINT ck_dormitory_buildings_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_dormitory_buildings_version CHECK (row_version >= 0)
);
CREATE UNIQUE INDEX ux_dormitory_buildings_code ON dormitory_buildings (lower(code));
CREATE INDEX ix_dormitory_buildings_status ON dormitory_buildings (status);

CREATE TABLE dormitory_rooms (
    id UUID PRIMARY KEY,
    building_id UUID NOT NULL REFERENCES dormitory_buildings(id),
    code VARCHAR(32) NOT NULL,
    name VARCHAR(160) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_dormitory_rooms_code CHECK (char_length(btrim(code, chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) BETWEEN 2 AND 32),
    CONSTRAINT ck_dormitory_rooms_name CHECK (char_length(btrim(name, chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) BETWEEN 2 AND 160),
    CONSTRAINT ck_dormitory_rooms_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_dormitory_rooms_version CHECK (row_version >= 0)
);
CREATE UNIQUE INDEX ux_dormitory_rooms_code ON dormitory_rooms (building_id, lower(code));
CREATE INDEX ix_dormitory_rooms_status ON dormitory_rooms (building_id, status);

CREATE TABLE dormitory_beds (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES dormitory_rooms(id),
    code VARCHAR(32) NOT NULL,
    name VARCHAR(160) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_dormitory_beds_code CHECK (char_length(btrim(code, chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) BETWEEN 2 AND 32),
    CONSTRAINT ck_dormitory_beds_name CHECK (char_length(btrim(name, chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) BETWEEN 2 AND 160),
    CONSTRAINT ck_dormitory_beds_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_dormitory_beds_version CHECK (row_version >= 0)
);
CREATE UNIQUE INDEX ux_dormitory_beds_code ON dormitory_beds (room_id, lower(code));
CREATE INDEX ix_dormitory_beds_status ON dormitory_beds (room_id, status);

CREATE TABLE dormitory_audit_events (
    id UUID PRIMARY KEY,
    actor_user_id UUID NOT NULL REFERENCES identity_users(id),
    resource_type VARCHAR(16) NOT NULL,
    target_id UUID NOT NULL,
    action VARCHAR(16) NOT NULL,
    resource_version BIGINT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT ck_dormitory_audit_resource CHECK (resource_type IN ('BUILDING', 'ROOM', 'BED')),
    CONSTRAINT ck_dormitory_audit_action CHECK (action IN ('CREATED', 'UPDATED')),
    CONSTRAINT ck_dormitory_audit_version CHECK (resource_version >= 0),
    CONSTRAINT ck_dormitory_audit_metadata CHECK (jsonb_typeof(metadata) = 'object')
);
CREATE INDEX ix_dormitory_audit_resource_time ON dormitory_audit_events(resource_type, target_id, occurred_at);
CREATE INDEX ix_dormitory_audit_actor_time ON dormitory_audit_events(actor_user_id, occurred_at);
