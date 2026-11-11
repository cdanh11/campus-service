package com.campus.academic.infrastructure.persistence.curriculum;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.campus.academic.application.CurriculumService;
import com.campus.academic.domain.CurriculumCourse;
import com.campus.academic.domain.CurriculumCourseRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

@Repository
class CurriculumCoursePersistenceAdapter implements CurriculumCourseRepository {
    private final CurriculumCourseJpaRepository repository;
    private final EntityManager entityManager;

    CurriculumCoursePersistenceAdapter(CurriculumCourseJpaRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    public CurriculumCourse create(CurriculumCourse value) {
        return repository.saveAndFlush(new CurriculumCourseEntity(value)).domain();
    }

    public CurriculumCourse update(CurriculumCourse value, long expectedVersion) {
        var entity = repository.findByIdForUpdate(value.id()).orElseThrow(CurriculumService.NotFoundException::new);
        entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
        if (entity.getRowVersion() != expectedVersion) throw new CurriculumService.StaleVersionException();
        entity.apply(value);
        return repository.saveAndFlush(entity).domain();
    }

    public Optional<CurriculumCourse> findById(UUID id) { return repository.findById(id).map(CurriculumCourseEntity::domain); }

    public List<CurriculumCourse> findByProgramId(UUID programId) {
        return repository.findByProgramIdOrderBySemesterIndexAscIdAsc(programId).stream()
                .map(CurriculumCourseEntity::domain).toList();
    }

    public boolean exists(UUID programId, UUID courseId) { return repository.existsByProgramIdAndCourseId(programId, courseId); }
}
