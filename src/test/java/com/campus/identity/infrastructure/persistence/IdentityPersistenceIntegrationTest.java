package com.campus.identity.infrastructure.persistence;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.sql.Timestamp;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.campus.identity.domain.AccountStatus;
import com.campus.identity.domain.AuthSession;
import com.campus.identity.domain.AuthSessionRepository;
import com.campus.identity.domain.RefreshToken;
import com.campus.identity.domain.RefreshTokenRepository;
import com.campus.identity.domain.Role;
import com.campus.identity.domain.RoleCode;
import com.campus.identity.domain.RoleRepository;
import com.campus.identity.domain.UserAccount;
import com.campus.identity.domain.UserAccountRepository;
import com.campus.identity.application.LastActiveAdministratorRequiredException;
import com.campus.identity.application.ConcurrentModificationException;
import com.campus.identity.application.SecurityMutationCoordinator;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class IdentityPersistenceIntegrationTest {

    private static final String PASSWORD_HASH = "{bcrypt}$2b$12$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZabcde";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6-alpine");

    @Autowired
    private Flyway flyway;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SecurityMutationCoordinator securityMutationCoordinator;

    @Test
    void appliesIdentityMigrationsAndSeedsUniqueRoles() {
        assertThat(flyway.info().applied()).extracting(info -> info.getVersion().getVersion()).contains("1", "2", "3", "4", "5");
        assertThat(roleRepository.findByCode(RoleCode.USER)).isPresent();
        assertThat(roleRepository.findByCode(RoleCode.ADMIN)).isPresent();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO identity_roles (id, code) VALUES (?, ?)", UUID.randomUUID(), "USER"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void persistsUserStatusRolesPasswordHashAndAuditTimestamps() {
        Role userRole = roleRepository.findByCode(RoleCode.USER).orElseThrow();
        UserAccount account = UserAccount.create(UUID.randomUUID(), "Person@Campus.Example", PASSWORD_HASH, Instant.now());
        account.replaceRoles(Set.of(userRole));

        UserAccount saved = userAccountRepository.save(account);
        saved.changeStatus(AccountStatus.SUSPENDED);
        UserAccount updated = userAccountRepository.save(saved);

        UserAccount reloaded = userAccountRepository.findByEmail("PERSON@CAMPUS.EXAMPLE").orElseThrow();
        assertThat(reloaded.status()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(reloaded.roles()).extracting(Role::code).containsExactly(RoleCode.USER);
        assertThat(reloaded.passwordHash()).isEqualTo(PASSWORD_HASH);
        assertThat(reloaded.createdAt()).isNotNull();
        assertThat(reloaded.updatedAt()).isNotNull().isAfterOrEqualTo(saved.updatedAt());
    }

    @Test
    void ordinarySaveUpdatesTheCurrentManagedEntityWhenCallerHasAnOlderRowVersion() {
        UserAccount created = userAccountRepository.save(UserAccount.create(
                UUID.randomUUID(), "managed-update@campus.example", PASSWORD_HASH, Instant.now()));
        UserAccount loggedIn = userAccountRepository.findById(created.id()).orElseThrow();
        loggedIn.recordLogin(Instant.now());
        UserAccount afterLogin = userAccountRepository.save(loggedIn);

        created.changeStatus(AccountStatus.DISABLED);
        UserAccount afterStatusChange = userAccountRepository.save(created);

        assertThat(afterStatusChange.status()).isEqualTo(AccountStatus.DISABLED);
        assertThat(afterStatusChange.rowVersion()).isGreaterThan(afterLogin.rowVersion());
        assertThat(userAccountRepository.findById(created.id()).orElseThrow().rowVersion())
                .isEqualTo(afterStatusChange.rowVersion());
    }

    @Test
    void rejectsDuplicateEmailIgnoringCase() {
        userAccountRepository.save(UserAccount.create(
                UUID.randomUUID(), "duplicate@campus.example", PASSWORD_HASH, Instant.now()));

        assertThatThrownBy(() -> userAccountRepository.save(UserAccount.create(
                UUID.randomUUID(), "DUPLICATE@campus.example", PASSWORD_HASH, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void persistsOnlyRefreshTokenDigestWithItsSession() {
        UserAccount user = userAccountRepository.save(UserAccount.create(
                UUID.randomUUID(), "session@campus.example", PASSWORD_HASH, Instant.now()));
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        AuthSession session = authSessionRepository.save(new AuthSession(
                UUID.randomUUID(), user.id(), issuedAt, issuedAt.plus(7, ChronoUnit.DAYS), null, null));
        byte[] tokenHash = new byte[32];
        tokenHash[0] = 1;
        RefreshToken refreshToken = refreshTokenRepository.save(new RefreshToken(
                UUID.randomUUID(), session.id(), tokenHash, issuedAt, issuedAt.plus(7, ChronoUnit.DAYS), null, null));

        RefreshToken reloaded = refreshTokenRepository.findByTokenHash(tokenHash).orElseThrow();
        assertThat(reloaded.id()).isEqualTo(refreshToken.id());
        assertThat(reloaded.sessionId()).isEqualTo(session.id());
        assertThat(reloaded.tokenHash()).containsExactly(tokenHash);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT OCTET_LENGTH(token_hash) FROM identity_refresh_tokens WHERE id = ?",
                Integer.class,
                refreshToken.id()))
                .isEqualTo(32);
    }

    @Test
    void enforcesRefreshTokenDatabaseConstraints() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant expiresAt = issuedAt.plus(7, ChronoUnit.DAYS);
        jdbcTemplate.update(
                "INSERT INTO identity_users (id, email, display_name, password_hash, status, security_version) VALUES (?, ?, ?, ?, ?, ?)",
                userId, "constraints@campus.example", "Constraints", PASSWORD_HASH, "ACTIVE", 0);
        jdbcTemplate.update(
                "INSERT INTO identity_auth_sessions (id, user_id, issued_at, expires_at) VALUES (?, ?, ?, ?)",
                sessionId, userId, Timestamp.from(issuedAt), Timestamp.from(expiresAt));

        byte[] validHash = new byte[32];
        validHash[0] = 2;
        insertRefreshToken(UUID.randomUUID(), sessionId, validHash, issuedAt, expiresAt);

        assertThatThrownBy(() -> insertRefreshToken(UUID.randomUUID(), sessionId, new byte[31], issuedAt, expiresAt))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRefreshToken(UUID.randomUUID(), sessionId, new byte[33], issuedAt, expiresAt))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRefreshToken(UUID.randomUUID(), sessionId, validHash, issuedAt, expiresAt))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRefreshToken(UUID.randomUUID(), UUID.randomUUID(), new byte[32], issuedAt, expiresAt))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO identity_auth_sessions (id, user_id, issued_at, expires_at) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), UUID.randomUUID(), Timestamp.from(issuedAt), Timestamp.from(expiresAt)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO identity_auth_sessions (id, user_id, issued_at, expires_at) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), userId, Timestamp.from(expiresAt), Timestamp.from(issuedAt)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRefreshToken(
                UUID.randomUUID(), sessionId, new byte[32], expiresAt, issuedAt))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void persistsAdminFoundationAndRevokesAllActiveSessionsForUser() {
        Role userRole = roleRepository.findByCode(RoleCode.USER).orElseThrow();
        UserAccount user = userAccountRepository.save(UserAccount.create(UUID.randomUUID(), "revoke@campus.example", "Revoked User", PASSWORD_HASH, AccountStatus.ACTIVE, Set.of(userRole), Instant.now()));
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        AuthSession first = authSessionRepository.save(new AuthSession(UUID.randomUUID(), user.id(), now, now.plus(1, ChronoUnit.DAYS), null, null));
        AuthSession second = authSessionRepository.save(new AuthSession(UUID.randomUUID(), user.id(), now, now.plus(1, ChronoUnit.DAYS), null, null));
        authSessionRepository.revokeActiveSessionsForUser(user.id(), now, "ACCOUNT_DISABLED");
        assertThat(authSessionRepository.findById(first.id()).orElseThrow().revokedAt()).isEqualTo(now);
        assertThat(authSessionRepository.findById(second.id()).orElseThrow().revocationReason()).isEqualTo("ACCOUNT_DISABLED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM identity_admin_guard WHERE guard_id = 1", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'identity_users' AND column_name IN ('display_name', 'row_version')", Integer.class)).isEqualTo(2);
    }

    @Test
    void serializesConcurrentActiveAdministratorReductionsThroughGuardRow() throws Exception {
        Role adminRole = roleRepository.findByCode(RoleCode.ADMIN).orElseThrow();
        UserAccount first = userAccountRepository.save(UserAccount.create(UUID.randomUUID(), "first-admin@campus.example", "First Admin", PASSWORD_HASH, AccountStatus.ACTIVE, Set.of(adminRole), Instant.now()));
        UserAccount second = userAccountRepository.save(UserAccount.create(UUID.randomUUID(), "second-admin@campus.example", "Second Admin", PASSWORD_HASH, AccountStatus.ACTIVE, Set.of(adminRole), Instant.now()));
        UUID actorId = first.id();

        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Class<?>> suspendFirst = () -> resultOf(() -> securityMutationCoordinator.changeStatus(actorId, first.id(), AccountStatus.SUSPENDED, first.rowVersion()));
            Callable<Class<?>> suspendSecond = () -> resultOf(() -> securityMutationCoordinator.changeStatus(actorId, second.id(), AccountStatus.SUSPENDED, second.rowVersion()));
            Future<Class<?>> firstResult = executor.submit(suspendFirst);
            Future<Class<?>> secondResult = executor.submit(suspendSecond);

            assertThat(Set.of(firstResult.get(), secondResult.get())).containsExactlyInAnyOrder(Void.class, LastActiveAdministratorRequiredException.class);
        }
        assertThat(userAccountRepository.countActiveAdministrators()).isEqualTo(1);
    }

    @Test
    void rejectsStaleExpectedRowVersionBeforeRevokingSessionsOrWritingAudit() {
        Role userRole = roleRepository.findByCode(RoleCode.USER).orElseThrow();
        UserAccount user = userAccountRepository.save(UserAccount.create(UUID.randomUUID(), "versioned@campus.example", "Versioned User", PASSWORD_HASH, AccountStatus.ACTIVE, Set.of(userRole), Instant.now()));
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        authSessionRepository.save(new AuthSession(UUID.randomUUID(), user.id(), now, now.plus(1, ChronoUnit.DAYS), null, null));

        securityMutationCoordinator.resetPassword(user.id(), user.id(), PASSWORD_HASH, user.rowVersion());

        assertThatThrownBy(() -> securityMutationCoordinator.resetPassword(user.id(), user.id(), PASSWORD_HASH, user.rowVersion()))
                .isInstanceOf(ConcurrentModificationException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM identity_admin_audit_events WHERE target_user_id = ?", Integer.class, user.id())).isEqualTo(1);
        assertThat(authSessionRepository.findById(jdbcTemplate.queryForObject("SELECT id FROM identity_auth_sessions WHERE user_id = ?", UUID.class, user.id())).orElseThrow().revocationReason())
                .isEqualTo("PASSWORD_RESET");
    }

    private static Class<?> resultOf(ThrowingRunnable mutation) {
        try {
            mutation.run();
            return Void.class;
        } catch (RuntimeException exception) {
            return exception.getClass();
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run(); }

    private void insertRefreshToken(UUID id, UUID sessionId, byte[] tokenHash, Instant issuedAt, Instant expiresAt) {
        jdbcTemplate.update(
                "INSERT INTO identity_refresh_tokens (id, session_id, token_hash, issued_at, expires_at) VALUES (?, ?, ?, ?, ?)",
                id, sessionId, tokenHash, Timestamp.from(issuedAt), Timestamp.from(expiresAt));
    }
}
