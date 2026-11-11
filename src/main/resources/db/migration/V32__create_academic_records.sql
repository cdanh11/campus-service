ALTER TABLE academic_courses
    ADD COLUMN course_group VARCHAR(20) NOT NULL DEFAULT 'GENERAL',
    ADD COLUMN has_final_project BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE academic_courses
    ADD CONSTRAINT ck_academic_courses_group CHECK (course_group IN ('GENERAL', 'MAJOR'));

CREATE TABLE academic_curriculum_courses (
    id UUID PRIMARY KEY,
    program_id UUID NOT NULL REFERENCES academic_programs (id),
    course_id UUID NOT NULL REFERENCES academic_courses (id),
    semester_index INTEGER NOT NULL,
    prerequisite_course_id UUID REFERENCES academic_courses (id),
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_academic_curriculum_program_course UNIQUE (program_id, course_id),
    CONSTRAINT ck_academic_curriculum_semester CHECK (semester_index BETWEEN 1 AND 12),
    CONSTRAINT ck_academic_curriculum_prerequisite CHECK (prerequisite_course_id IS NULL OR prerequisite_course_id <> course_id),
    CONSTRAINT ck_academic_curriculum_version CHECK (row_version >= 0)
);
CREATE INDEX ix_academic_curriculum_program ON academic_curriculum_courses (program_id, semester_index);
CREATE INDEX ix_academic_curriculum_prerequisite ON academic_curriculum_courses (prerequisite_course_id);

CREATE TABLE academic_grades (
    id UUID PRIMARY KEY,
    enrollment_id UUID NOT NULL REFERENCES academic_enrollments (id),
    scheme VARCHAR(40) NOT NULL,
    attendance_score NUMERIC(4, 2) NOT NULL,
    midterm_score NUMERIC(4, 2) NOT NULL,
    final_score NUMERIC(4, 2),
    project_score NUMERIC(4, 2),
    total_score NUMERIC(4, 2) NOT NULL,
    letter_grade VARCHAR(2) NOT NULL,
    grade_point NUMERIC(3, 2) NOT NULL,
    passed BOOLEAN NOT NULL,
    recorded_by UUID NOT NULL REFERENCES identity_users (id),
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_academic_grades_enrollment UNIQUE (enrollment_id),
    CONSTRAINT ck_academic_grades_scheme CHECK (scheme IN ('GENERAL', 'MAJOR_PROJECT', 'MAJOR_FINAL')),
    CONSTRAINT ck_academic_grades_attendance CHECK (attendance_score BETWEEN 0 AND 10),
    CONSTRAINT ck_academic_grades_midterm CHECK (midterm_score BETWEEN 0 AND 10),
    CONSTRAINT ck_academic_grades_final CHECK (final_score IS NULL OR final_score BETWEEN 0 AND 10),
    CONSTRAINT ck_academic_grades_project CHECK (project_score IS NULL OR project_score BETWEEN 0 AND 10),
    CONSTRAINT ck_academic_grades_components CHECK (
        (scheme = 'MAJOR_PROJECT' AND final_score IS NULL AND project_score IS NOT NULL)
        OR (scheme <> 'MAJOR_PROJECT' AND final_score IS NOT NULL AND project_score IS NULL)
    ),
    CONSTRAINT ck_academic_grades_total CHECK (total_score BETWEEN 0 AND 10),
    CONSTRAINT ck_academic_grades_letter CHECK (letter_grade IN ('A', 'B', 'C', 'D', 'F')),
    CONSTRAINT ck_academic_grades_point CHECK (grade_point BETWEEN 0 AND 4),
    CONSTRAINT ck_academic_grades_version CHECK (row_version >= 0)
);
CREATE INDEX ix_academic_grades_enrollment ON academic_grades (enrollment_id);

ALTER TABLE academic_audit_events DROP CONSTRAINT ck_academic_audit_resource;
ALTER TABLE academic_audit_events ADD CONSTRAINT ck_academic_audit_resource
    CHECK (resource_type IN ('PROGRAM', 'COURSE', 'TERM', 'COURSE_OFFERING', 'CLASS_SECTION', 'ENROLLMENT', 'GRADE', 'CURRICULUM'));
