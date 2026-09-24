CREATE TABLE organization_units (
    id UUID PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    unit_type VARCHAR(32) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_organization_units_code_not_blank CHECK (char_length(btrim(code, chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) >= 2),
    CONSTRAINT ck_organization_units_name_not_blank CHECK (char_length(btrim(name, chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) >= 2),
    CONSTRAINT ck_organization_units_unit_type CHECK (unit_type IN ('FACULTY', 'DEPARTMENT', 'ADMINISTRATIVE')),
    CONSTRAINT ck_organization_units_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
