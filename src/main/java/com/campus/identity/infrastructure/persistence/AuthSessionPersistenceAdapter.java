package com.campus.identity.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import com.campus.identity.domain.AuthSession;
import com.campus.identity.domain.AuthSessionRepository;
import com.campus.identity.infrastructure.persistence.entity.AuthSessionEntity;
import com.campus.identity.infrastructure.persistence.entity.UserAccountEntity;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class AuthSessionPersistenceAdapter implements AuthSessionRepository {

    private final AuthSessionJpaRepository authSessionJpaRepository;
    private final EntityManager entityManager;
    private final IdentityPersistenceMapper mapper;

    AuthSessionPersistenceAdapter(
            AuthSessionJpaRepository authSessionJpaRepository,
            EntityManager entityManager,
            IdentityPersistenceMapper mapper) {
        this.authSessionJpaRepository = authSessionJpaRepository;
        this.entityManager = entityManager;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public AuthSession save(AuthSession session) {
        AuthSessionEntity entity = authSessionJpaRepository.findById(session.id())
                .orElseGet(() -> new AuthSessionEntity(
                        session.id(), entityManager.getReference(UserAccountEntity.class, session.userId())));
        entity.update(session.issuedAt(), session.expiresAt(), session.revokedAt(), session.revocationReason());
        return mapper.toDomain(authSessionJpaRepository.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthSession> findById(UUID id) {
        return authSessionJpaRepository.findById(id).map(mapper::toDomain);
    }
}
