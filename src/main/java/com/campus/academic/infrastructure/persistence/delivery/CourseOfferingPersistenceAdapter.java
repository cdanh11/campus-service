package com.campus.academic.infrastructure.persistence.delivery;

import java.util.*;
import com.campus.academic.domain.*;
import com.campus.academic.application.AcademicDeliveryService;
import com.campus.shared.application.PageResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class CourseOfferingPersistenceAdapter implements CourseOfferingRepository {
    private final CourseOfferingJpaRepository repository;
    private final EntityManager entityManager;

    CourseOfferingPersistenceAdapter(CourseOfferingJpaRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Transactional
    public CourseOffering create(CourseOffering value) {
        var entity = new CourseOfferingEntity(value.id());
        entity.update(value);
        return map(repository.saveAndFlush(entity));
    }

    @Transactional
    public CourseOffering update(CourseOffering value, long expectedVersion) {
        var entity = locked(value.id());
        if (entity.getRowVersion() != expectedVersion) throw new AcademicDeliveryService.StaleVersionException();
        entity.update(value);
        return map(repository.saveAndFlush(entity));
    }

    @Transactional(readOnly = true)
    public Optional<CourseOffering> findById(UUID id) { return repository.findById(id).map(this::map); }

    @Transactional
    public CourseOffering lock(UUID id) { return map(locked(id)); }

    private CourseOfferingEntity locked(UUID id) {
        var entity = repository.findByIdForUpdate(id).orElseThrow(AcademicDeliveryService.ResourceNotFoundException::new);
        entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
        return entity;
    }

    @Transactional(readOnly = true)
    public PageResult<CourseOffering> search(AcademicDeliverySearch search) {
        Specification<CourseOfferingEntity> specification = (root, query, b) -> {
            List<Predicate> filters = new ArrayList<>();
            if (search.status() != null) filters.add(b.equal(root.get("status"), AcademicDeliveryStatus.valueOf(search.status())));
            if (search.termId() != null) filters.add(b.equal(root.get("termId"), search.termId()));
            if (search.courseId() != null) filters.add(b.equal(root.get("courseId"), search.courseId()));
            return b.and(filters.toArray(Predicate[]::new));
        };
        var sort = Sort.by(search.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC, search.sortField()).and(Sort.by("id"));
        var page = repository.findAll(specification, PageRequest.of(search.page(), search.size(), sort));
        return new PageResult<>(page.getContent().stream().map(this::map).toList(), page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public boolean hasOpenOfferings(UUID termId) { return repository.existsByTermIdAndStatus(termId, AcademicDeliveryStatus.OPEN); }
    private CourseOffering map(CourseOfferingEntity entity) {
        return new CourseOffering(entity.getId(), entity.getTermId(), entity.getCourseId(), entity.getOrganizationUnitId(), entity.getStatus(), entity.getRowVersion(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
