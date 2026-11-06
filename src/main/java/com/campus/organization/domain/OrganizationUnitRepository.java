package com.campus.organization.domain;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import com.campus.shared.application.PageResult;

public interface OrganizationUnitRepository {
    OrganizationUnit save(OrganizationUnit organizationUnit);
    OrganizationUnit saveMutation(OrganizationUnit organizationUnit, long expectedVersion);
    Optional<OrganizationUnit> findById(UUID id);
    boolean existsByCode(String code);
    List<OrganizationUnit> findAll();
    List<OrganizationUnit> findAllById(Collection<UUID> ids);
    PageResult<OrganizationUnit> search(OrganizationUnitSearch search);
}
