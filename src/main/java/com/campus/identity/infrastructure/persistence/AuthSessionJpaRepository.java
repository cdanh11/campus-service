package com.campus.identity.infrastructure.persistence;

import java.util.UUID;

import com.campus.identity.infrastructure.persistence.entity.AuthSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

interface AuthSessionJpaRepository extends JpaRepository<AuthSessionEntity, UUID> {
}
