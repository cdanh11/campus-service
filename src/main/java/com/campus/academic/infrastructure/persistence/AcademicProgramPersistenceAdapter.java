package com.campus.academic.infrastructure.persistence;

import java.util.*;
import com.campus.academic.application.AcademicCatalogService;
import com.campus.academic.domain.*;
import com.campus.academic.infrastructure.persistence.entity.AcademicProgramEntity;
import com.campus.shared.application.PageResult;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class AcademicProgramPersistenceAdapter implements AcademicProgramRepository {
    private final AcademicProgramJpaRepository repository;
    private final EntityManager entityManager;

    AcademicProgramPersistenceAdapter(AcademicProgramJpaRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Transactional
    public AcademicProgram save(AcademicProgram value) {
        return managed(new AcademicProgramEntity(value.id()), value);
    }

    @Transactional
    public AcademicProgram saveMutation(AcademicProgram value, long expectedVersion) {
        var entity = repository.findByIdForUpdate(value.id()).orElseThrow();
        // Refresh a previously read entity after acquiring the lock, including concurrent commits.
        entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
        if (entity.getRowVersion() != expectedVersion) {
            throw new AcademicCatalogService.ConcurrentModificationException();
        }
        return managed(entity, value);
    }

    private AcademicProgram managed(AcademicProgramEntity entity, AcademicProgram value) {
        entity.update(value.code(), value.name(), value.organizationUnitId(), value.status());
        return map(repository.saveAndFlush(entity));
    }

    @Transactional(readOnly = true)
    public Optional<AcademicProgram> findById(UUID id) {
        return repository.findById(id).map(this::map);
    }

    @Transactional(readOnly = true)
    public boolean existsByCode(String code) {
        return repository.existsByCode(code);
    }

    @Transactional(readOnly = true)
    public PageResult<AcademicProgram> search(AcademicCatalogSearch search) {
        Specification<AcademicProgramEntity> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (search.query() != null) {
                // Treat wildcard characters as literal input, not SQL LIKE operators.
                String pattern = "%" + search.query().toLowerCase(Locale.ROOT)
                        .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
                predicates.add(builder.or(builder.like(builder.lower(root.get("code")), pattern, '!'),
                        builder.like(builder.lower(root.get("name")), pattern, '!')));
            }
            if (search.status() != null) {
                predicates.add(builder.equal(root.get("status"), search.status()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        Sort sort = Sort.by(search.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC, search.sortField())
                .and(Sort.by("id"));
        var page = repository.findAll(specification, PageRequest.of(search.page(), search.size(), sort));
        return new PageResult<>(page.getContent().stream().map(this::map).toList(), page.getTotalElements());
    }

    private AcademicProgram map(AcademicProgramEntity entity) {
        return new AcademicProgram(entity.getId(), entity.getCode(), entity.getName(), entity.getOrganizationUnitId(),
                entity.getStatus(), entity.getRowVersion(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
