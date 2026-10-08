package com.campus.identity.application;

import com.campus.identity.domain.AdminAuditEventRepository;
import com.campus.testsupport.PostgresApplicationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@PostgresApplicationTest
class FirstAdministratorProvisioningIntegrationTest {
    private static final String PASSWORD = "Bootstrap-test-Long!42";
    @Autowired FirstAdministratorProvisioningService provisioning;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @MockitoSpyBean AdminAuditEventRepository audits;

    @BeforeEach void clearIsolatedFixture() {
        reset(audits);
        jdbc.update("DELETE FROM identity_admin_audit_events");
        jdbc.update("DELETE FROM identity_user_roles");
        jdbc.update("DELETE FROM identity_users");
    }

    @Test void createsOneValidatedHashedAdministratorAndAtomicAuditWithoutWebServer() {
        var id = provisioning.provision("FIRST@example.test", "First administrator", PASSWORD);
        assertThat(jdbc.queryForObject("SELECT email FROM identity_users WHERE id=?", String.class, id))
                .isEqualTo("first@example.test");
        String hash = jdbc.queryForObject("SELECT password_hash FROM identity_users WHERE id=?", String.class, id);
        assertThat(hash).startsWith("{bcrypt}").isNotEqualTo(PASSWORD);
        assertThat(encoder.matches(PASSWORD, hash)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_user_roles ur JOIN identity_roles r ON r.id=ur.role_id WHERE ur.user_id=? AND r.code='ADMIN'", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_admin_audit_events WHERE actor_user_id=? AND target_user_id=? AND action='USER_CREATED'", Integer.class, id, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT metadata FROM identity_admin_audit_events", String.class)).isEqualTo("{}");
    }

    @Test void refusesExistingAdministratorEvenWhenDisabled() {
        var id = provisioning.provision("first@example.test", "First administrator", PASSWORD);
        jdbc.update("UPDATE identity_users SET status='DISABLED' WHERE id=?", id);
        assertThatThrownBy(() -> provisioning.provision("second@example.test", "Second", PASSWORD))
                .isInstanceOf(FirstAdministratorProvisioningService.AdministratorAlreadyExistsException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_users", Integer.class)).isEqualTo(1);
    }

    @Test void rejectsInvalidPasswordAndProfileWithoutCreatingData() {
        assertThatThrownBy(() -> provisioning.provision("first@example.test", "First", "short"))
                .isInstanceOf(AdminUserManagementService.RequestValidationException.class);
        assertThatThrownBy(() -> provisioning.provision("invalid", "First", PASSWORD))
                .isInstanceOf(jakarta.validation.ConstraintViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_users", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_admin_audit_events", Integer.class)).isZero();
    }

    @Test void auditFailureRollsBackAdministratorAndRoles() {
        doThrow(new IllegalStateException("Simulated isolated audit failure")).when(audits).save(any());
        assertThatThrownBy(() -> provisioning.provision("first@example.test", "First", PASSWORD))
                .isInstanceOf(org.springframework.dao.DataAccessException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("Simulated isolated audit failure");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_users", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_user_roles", Integer.class)).isZero();
    }

    @Test void competingSetupTransactionsCreateExactlyOneAdministrator() throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> attempt(barrier, "first@example.test"));
            var second = executor.submit(() -> attempt(barrier, "second@example.test"));
            assertThat(first.get(15, TimeUnit.SECONDS) + second.get(15, TimeUnit.SECONDS)).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_users", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_admin_audit_events", Integer.class)).isEqualTo(1);
    }

    private int attempt(CyclicBarrier barrier, String email) throws Exception {
        barrier.await(5, TimeUnit.SECONDS);
        try { provisioning.provision(email, "First administrator", PASSWORD); return 1; }
        catch (FirstAdministratorProvisioningService.AdministratorAlreadyExistsException expected) { return 0; }
    }
}
