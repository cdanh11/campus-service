package com.campus.academic.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.campus.shared.application.PageResult;

public interface AcademicCourseRepository {
    AcademicCourse save(AcademicCourse value);
    AcademicCourse saveMutation(AcademicCourse value, long expectedVersion);
    List<AcademicCourse> findAllById(Collection<UUID> ids);
    Optional<AcademicCourse> findById(UUID id);
    PageResult<AcademicCourse> search(AcademicCatalogSearch search);
    boolean existsByCode(String code);
}
