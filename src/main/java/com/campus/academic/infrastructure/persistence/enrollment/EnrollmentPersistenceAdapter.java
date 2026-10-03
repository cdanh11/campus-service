package com.campus.academic.infrastructure.persistence.enrollment;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import com.campus.academic.application.EnrollmentService;
import com.campus.academic.domain.*;
import com.campus.shared.application.PageResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

/** Writes are invoked inside the service's parent-lock transaction. */
@Repository
class EnrollmentPersistenceAdapter implements EnrollmentRepository {
    private final EnrollmentJpaRepository repository;
    private final EntityManager entityManager;

    EnrollmentPersistenceAdapter(EnrollmentJpaRepository repository, EntityManager entityManager) {
        this.repository = repository; this.entityManager = entityManager;
    }

    public Enrollment create(Enrollment value) { return repository.saveAndFlush(new EnrollmentEntity(value)).domain(); }

    public Enrollment update(Enrollment value, long expectedVersion) {
        var entity = repository.findById(value.id()).orElseThrow(EnrollmentService.NotFoundException::new);
        entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
        if (entity.domain().rowVersion() != expectedVersion) throw new EnrollmentService.StaleVersionException();
        entity.update(value);
        return repository.saveAndFlush(entity).domain();
    }

    public Optional<Enrollment> findById(UUID id) { return repository.findById(id).map(EnrollmentEntity::domain); }
    public Enrollment lock(UUID id) {
        var entity = repository.findById(id).orElseThrow(EnrollmentService.NotFoundException::new);
        entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
        return entity.domain();
    }
    public boolean exists(UUID studentId, UUID sectionId) { return repository.existsByStudentIdAndSectionId(studentId, sectionId); }
    public long occupied(UUID sectionId) { return repository.countBySectionIdAndStatus(sectionId, EnrollmentStatus.ENROLLED); }

    public PageResult<Enrollment> search(EnrollmentSearch search) {
        Specification<EnrollmentEntity> specification = (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            if (search.studentId() != null) predicates.add(builder.equal(root.get("studentId"), search.studentId()));
            if (search.sectionId() != null) predicates.add(builder.equal(root.get("sectionId"), search.sectionId()));
            if (search.status() != null) predicates.add(builder.equal(root.get("status"), search.status()));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        var sort = Sort.by(search.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC, search.sortField()).and(Sort.by("id"));
        var result = repository.findAll(specification, PageRequest.of(search.page(), search.size(), sort));
        return new PageResult<>(result.getContent().stream().map(EnrollmentEntity::domain).toList(), result.getTotalElements());
    }
}
