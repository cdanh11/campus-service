package com.campus.identity.infrastructure.persistence;

import java.util.Optional;

import com.campus.identity.domain.Role;
import com.campus.identity.domain.RoleCode;
import com.campus.identity.domain.RoleRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class RolePersistenceAdapter implements RoleRepository {

    private final RoleJpaRepository roleJpaRepository;
    private final IdentityPersistenceMapper mapper;

    RolePersistenceAdapter(RoleJpaRepository roleJpaRepository, IdentityPersistenceMapper mapper) {
        this.roleJpaRepository = roleJpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Role> findByCode(RoleCode code) {
        return roleJpaRepository.findByCode(code).map(mapper::toDomain);
    }
}
