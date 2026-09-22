package com.campus.identity.infrastructure.persistence;

import java.util.UUID;
import java.time.Instant;

import com.campus.identity.infrastructure.persistence.entity.AuthSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface AuthSessionJpaRepository extends JpaRepository<AuthSessionEntity, UUID> {
    @Modifying @Query("update AuthSessionEntity session set session.revokedAt = :revokedAt, session.revocationReason = :reason where session.user.id = :userId and session.revokedAt is null")
    void revokeActiveForUser(UUID userId, Instant revokedAt, String reason);
}
