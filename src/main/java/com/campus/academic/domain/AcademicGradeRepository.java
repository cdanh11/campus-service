package com.campus.academic.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.campus.shared.application.PageResult;

public interface AcademicGradeRepository {
    AcademicGrade create(AcademicGrade value);
    AcademicGrade update(AcademicGrade value, long expectedVersion);
    Optional<AcademicGrade> findById(UUID id);
    Optional<AcademicGrade> findByEnrollmentId(UUID enrollmentId);
    List<AcademicGrade> findAllByEnrollmentIds(Collection<UUID> enrollmentIds);
    PageResult<AcademicGrade> search(GradeSearch query);
}
