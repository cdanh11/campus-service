CREATE TABLE dormitory_assignments (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL REFERENCES students(id),
    bed_id UUID NOT NULL REFERENCES dormitory_beds(id),
    status VARCHAR(16) NOT NULL DEFAULT 'ASSIGNED',
    row_version BIGINT NOT NULL DEFAULT 0,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_dormitory_assignment_status CHECK (status IN ('ASSIGNED', 'RELEASED')),
    CONSTRAINT ck_dormitory_assignment_version CHECK (row_version >= 0),
    CONSTRAINT ck_dormitory_assignment_release CHECK (
        (status = 'ASSIGNED' AND released_at IS NULL) OR
        (status = 'RELEASED' AND released_at IS NOT NULL AND released_at >= assigned_at))
);
CREATE UNIQUE INDEX ux_dormitory_assignment_current_student ON dormitory_assignments(student_id) WHERE status = 'ASSIGNED';
CREATE UNIQUE INDEX ux_dormitory_assignment_current_bed ON dormitory_assignments(bed_id) WHERE status = 'ASSIGNED';
CREATE INDEX ix_dormitory_assignment_student_status ON dormitory_assignments(student_id, status);
CREATE INDEX ix_dormitory_assignment_bed_status ON dormitory_assignments(bed_id, status);

ALTER TABLE dormitory_audit_events DROP CONSTRAINT ck_dormitory_audit_resource;
ALTER TABLE dormitory_audit_events ADD CONSTRAINT ck_dormitory_audit_resource CHECK (resource_type IN ('BUILDING', 'ROOM', 'BED', 'ASSIGNMENT'));
ALTER TABLE dormitory_audit_events DROP CONSTRAINT ck_dormitory_audit_action;
ALTER TABLE dormitory_audit_events ADD CONSTRAINT ck_dormitory_audit_action CHECK (action IN ('CREATED', 'UPDATED', 'ASSIGNED', 'RELEASED'));
ALTER TABLE dormitory_audit_events ADD CONSTRAINT ck_dormitory_audit_resource_action CHECK (
    (resource_type = 'ASSIGNMENT' AND action IN ('ASSIGNED', 'RELEASED')) OR
    (resource_type IN ('BUILDING', 'ROOM', 'BED') AND action IN ('CREATED', 'UPDATED'))
);
