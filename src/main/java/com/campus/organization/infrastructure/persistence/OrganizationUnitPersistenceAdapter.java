package com.campus.organization.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.campus.organization.application.OrganizationUnitManagementService.ConcurrentOrganizationUnitModificationException;
import com.campus.organization.domain.OrganizationUnit;
import com.campus.organization.domain.OrganizationUnitRepository;
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
    private OrganizationUnit map(OrganizationUnitEntity entity) { return new OrganizationUnit(entity.getId(), entity.getCode(), entity.getName(), entity.getUnitType(), entity.getStatus(), entity.getRowVersion(), entity.getCreatedAt(), entity.getUpdatedAt()); }
}
