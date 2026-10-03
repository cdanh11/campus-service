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
class AcademicTermPersistenceAdapter implements AcademicTermRepository {
    private final AcademicTermJpaRepository repository;
    private final EntityManager entityManager;

    AcademicTermPersistenceAdapter(AcademicTermJpaRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Transactional
    public AcademicTerm create(AcademicTerm value) {
        var entity = new AcademicTermEntity(value.id());
        entity.update(value);
        return map(repository.saveAndFlush(entity));
    }

    @Transactional
    public AcademicTerm update(AcademicTerm value, long expectedVersion) {
        var entity = locked(value.id());
        if (entity.getRowVersion() != expectedVersion) throw new AcademicDeliveryService.StaleVersionException();
        entity.update(value);
        return map(repository.saveAndFlush(entity));
    }

    @Transactional(readOnly = true)
    public Optional<AcademicTerm> findById(UUID id) { return repository.findById(id).map(this::map); }

    @Transactional
    public AcademicTerm lock(UUID id) { return map(locked(id)); }

    private AcademicTermEntity locked(UUID id) {
        var entity = repository.findByIdForUpdate(id).orElseThrow(AcademicDeliveryService.ResourceNotFoundException::new);
        entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
        return entity;
    }

    @Transactional(readOnly = true)
    public PageResult<AcademicTerm> search(AcademicDeliverySearch search) {
        Specification<AcademicTermEntity> specification = (root, query, b) -> {
            List<Predicate> filters = new ArrayList<>();
            if (search.query() != null) {
                String pattern = "%" + search.query().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
                filters.add(b.or(b.like(b.lower(root.get("code")), pattern, '!'),
                        b.like(b.lower(root.get("name")), pattern, '!')));
            }
            if (search.status() != null) filters.add(b.equal(root.get("status"), AcademicTermStatus.valueOf(search.status())));
            return b.and(filters.toArray(Predicate[]::new));
        };
        var sort = Sort.by(search.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC, search.sortField()).and(Sort.by("id"));
        var page = repository.findAll(specification, PageRequest.of(search.page(), search.size(), sort));
        return new PageResult<>(page.getContent().stream().map(this::map).toList(), page.getTotalElements());
    }

    private AcademicTerm map(AcademicTermEntity entity) {
        return new AcademicTerm(entity.getId(), entity.getCode(), entity.getName(), entity.getStartDate(), entity.getEndDate(), entity.getStatus(), entity.getRowVersion(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
