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
class ClassSectionPersistenceAdapter implements ClassSectionRepository {
    private final ClassSectionJpaRepository repository;
    private final EntityManager entityManager;

    ClassSectionPersistenceAdapter(ClassSectionJpaRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Transactional
    public ClassSection create(ClassSection value) {
        var entity = new ClassSectionEntity(value.id());
        entity.update(value);
        return map(repository.saveAndFlush(entity));
    }

    @Transactional
    public ClassSection update(ClassSection value, long expectedVersion) {
        var entity = locked(value.id());
        if (entity.getRowVersion() != expectedVersion) throw new AcademicDeliveryService.StaleVersionException();
        entity.update(value);
        return map(repository.saveAndFlush(entity));
    }

    @Transactional(readOnly = true)
    public Optional<ClassSection> findById(UUID id) { return repository.findById(id).map(this::map); }

    @Transactional
    public ClassSection lock(UUID id) { return map(locked(id)); }

    private ClassSectionEntity locked(UUID id) {
        // Refresh acquires the row lock and replaces stale state in one operation.
        // A locking query first can reject a cached version before refresh runs.
        var entity = repository.findById(id).orElseThrow(AcademicDeliveryService.ResourceNotFoundException::new);
        entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
        return entity;
    }

    @Transactional(readOnly = true)
    public PageResult<ClassSection> search(AcademicDeliverySearch search) {
        Specification<ClassSectionEntity> specification = (root, query, b) -> {
            List<Predicate> filters = new ArrayList<>();
            if (search.query() != null) {
                String pattern = "%" + search.query().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
                filters.add(b.or(b.like(b.lower(root.get("code")), pattern, '!')));
            }
            if (search.status() != null) filters.add(b.equal(root.get("status"), AcademicDeliveryStatus.valueOf(search.status())));
            if (search.offeringId() != null) filters.add(b.equal(root.get("offeringId"), search.offeringId()));
            return b.and(filters.toArray(Predicate[]::new));
        };
        var sort = Sort.by(search.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC, search.sortField()).and(Sort.by("id"));
        var page = repository.findAll(specification, PageRequest.of(search.page(), search.size(), sort));
        return new PageResult<>(page.getContent().stream().map(this::map).toList(), page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public boolean hasOpenSections(UUID offeringId) { return repository.existsByOfferingIdAndStatus(offeringId, AcademicDeliveryStatus.OPEN); }
    private ClassSection map(ClassSectionEntity entity) {
        return new ClassSection(entity.getId(), entity.getOfferingId(), entity.getCode(), entity.getCapacity(), entity.getFacultyId(), entity.getStatus(), entity.getRowVersion(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
