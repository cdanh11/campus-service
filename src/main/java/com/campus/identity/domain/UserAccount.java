package com.campus.identity.domain;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class UserAccount {

    private static final Pattern BCRYPT_PASSWORD_HASH = Pattern.compile(
            "\\A\\{bcrypt}\\$2[aby]\\$(?:1[0-9]|2[0-9]|3[01])\\$[./A-Za-z0-9]{53}\\z");

    private final UUID id;
    private final String email;
    private final String displayName;
    private String passwordHash;
    private AccountStatus status;
    private Set<Role> roles;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant lastLoginAt;
    private long securityVersion;
    private long rowVersion;

    private UserAccount(
            UUID id,
            String email,
            String displayName, String passwordHash,
            AccountStatus status,
            Set<Role> roles,
            Instant createdAt,
            Instant updatedAt,
            Instant lastLoginAt,
            long securityVersion, long rowVersion) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.email = normalizeEmail(email);
        this.displayName = normalizeDisplayName(displayName);
        this.passwordHash = requirePasswordHash(passwordHash);
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.roles = copyRoles(roles);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        this.lastLoginAt = lastLoginAt;

        if (securityVersion < 0) {
            throw new IllegalArgumentException("securityVersion must not be negative");
        }
        this.securityVersion = securityVersion;
        if (rowVersion < 0) throw new IllegalArgumentException("rowVersion must not be negative");
        this.rowVersion = rowVersion;
    }

    public static UserAccount create(UUID id, String email, String passwordHash, Instant now) {
        return new UserAccount(id, email, email, passwordHash, AccountStatus.ACTIVE, Set.of(), now, now, null, 0, 0);
    }
    public static UserAccount create(UUID id, String email, String displayName, String passwordHash, AccountStatus status, Set<Role> roles, Instant now) {
        if (Objects.requireNonNull(roles, "roles must not be null").isEmpty()) {
            throw new IllegalArgumentException("roles must not be empty");
        }
        return new UserAccount(id, email, displayName, passwordHash, status, roles, now, now, null, 0, 0);
    }

    public static UserAccount rehydrate(
            UUID id,
            String email,
            String displayName, String passwordHash,
            AccountStatus status,
            Set<Role> roles,
            Instant createdAt,
            Instant updatedAt,
            Instant lastLoginAt,
            long securityVersion, long rowVersion) {
        return new UserAccount(
                id, email, displayName, passwordHash, status, roles, createdAt, updatedAt, lastLoginAt, securityVersion, rowVersion);
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }
    public String displayName() { return displayName; }

    public String passwordHash() {
        return passwordHash;
    }

    public AccountStatus status() {
        return status;
    }

    public Set<Role> roles() {
        return Set.copyOf(roles);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant lastLoginAt() {
        return lastLoginAt;
    }

    public long securityVersion() {
        return securityVersion;
    }
    public long rowVersion() { return rowVersion; }

    public void changeStatus(AccountStatus status) {
        AccountStatus target = Objects.requireNonNull(status, "status must not be null");
        if (this.status == target) {
            return;
        }
        if ((this.status == AccountStatus.ACTIVE && target != AccountStatus.SUSPENDED && target != AccountStatus.DISABLED)
                || (this.status == AccountStatus.SUSPENDED && target != AccountStatus.ACTIVE && target != AccountStatus.DISABLED)
                || (this.status == AccountStatus.DISABLED && target != AccountStatus.ACTIVE)) {
            throw new IllegalStateException("account status transition is not allowed");
        }
        this.status = target;
        incrementSecurityVersion();
    }

    public void replaceRoles(Set<Role> roles) {
        if (roles.isEmpty()) throw new IllegalArgumentException("roles must not be empty");
        this.roles = copyRoles(roles);
        incrementSecurityVersion();
    }
    public void replacePasswordHash(String passwordHash) { this.passwordHash = requirePasswordHash(passwordHash); incrementSecurityVersion(); }

    public void recordLogin(Instant loggedInAt) {
        this.lastLoginAt = Objects.requireNonNull(loggedInAt, "loggedInAt must not be null");
    }

    public void incrementSecurityVersion() {
        securityVersion++;
    }

    private static String normalizeEmail(String email) {
        String normalized = Objects.requireNonNull(email, "email must not be null").trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.isBlank() || normalized.length() > 320 || !normalized.contains("@")) {
            throw new IllegalArgumentException("email must be a valid institutional email address");
        }
        return normalized;
    }
    private static String normalizeDisplayName(String displayName) { String normalized = Objects.requireNonNull(displayName, "displayName must not be null").trim(); if (normalized.length() < 2 || normalized.length() > 100) throw new IllegalArgumentException("displayName length must be between 2 and 100"); return normalized; }

    private static String requirePasswordHash(String passwordHash) {
        if (passwordHash == null || !BCRYPT_PASSWORD_HASH.matcher(passwordHash).matches()) {
            throw new IllegalArgumentException("passwordHash must be a BCrypt encoded value");
        }
        return passwordHash;
    }

    private static Set<Role> copyRoles(Set<Role> roles) {
        Objects.requireNonNull(roles, "roles must not be null");
        Set<Role> copiedRoles = new LinkedHashSet<>(roles);
        long distinctCodes = copiedRoles.stream().map(Role::code).distinct().count();
        if (distinctCodes != copiedRoles.size()) {
            throw new IllegalArgumentException("roles must not contain duplicate role codes");
        }
        return copiedRoles;
    }
}
