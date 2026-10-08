package com.campus.identity.api;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import com.campus.identity.domain.*;
import com.campus.identity.infrastructure.security.TokenService;
import com.campus.testsupport.PostgresApplicationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @PostgresApplicationTest
@Transactional
class FinalActiveAdminScopeIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @Autowired TokenService tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    static Stream<Arguments> nonAdministratorChanges() {
        return Stream.of(RoleCode.USER, RoleCode.FINANCE_ADMIN, RoleCode.AUDIT_VIEWER)
                .flatMap(role -> Stream.of(AccountStatus.SUSPENDED, AccountStatus.DISABLED)
                        .map(target -> Arguments.of(role, target)));
    }

    @ParameterizedTest @MethodSource("nonAdministratorChanges")
    void soleActiveAdministratorCanDeactivateNonAdministrators(RoleCode role, AccountStatus targetStatus) throws Exception {
        var actor = account(RoleCode.ADMIN, AccountStatus.ACTIVE);
        var target = account(role, AccountStatus.ACTIVE);
        assertThat(users.countActiveAdministrators()).isEqualTo(1);
        mvc.perform(patch("/api/v1/admin/users/" + target.id() + "/status")
                .header("Authorization", "Bearer " + tokens.accessToken(actor)).contentType("application/json")
                .content("{\"status\":\"" + targetStatus + "\",\"expectedVersion\":" + target.rowVersion() + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(targetStatus.name()));
        var saved = users.findById(target.id()).orElseThrow();
        assertThat(saved.rowVersion()).isEqualTo(target.rowVersion() + 1);
        assertThat(saved.securityVersion()).isEqualTo(1);
        assertThat(saved.roles()).extracting(Role::code).containsExactly(role);
        assertThat(users.countActiveAdministrators()).isEqualTo(1);
        assertAudit(actor.id(), target.id(), "STATUS_CHANGED");
    }

    @ParameterizedTest @EnumSource(value = AccountStatus.class, names = {"SUSPENDED", "DISABLED"})
    void soleActiveAdministratorCanRemoveAdminRoleFromInactiveAdministrator(AccountStatus inactiveStatus) throws Exception {
        var actor = account(RoleCode.ADMIN, AccountStatus.ACTIVE);
        var target = account(RoleCode.ADMIN, inactiveStatus);
        assertThat(users.countActiveAdministrators()).isEqualTo(1);
        mvc.perform(put("/api/v1/admin/users/" + target.id() + "/roles")
                .header("Authorization", "Bearer " + tokens.accessToken(actor)).contentType("application/json")
                .content("{\"roles\":[\"USER\"],\"expectedVersion\":" + target.rowVersion() + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("USER"));
        var saved = users.findById(target.id()).orElseThrow();
        assertThat(saved.status()).isEqualTo(inactiveStatus);
        assertThat(saved.roles()).extracting(Role::code).containsExactly(RoleCode.USER);
        assertThat(saved.rowVersion()).isEqualTo(target.rowVersion() + 1);
        assertThat(saved.securityVersion()).isEqualTo(1);
        assertThat(users.countActiveAdministrators()).isEqualTo(1);
        assertAudit(actor.id(), target.id(), "ROLES_REPLACED");
    }

    private UserAccount account(RoleCode role, AccountStatus status) {
        return users.save(UserAccount.create(UUID.randomUUID(), UUID.randomUUID() + "@example.test", "Guard scope",
                passwords.encode("test-only-password"), status, Set.of(roles.findByCode(role).orElseThrow()), Instant.now()));
    }
    private void assertAudit(UUID actor, UUID target, String action) {
        entityManager.flush();
        var event = jdbc.queryForMap("SELECT actor_user_id, action FROM identity_admin_audit_events WHERE target_user_id=?", target);
        assertThat(event).containsEntry("actor_user_id", actor).containsEntry("action", action);
    }
}
