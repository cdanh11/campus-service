CREATE TABLE academic_courses (
    id UUID PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    title VARCHAR(160) NOT NULL,
    credits INTEGER NOT NULL,
    organization_unit_id UUID NOT NULL REFERENCES organization_units (id),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_academic_courses_code_not_blank CHECK (char_length(btrim(code, chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) >= 2),
    CONSTRAINT ck_academic_courses_title_not_blank CHECK (char_length(btrim(title, chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) >= 2),
    CONSTRAINT ck_academic_courses_credits CHECK (credits BETWEEN 1 AND 30),
    CONSTRAINT ck_academic_courses_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
CREATE INDEX ix_academic_courses_organization_unit_id ON academic_courses (organization_unit_id);
CREATE UNIQUE INDEX ux_academic_courses_code_lower ON academic_courses (lower(code));
