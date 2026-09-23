package com.campus.identity.domain;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public final class RefreshToken {

    private final UUID id;
    private final UUID sessionId;
    private final byte[] tokenHash;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private Instant revokedAt;
    private UUID replacedById;

    public RefreshToken(
            UUID id,
            UUID sessionId,
            byte[] tokenHash,
            Instant issuedAt,
            Instant expiresAt,
            Instant revokedAt,
            UUID replacedById) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
        this.tokenHash = copyHash(tokenHash);
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
        this.revokedAt = revokedAt;
        this.replacedById = replacedById;
    }

    public UUID id() {
        return id;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public byte[] tokenHash() {
        return Arrays.copyOf(tokenHash, tokenHash.length);
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

    public UUID replacedById() {
        return replacedById;
    }

    public void revoke(Instant revokedAt, UUID replacedById) {
        this.revokedAt = Objects.requireNonNull(revokedAt, "revokedAt must not be null");
        this.replacedById = replacedById;
    }

    private static byte[] copyHash(byte[] tokenHash) {
        Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        if (tokenHash.length != 32) {
            throw new IllegalArgumentException("tokenHash must be a SHA-256 digest");
        }
        return Arrays.copyOf(tokenHash, tokenHash.length);
    }
}
