package com.campus.identity.domain;

import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository {

    AuthSession save(AuthSession session);

    Optional<AuthSession> findById(UUID id);
    void revokeActiveSessionsForUser(UUID userId, java.time.Instant revokedAt, String reason);
}
