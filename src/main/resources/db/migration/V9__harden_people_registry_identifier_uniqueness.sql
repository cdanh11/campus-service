CREATE UNIQUE INDEX ux_organization_units_code_lower ON organization_units (lower(code));
CREATE UNIQUE INDEX ux_students_student_number_lower ON students (lower(student_number));
CREATE UNIQUE INDEX ux_faculty_staff_personnel_number_lower ON faculty_staff (lower(personnel_number));
