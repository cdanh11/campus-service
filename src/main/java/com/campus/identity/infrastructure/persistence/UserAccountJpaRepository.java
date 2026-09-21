package com.campus.identity.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import com.campus.identity.infrastructure.persistence.entity.UserAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserAccountJpaRepository extends JpaRepository<UserAccountEntity, UUID> {

    Optional<UserAccountEntity> findByEmailIgnoreCase(String email);
}
