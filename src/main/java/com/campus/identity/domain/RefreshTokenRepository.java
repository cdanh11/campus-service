package com.campus.identity.domain;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    RefreshToken save(RefreshToken refreshToken);

    Optional<RefreshToken> findById(UUID id);

    Optional<RefreshToken> findByTokenHash(byte[] tokenHash);

    Optional<RefreshToken> findByTokenHashForUpdate(byte[] tokenHash);
}
