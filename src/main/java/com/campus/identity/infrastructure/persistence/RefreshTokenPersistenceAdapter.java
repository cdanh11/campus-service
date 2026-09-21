package com.campus.identity.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import com.campus.identity.domain.RefreshToken;
import com.campus.identity.domain.RefreshTokenRepository;
import com.campus.identity.infrastructure.persistence.entity.AuthSessionEntity;
import com.campus.identity.infrastructure.persistence.entity.RefreshTokenEntity;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class RefreshTokenPersistenceAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository refreshTokenJpaRepository;
    private final EntityManager entityManager;
    private final IdentityPersistenceMapper mapper;

    RefreshTokenPersistenceAdapter(
            RefreshTokenJpaRepository refreshTokenJpaRepository,
            EntityManager entityManager,
            IdentityPersistenceMapper mapper) {
        this.refreshTokenJpaRepository = refreshTokenJpaRepository;
        this.entityManager = entityManager;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public RefreshToken save(RefreshToken refreshToken) {
        RefreshTokenEntity entity = refreshTokenJpaRepository.findById(refreshToken.id())
                .orElseGet(() -> new RefreshTokenEntity(
                        refreshToken.id(), entityManager.getReference(AuthSessionEntity.class, refreshToken.sessionId())));
        entity.update(
                refreshToken.tokenHash(),
                refreshToken.issuedAt(),
                refreshToken.expiresAt(),
                refreshToken.revokedAt(),
                refreshToken.replacedById());
        return mapper.toDomain(refreshTokenJpaRepository.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshToken> findById(UUID id) {
        return refreshTokenJpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshToken> findByTokenHash(byte[] tokenHash) {
        return refreshTokenJpaRepository.findByTokenHash(tokenHash).map(mapper::toDomain);
    }
}
