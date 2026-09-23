package com.campus.identity.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import com.campus.identity.infrastructure.persistence.entity.RefreshTokenEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    Optional<RefreshTokenEntity> findByTokenHash(byte[] tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshTokenEntity token where token.tokenHash = :tokenHash")
    Optional<RefreshTokenEntity> findByTokenHashForUpdate(byte[] tokenHash);
}
