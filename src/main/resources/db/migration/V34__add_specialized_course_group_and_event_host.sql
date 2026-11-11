ALTER TABLE academic_courses DROP CONSTRAINT ck_academic_courses_group;
ALTER TABLE academic_courses
    ADD CONSTRAINT ck_academic_courses_group CHECK (course_group IN ('GENERAL', 'MAJOR', 'SPECIALIZED'));

ALTER TABLE campus_events ADD COLUMN host_unit_id UUID REFERENCES organization_units(id);
CREATE INDEX ix_event_host_status_start ON campus_events(host_unit_id, status, starts_at);
