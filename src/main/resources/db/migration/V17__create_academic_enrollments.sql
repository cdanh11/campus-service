CREATE TABLE academic_enrollments (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL REFERENCES students (id),
    section_id UUID NOT NULL REFERENCES academic_class_sections (id),
    status VARCHAR(20) NOT NULL DEFAULT 'ENROLLED',
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_academic_enrollment_student_section UNIQUE (student_id, section_id),
    CONSTRAINT ck_academic_enrollment_status CHECK (status IN ('ENROLLED', 'WITHDRAWN')),
    CONSTRAINT ck_academic_enrollment_version CHECK (row_version >= 0)
);
CREATE INDEX ix_academic_enrollment_section_status ON academic_enrollments (section_id, status);
CREATE INDEX ix_academic_enrollment_student_status ON academic_enrollments (student_id, status);
