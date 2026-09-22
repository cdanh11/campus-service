package com.campus.identity.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import com.campus.identity.infrastructure.persistence.entity.UserAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

interface UserAccountJpaRepository extends JpaRepository<UserAccountEntity, UUID> {

    Optional<UserAccountEntity> findByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from UserAccountEntity user where user.id = :id")
    Optional<UserAccountEntity> findByIdForUpdate(UUID id);

    @Query("select count(distinct user.id) from UserAccountEntity user join user.roles role where user.status = com.campus.identity.domain.AccountStatus.ACTIVE and role.code = com.campus.identity.domain.RoleCode.ADMIN")
    long countActiveAdministrators();
}
