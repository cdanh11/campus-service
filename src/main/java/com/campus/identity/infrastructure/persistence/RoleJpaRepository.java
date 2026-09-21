package com.campus.identity.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.campus.identity.domain.RoleCode;
import com.campus.identity.infrastructure.persistence.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

interface RoleJpaRepository extends JpaRepository<RoleEntity, UUID> {

    Optional<RoleEntity> findByCode(RoleCode code);

    List<RoleEntity> findAllByCodeIn(Collection<RoleCode> codes);
}
