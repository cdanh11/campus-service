package com.campus.academic.infrastructure.persistence.curriculum;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface CurriculumCourseJpaRepository extends JpaRepository<CurriculumCourseEntity, UUID> {
    List<CurriculumCourseEntity> findByProgramIdOrderBySemesterIndexAscIdAsc(UUID programId);

    boolean existsByProgramIdAndCourseId(UUID programId, UUID courseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select entry from CurriculumCourseEntity entry where entry.id = :id")
    Optional<CurriculumCourseEntity> findByIdForUpdate(UUID id);
}
