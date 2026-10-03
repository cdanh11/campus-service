package com.campus.academic.domain;

import java.util.Optional;
import java.util.UUID;
import com.campus.shared.application.PageResult;

public interface AcademicCourseRepository {
    AcademicCourse save(AcademicCourse value);
    AcademicCourse saveMutation(AcademicCourse value, long expectedVersion);
    Optional<AcademicCourse> findById(UUID id);
    PageResult<AcademicCourse> search(AcademicCatalogSearch search);
    boolean existsByCode(String code);
}
