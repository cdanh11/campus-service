package com.campus.academic.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CurriculumCourseRepository {
    CurriculumCourse create(CurriculumCourse value);
    CurriculumCourse update(CurriculumCourse value, long expectedVersion);
    Optional<CurriculumCourse> findById(UUID id);
    List<CurriculumCourse> findByProgramId(UUID programId);
    boolean exists(UUID programId, UUID courseId);
}
