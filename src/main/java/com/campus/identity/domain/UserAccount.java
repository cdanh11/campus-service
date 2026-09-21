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
    private final String passwordHash;
    private AccountStatus status;
    private Set<Role> roles;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant lastLoginAt;
    private long securityVersion;

    private UserAccount(
            UUID id,
            String email,
            String passwordHash,
            AccountStatus status,
            Set<Role> roles,
            Instant createdAt,
            Instant updatedAt,
            Instant lastLoginAt,
            long securityVersion) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.email = normalizeEmail(email);
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
    }

    public static UserAccount create(UUID id, String email, String passwordHash, Instant now) {
        return new UserAccount(id, email, passwordHash, AccountStatus.ACTIVE, Set.of(), now, now, null, 0);
    }

    public static UserAccount rehydrate(
            UUID id,
            String email,
            String passwordHash,
            AccountStatus status,
            Set<Role> roles,
            Instant createdAt,
            Instant updatedAt,
            Instant lastLoginAt,
            long securityVersion) {
        return new UserAccount(
                id, email, passwordHash, status, roles, createdAt, updatedAt, lastLoginAt, securityVersion);
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

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

    public void changeStatus(AccountStatus status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public void replaceRoles(Set<Role> roles) {
        this.roles = copyRoles(roles);
    }

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
