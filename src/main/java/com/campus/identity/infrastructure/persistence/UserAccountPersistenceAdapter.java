package com.campus.identity.infrastructure.persistence;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.campus.identity.domain.RoleCode;
import com.campus.identity.domain.UserAccount;
import com.campus.identity.domain.UserAccountRepository;
import com.campus.identity.infrastructure.persistence.entity.RoleEntity;
import com.campus.identity.infrastructure.persistence.entity.UserAccountEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class UserAccountPersistenceAdapter implements UserAccountRepository {

    private final UserAccountJpaRepository userAccountJpaRepository;
    private final RoleJpaRepository roleJpaRepository;
    private final IdentityPersistenceMapper mapper;

    UserAccountPersistenceAdapter(
            UserAccountJpaRepository userAccountJpaRepository,
            RoleJpaRepository roleJpaRepository,
            IdentityPersistenceMapper mapper) {
        this.userAccountJpaRepository = userAccountJpaRepository;
        this.roleJpaRepository = roleJpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public UserAccount save(UserAccount userAccount) {
        UserAccountEntity entity = userAccountJpaRepository.findById(userAccount.id())
                .orElseGet(() -> new UserAccountEntity(userAccount.id()));
        Set<RoleCode> roleCodes = userAccount.roles().stream().map(role -> role.code()).collect(Collectors.toSet());
        Set<RoleEntity> roles = roleJpaRepository.findAllByCodeIn(roleCodes).stream().collect(Collectors.toSet());
        if (roles.size() != roleCodes.size()) {
            throw new IllegalArgumentException("all assigned roles must exist");
        }

        entity.update(
                userAccount.email(),
                userAccount.passwordHash(),
                userAccount.status(),
                userAccount.securityVersion(),
                userAccount.lastLoginAt(),
                roles);
        return mapper.toDomain(userAccountJpaRepository.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccount> findById(java.util.UUID id) {
        return userAccountJpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccount> findByEmail(String email) {
        return userAccountJpaRepository.findByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT)).map(mapper::toDomain);
    }
}
