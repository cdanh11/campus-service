package com.campus.student.domain;

import java.util.Optional;
import java.util.UUID;

public interface StudentContactProfileRepository {
    Optional<StudentContactProfile> findByStudentId(UUID studentId);
    StudentContactProfile save(StudentContactProfile profile, long expectedVersion);
}
