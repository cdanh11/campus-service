package com.campus.organization.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.campus.organization.application.OrganizationUnitManagementService.ConcurrentOrganizationUnitModificationException;
import com.campus.organization.domain.OrganizationUnit;
import com.campus.organization.domain.OrganizationUnitRepository;
import com.campus.organization.domain.OrganizationUnitSearch;
import com.campus.shared.application.PageResult;
import com.campus.organization.infrastructure.persistence.entity.OrganizationUnitEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class OrganizationUnitPersistenceAdapter implements OrganizationUnitRepository {
    private final OrganizationUnitJpaRepository repository;
    OrganizationUnitPersistenceAdapter(OrganizationUnitJpaRepository repository) { this.repository = repository; }
    @Override @Transactional public OrganizationUnit save(OrganizationUnit unit) {
        OrganizationUnitEntity entity = repository.findById(unit.id()).orElseGet(() -> new OrganizationUnitEntity(unit.id()));
        return saveManaged(entity, unit);
    }
    @Override @Transactional public OrganizationUnit saveMutation(OrganizationUnit unit, long expectedVersion) {
        OrganizationUnitEntity entity = repository.findByIdForUpdate(unit.id()).orElseThrow();
        if (entity.getRowVersion() != expectedVersion) throw new ConcurrentOrganizationUnitModificationException();
        return saveManaged(entity, unit);
    }
    private OrganizationUnit saveManaged(OrganizationUnitEntity entity, OrganizationUnit unit) {
        entity.update(unit.code(), unit.name(), unit.unitType(), unit.status());
        return map(repository.saveAndFlush(entity));
    }
    @Override @Transactional(readOnly = true) public Optional<OrganizationUnit> findById(UUID id) { return repository.findById(id).map(this::map); }
    @Override @Transactional(readOnly = true) public boolean existsByCode(String code) { return repository.existsByCode(code); }
    @Override @Transactional(readOnly = true) public List<OrganizationUnit> findAll() { return repository.findAll(org.springframework.data.domain.Sort.by("code")).stream().map(this::map).toList(); }
    @Override @Transactional(readOnly = true) public PageResult<OrganizationUnit> search(OrganizationUnitSearch search) {
        org.springframework.data.jpa.domain.Specification<OrganizationUnitEntity> specification = (root, query, builder) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            if (search.query() != null) { String value = "%" + search.query().toLowerCase(java.util.Locale.ROOT) + "%"; predicates.add(builder.or(builder.like(builder.lower(root.get("code")), value), builder.like(builder.lower(root.get("name")), value))); }
            if (search.status() != null) predicates.add(builder.equal(root.get("status"), search.status()));
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        org.springframework.data.domain.Sort sort = org.springframework.data.domain.Sort.by(search.ascending() ? org.springframework.data.domain.Sort.Direction.ASC : org.springframework.data.domain.Sort.Direction.DESC, search.sortField()).and(org.springframework.data.domain.Sort.by("id"));
        var page = repository.findAll(specification, org.springframework.data.domain.PageRequest.of(search.page(), search.size(), sort));
        return new PageResult<>(page.getContent().stream().map(this::map).toList(), page.getTotalElements());
    }
    private OrganizationUnit map(OrganizationUnitEntity entity) { return new OrganizationUnit(entity.getId(), entity.getCode(), entity.getName(), entity.getUnitType(), entity.getStatus(), entity.getRowVersion(), entity.getCreatedAt(), entity.getUpdatedAt()); }
}
