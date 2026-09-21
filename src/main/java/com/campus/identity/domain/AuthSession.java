package com.campus.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class AuthSession {

    private final UUID id;
    private final UUID userId;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private Instant revokedAt;
    private String revocationReason;

    public AuthSession(UUID id, UUID userId, Instant issuedAt, Instant expiresAt, Instant revokedAt, String revocationReason) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
        this.revokedAt = revokedAt;
        this.revocationReason = revocationReason;
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public String revocationReason() {
        return revocationReason;
    }

    public void revoke(Instant revokedAt, String revocationReason) {
        this.revokedAt = Objects.requireNonNull(revokedAt, "revokedAt must not be null");
        this.revocationReason = Objects.requireNonNull(revocationReason, "revocationReason must not be null");
    }
}
