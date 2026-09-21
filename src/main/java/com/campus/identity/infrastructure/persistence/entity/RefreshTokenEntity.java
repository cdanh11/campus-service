package com.campus.identity.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "identity_refresh_tokens")
public class RefreshTokenEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private AuthSessionEntity session;

    @Column(name = "token_hash", nullable = false, columnDefinition = "bytea")
    private byte[] tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_id")
    private UUID replacedById;

    protected RefreshTokenEntity() {
    }

    public RefreshTokenEntity(UUID id, AuthSessionEntity session) {
        this.id = id;
        this.session = session;
    }

    public UUID getId() {
        return id;
    }

    public AuthSessionEntity getSession() {
        return session;
    }

    public byte[] getTokenHash() {
        return tokenHash == null ? null : Arrays.copyOf(tokenHash, tokenHash.length);
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public UUID getReplacedById() {
        return replacedById;
    }

    public void update(
            byte[] tokenHash,
            Instant issuedAt,
            Instant expiresAt,
            Instant revokedAt,
            UUID replacedById) {
        this.tokenHash = Arrays.copyOf(tokenHash, tokenHash.length);
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
        this.replacedById = replacedById;
    }
}
