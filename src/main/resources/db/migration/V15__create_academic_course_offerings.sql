CREATE TABLE academic_course_offerings (
    id UUID PRIMARY KEY,
    term_id UUID NOT NULL REFERENCES academic_terms (id),
    course_id UUID NOT NULL REFERENCES academic_courses (id),
    organization_unit_id UUID NOT NULL REFERENCES organization_units (id),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_academic_offering_term_course UNIQUE (term_id, course_id),
    CONSTRAINT ck_academic_offering_status CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED', 'CANCELLED'))
);
CREATE INDEX ix_academic_offering_term_status ON academic_course_offerings (term_id, status);
CREATE INDEX ix_academic_offering_course ON academic_course_offerings (course_id);
CREATE INDEX ix_academic_offering_organization ON academic_course_offerings (organization_unit_id);
