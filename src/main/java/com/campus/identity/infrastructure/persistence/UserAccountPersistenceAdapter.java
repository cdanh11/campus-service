package com.campus.identity.infrastructure.persistence;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.campus.identity.domain.RoleCode;
import com.campus.identity.domain.UserAccount;
import com.campus.identity.domain.UserAccountRepository;
import com.campus.identity.domain.UserAccountPage;
import com.campus.identity.domain.UserAccountSearch;
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
        return saveManaged(entity, userAccount);
    }

    @Override
    @Transactional
    public UserAccount saveAdminMutation(UserAccount userAccount, long expectedVersion) {
        UserAccountEntity entity = userAccountJpaRepository.findByIdForUpdate(userAccount.id()).orElseThrow();
        if (entity.getRowVersion() != expectedVersion) {
            throw new com.campus.identity.application.ConcurrentModificationException();
        }
        return saveManaged(entity, userAccount);
    }

    private UserAccount saveManaged(UserAccountEntity entity, UserAccount userAccount) {
        Set<RoleCode> roleCodes = userAccount.roles().stream().map(role -> role.code()).collect(Collectors.toSet());
        Set<RoleEntity> roles = roleJpaRepository.findAllByCodeIn(roleCodes).stream().collect(Collectors.toSet());
        if (roles.size() != roleCodes.size()) {
            throw new IllegalArgumentException("all assigned roles must exist");
        }

        entity.update(
                userAccount.email(),
                userAccount.displayName(),
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
    @Transactional
    public Optional<UserAccount> findByIdForUpdate(java.util.UUID id) {
        return userAccountJpaRepository.findByIdForUpdate(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccount> findByEmail(String email) {
        return userAccountJpaRepository.findByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT)).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveAdministrators() {
        return userAccountJpaRepository.countActiveAdministrators();
    }

    @Override
    @Transactional(readOnly = true)
    public UserAccountPage search(UserAccountSearch search) {
        org.springframework.data.jpa.domain.Specification<UserAccountEntity> specification = (root, query, builder) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            if (search.query() != null) {
                String value = "%" + search.query().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(builder.like(builder.lower(root.get("email")), value), builder.like(builder.lower(root.get("displayName")), value)));
            }
            if (search.status() != null) predicates.add(builder.equal(root.get("status"), search.status()));
            if (search.role() != null) predicates.add(builder.equal(root.join("roles").get("code"), search.role()));
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        org.springframework.data.domain.Sort sort = org.springframework.data.domain.Sort.by(search.ascending() ? org.springframework.data.domain.Sort.Direction.ASC : org.springframework.data.domain.Sort.Direction.DESC, search.sortField())
                .and(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "id"));
        var result = userAccountJpaRepository.findAll(specification, org.springframework.data.domain.PageRequest.of(search.page(), search.size(), sort));
        return new UserAccountPage(result.getContent().stream().map(mapper::toDomain).toList(), result.getTotalElements());
    }
}
