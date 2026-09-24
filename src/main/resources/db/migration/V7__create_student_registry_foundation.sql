CREATE TABLE students (
    id UUID PRIMARY KEY,
    student_number VARCHAR(32) NOT NULL UNIQUE,
    full_name VARCHAR(160) NOT NULL,
    email VARCHAR(320),
    organization_unit_id UUID NOT NULL REFERENCES organization_units (id),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_students_student_number_not_blank CHECK (char_length(btrim(student_number, chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) >= 2),
    CONSTRAINT ck_students_full_name_not_blank CHECK (char_length(btrim(full_name, chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) >= 2),
    CONSTRAINT ck_students_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
CREATE INDEX ix_students_organization_unit_id ON students (organization_unit_id);
