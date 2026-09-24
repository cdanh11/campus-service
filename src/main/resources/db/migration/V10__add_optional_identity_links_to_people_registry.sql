ALTER TABLE students ADD COLUMN identity_user_id UUID REFERENCES identity_users (id);
ALTER TABLE faculty_staff ADD COLUMN identity_user_id UUID REFERENCES identity_users (id);

CREATE UNIQUE INDEX ux_students_identity_user_id ON students (identity_user_id) WHERE identity_user_id IS NOT NULL;
CREATE UNIQUE INDEX ux_faculty_staff_identity_user_id ON faculty_staff (identity_user_id) WHERE identity_user_id IS NOT NULL;
