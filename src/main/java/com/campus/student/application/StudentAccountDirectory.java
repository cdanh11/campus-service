package com.campus.student.application;

import java.util.Optional;
import java.util.UUID;
import com.campus.student.domain.StudentStatus;

/** Narrow ownership lookup for linked-account self-service; no contact or Identity credential data. */
public interface StudentAccountDirectory {
    Optional<LinkedStudent> findByAccount(UUID accountId);
    Optional<LinkedStudent> findByStudent(UUID studentId);
    record LinkedStudent(UUID studentId, StudentStatus status) { }
}
