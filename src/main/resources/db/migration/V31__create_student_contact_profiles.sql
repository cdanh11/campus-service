CREATE TABLE student_contact_profiles (
    student_id UUID PRIMARY KEY REFERENCES students (id) ON DELETE CASCADE,
    phone VARCHAR(32),
    address VARCHAR(500),
    date_of_birth DATE,
    cohort VARCHAR(8),
    program_id UUID REFERENCES academic_programs (id),
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_student_contact_phone CHECK (phone IS NULL OR char_length(btrim(phone)) BETWEEN 8 AND 32),
    CONSTRAINT ck_student_contact_address CHECK (address IS NULL OR char_length(btrim(address)) BETWEEN 2 AND 500),
    CONSTRAINT ck_student_contact_birth CHECK (date_of_birth IS NULL OR (date_of_birth >= DATE '1900-01-01' AND date_of_birth <= CURRENT_DATE)),
    CONSTRAINT ck_student_contact_cohort CHECK (cohort IS NULL OR cohort ~ '^K[0-9]{2}$'),
    CONSTRAINT ck_student_contact_version CHECK (row_version >= 0)
);
CREATE INDEX ix_student_contact_profiles_program ON student_contact_profiles (program_id);
