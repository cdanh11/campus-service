CREATE TABLE faculty_staff (
    id UUID PRIMARY KEY,
    personnel_number VARCHAR(32) NOT NULL UNIQUE,
    full_name VARCHAR(160) NOT NULL,
    email VARCHAR(320),
    personnel_type VARCHAR(20) NOT NULL,
    organization_unit_id UUID NOT NULL REFERENCES organization_units (id),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_faculty_staff_personnel_number_not_blank CHECK (char_length(btrim(personnel_number, chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) >= 2),
    CONSTRAINT ck_faculty_staff_full_name_not_blank CHECK (char_length(btrim(full_name, chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) >= 2),
    CONSTRAINT ck_faculty_staff_type CHECK (personnel_type IN ('FACULTY', 'STAFF')),
    CONSTRAINT ck_faculty_staff_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
CREATE INDEX ix_faculty_staff_organization_unit_id ON faculty_staff (organization_unit_id);
