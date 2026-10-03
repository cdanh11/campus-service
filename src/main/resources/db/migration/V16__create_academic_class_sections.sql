CREATE TABLE academic_class_sections (
    id UUID PRIMARY KEY,
    offering_id UUID NOT NULL REFERENCES academic_course_offerings (id),
    code VARCHAR(32) NOT NULL,
    capacity INTEGER NOT NULL,
    faculty_id UUID REFERENCES faculty_staff (id),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_academic_section_code CHECK (char_length(btrim(code, chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) >= 2),
    CONSTRAINT ck_academic_section_capacity CHECK (capacity > 0),
    CONSTRAINT ck_academic_section_status CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED', 'CANCELLED')),
    CONSTRAINT ck_academic_section_open_faculty CHECK (status <> 'OPEN' OR faculty_id IS NOT NULL)
);
CREATE UNIQUE INDEX ux_academic_section_offering_code_lower ON academic_class_sections (offering_id, lower(code));
CREATE INDEX ix_academic_section_offering_status ON academic_class_sections (offering_id, status);
CREATE INDEX ix_academic_section_faculty ON academic_class_sections (faculty_id);
