package com.campus.identity.infrastructure.persistence;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import com.campus.identity.application.SecurityMutationCoordinator;
import com.campus.identity.domain.AccountStatus;
import com.campus.identity.domain.AdminAuditEventRepository;
import com.campus.identity.domain.AuthSession;
import com.campus.identity.domain.AuthSessionRepository;
import com.campus.identity.domain.Role;
import com.campus.identity.domain.RoleCode;
import com.campus.identity.domain.RoleRepository;
import com.campus.identity.domain.UserAccount;
import com.campus.identity.domain.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
@Import(SecurityMutationAuditRollbackIntegrationTest.AuditFailureConfiguration.class)
class SecurityMutationAuditRollbackIntegrationTest {

    private static final String PASSWORD_HASH = "{bcrypt}$2b$12$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZabcde";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6-alpine");

    @Autowired private SecurityMutationCoordinator coordinator;
    @Autowired private UserAccountRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private AuthSessionRepository sessions;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void rollsBackPasswordAndSessionRevocationWhenAuditPersistenceFails() {
        Role userRole = roles.findByCode(RoleCode.USER).orElseThrow();
        UserAccount target = users.save(UserAccount.create(UUID.randomUUID(), "audit-failure@campus.example", "Audit Failure",
                PASSWORD_HASH, AccountStatus.ACTIVE, Set.of(userRole), Instant.now()));
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        AuthSession session = sessions.save(new AuthSession(UUID.randomUUID(), target.id(), issuedAt, issuedAt.plus(1, ChronoUnit.DAYS), null, null));

        assertThatThrownBy(() -> coordinator.resetPassword(UUID.randomUUID(), target.id(), PASSWORD_HASH, target.rowVersion()))
                .isInstanceOf(IllegalStateException.class).hasMessage("Injected audit failure");

        UserAccount reloaded = users.findById(target.id()).orElseThrow();
        assertThat(reloaded.passwordHash()).isEqualTo(target.passwordHash());
        assertThat(reloaded.status()).isEqualTo(target.status());
        assertThat(reloaded.securityVersion()).isEqualTo(target.securityVersion());
        assertThat(reloaded.rowVersion()).isEqualTo(target.rowVersion());
        assertThat(sessions.findById(session.id()).orElseThrow().revokedAt()).isNull();
        assertThat(jdbcTemplate.queryForObject("select count(*) from identity_admin_audit_events where target_user_id = ?", Integer.class, target.id())).isZero();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AuditFailureConfiguration {
        @Bean
        @Primary
        AdminAuditEventRepository failingAuditEventRepository() {
            return event -> { throw new IllegalStateException("Injected audit failure"); };
        }
    }
}
