package com.campus.organization.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import com.campus.organization.infrastructure.persistence.entity.OrganizationUnitEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface OrganizationUnitJpaRepository extends JpaRepository<OrganizationUnitEntity, UUID>, JpaSpecificationExecutor<OrganizationUnitEntity> {
    boolean existsByCode(String code);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select unit from OrganizationUnitEntity unit where unit.id = :id")
    Optional<OrganizationUnitEntity> findByIdForUpdate(UUID id);
}
