package com.campus.identity.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "identity_auth_sessions")
public class AuthSessionEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccountEntity user;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revocation_reason", length = 64)
    private String revocationReason;

    protected AuthSessionEntity() {
    }

    public AuthSessionEntity(UUID id, UserAccountEntity user) {
        this.id = id;
        this.user = user;
    }

    public UUID getId() {
        return id;
    }

    public UserAccountEntity getUser() {
        return user;
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

    public String getRevocationReason() {
        return revocationReason;
    }

    public void update(Instant issuedAt, Instant expiresAt, Instant revokedAt, String revocationReason) {
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
        this.revocationReason = revocationReason;
    }
}
