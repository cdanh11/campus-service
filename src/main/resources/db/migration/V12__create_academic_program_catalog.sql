CREATE TABLE academic_programs (
    id UUID PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    organization_unit_id UUID NOT NULL REFERENCES organization_units (id),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_academic_programs_code_not_blank CHECK (char_length(btrim(code, chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) >= 2),
    CONSTRAINT ck_academic_programs_name_not_blank CHECK (char_length(btrim(name, chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) >= 2),
    CONSTRAINT ck_academic_programs_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
CREATE INDEX ix_academic_programs_organization_unit_id ON academic_programs (organization_unit_id);
CREATE UNIQUE INDEX ux_academic_programs_code_lower ON academic_programs (lower(code));
