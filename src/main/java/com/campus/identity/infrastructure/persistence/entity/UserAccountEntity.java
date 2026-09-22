package com.campus.identity.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import com.campus.identity.domain.AccountStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "identity_users")
@EntityListeners(AuditingEntityListener.class)
public class UserAccountEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 320)
    private String email;
    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "security_version", nullable = false)
    private long securityVersion;
    @Version @Column(name = "row_version", nullable = false)
    private long rowVersion;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToMany(fetch = FetchType.EAGER, cascade = CascadeType.MERGE)
    @JoinTable(
            name = "identity_user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<RoleEntity> roles = new LinkedHashSet<>();

    protected UserAccountEntity() {
    }

    public UserAccountEntity(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }
    public String getDisplayName() { return displayName; }
    public long getRowVersion() { return rowVersion; }

    public String getPasswordHash() {
        return passwordHash;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public long getSecurityVersion() {
        return securityVersion;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Set<RoleEntity> getRoles() {
        return roles;
    }

    public void update(
            String email,
            String displayName, String passwordHash,
            AccountStatus status,
            long securityVersion,
            Instant lastLoginAt,
            Set<RoleEntity> roles) {
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.status = status;
        this.securityVersion = securityVersion;
        this.lastLoginAt = lastLoginAt;
        this.roles = new LinkedHashSet<>(roles);
    }
}
