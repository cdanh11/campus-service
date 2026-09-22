package com.campus.identity.api;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;

import jakarta.servlet.http.Cookie;
import com.campus.identity.domain.AccountStatus;
import com.campus.identity.domain.Role;
import com.campus.identity.domain.RoleCode;
import com.campus.identity.domain.RoleRepository;
import com.campus.identity.domain.UserAccount;
import com.campus.identity.domain.UserAccountRepository;
import com.campus.identity.infrastructure.security.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AdminUserControllerIntegrationTest {
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6-alpine");
    @Autowired MockMvc mockMvc;
    @Autowired UserAccountRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @Autowired TokenService tokens;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired TestRestTemplate restTemplate;
    @LocalServerPort int port;

    @Test
    void restrictsAdminEndpointsAndCreatesThenSearchesUsers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("MISSING_ACCESS_TOKEN"));
        String studentToken = tokens.accessToken(user("student@campus.example", RoleCode.USER));
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + studentToken)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        String adminToken = tokens.accessToken(user("admin@campus.example", RoleCode.ADMIN));
        mockMvc.perform(post("/api/v1/admin/users").header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"New@campus.example\",\"displayName\":\" New User \",\"initialPassword\":\"valid-password\",\"roles\":[\"USER\"]}"))
                .andExpect(status().isCreated()).andExpect(header().exists("Location")).andExpect(jsonPath("$.email").value("new@campus.example")).andExpect(jsonPath("$.displayName").value("New User")).andExpect(jsonPath("$.passwordHash").doesNotExist());
        mockMvc.perform(get("/api/v1/admin/users?q=new&sort=email,asc").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].email").value("new@campus.example")).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void createsUnicodeNamesAndReturnsRolesInStableOrder() throws Exception {
        String token = tokens.accessToken(user("create-admin@campus.example", RoleCode.ADMIN));
        String displayName = "😀".repeat(100);
        var created = mockMvc.perform(post("/api/v1/admin/users").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"unicode@campus.example\",\"displayName\":\"" + displayName + "\",\"initialPassword\":\"valid-password\",\"roles\":[\"USER\",\"ADMIN\"]}"))
                .andExpect(status().isCreated()).andExpect(header().exists("Location")).andExpect(jsonPath("$.displayName").value(displayName)).andExpect(jsonPath("$.roles[0]").value("ADMIN")).andExpect(jsonPath("$.roles[1]").value("USER"))
                .andReturn().getResponse().getContentAsString();
        String userId = created.replaceAll(".*\\\"id\\\":\\\"([^\\\"]+).*", "$1");
        mockMvc.perform(get("/api/v1/admin/users/" + userId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("ADMIN")).andExpect(jsonPath("$.roles[1]").value("USER"));
        mockMvc.perform(post("/api/v1/admin/users").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"duplicate-role@campus.example\",\"displayName\":\"Duplicate\",\"initialPassword\":\"valid-password\",\"roles\":[\"user\",\" USER \"]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void distinguishesQueryEnumErrorsFromBodyEnumErrors() throws Exception {
        String token = tokens.accessToken(user("query-admin@campus.example", RoleCode.ADMIN));
        mockMvc.perform(get("/api/v1/admin/users?status=UNKNOWN").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUERY_PARAMETER"));
        mockMvc.perform(get("/api/v1/admin/users?role=UNKNOWN").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUERY_PARAMETER"));
        mockMvc.perform(post("/api/v1/admin/users").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"body-enum@campus.example\",\"displayName\":\"Body Enum\",\"initialPassword\":\"valid-password\",\"roles\":[\"USER\"],\"status\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mockMvc.perform(get("/api/v1/admin/users?q=not-present&sort=createdAt,desc").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void changesStatusRolesAndPasswordWithSessionAuditAndVersionEffects() throws Exception {
        UserAccount admin = user("mutation-admin@campus.example", RoleCode.ADMIN);
        UserAccount target = user("mutation-target@campus.example", RoleCode.USER);
        String token = tokens.accessToken(admin);
        Cookie targetRefresh = login(target.email());
        assertThat(targetRefresh).isNotNull();
        long initialVersion = users.findById(target.id()).orElseThrow().rowVersion();
        mockMvc.perform(patch("/api/v1/admin/users/" + target.id() + "/status").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SUSPENDED\",\"expectedVersion\":" + initialVersion + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED")).andExpect(jsonPath("$.securityVersion").value(1));
        UserAccount suspended = users.findById(target.id()).orElseThrow();
        assertThat(suspended.rowVersion()).isGreaterThan(initialVersion);
        assertThat(jdbcTemplate.queryForObject("select count(*) from identity_auth_sessions where user_id = ? and revoked_at is not null", Integer.class, target.id())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select action from identity_admin_audit_events where target_user_id = ? order by occurred_at desc limit 1", String.class, target.id())).isEqualTo("STATUS_CHANGED");
        mockMvc.perform(put("/api/v1/admin/users/" + target.id() + "/roles").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"ADMIN\",\"USER\"],\"expectedVersion\":" + suspended.rowVersion() + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("ADMIN")).andExpect(jsonPath("$.securityVersion").value(2));
        UserAccount roleChanged = users.findById(target.id()).orElseThrow();
        mockMvc.perform(post("/api/v1/admin/users/" + target.id() + "/password-reset").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"replacement-password\",\"expectedVersion\":" + roleChanged.rowVersion() + "}"))
                .andExpect(status().isNoContent());
        assertThat(users.findById(target.id()).orElseThrow().securityVersion()).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("select count(*) from identity_admin_audit_events where target_user_id = ?", Integer.class, target.id())).isEqualTo(3);
        mockMvc.perform(post("/api/v1/admin/users/" + target.id() + "/password-reset").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"another-valid-password\",\"expectedVersion\":" + roleChanged.rowVersion() + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        assertThat(jdbcTemplate.queryForObject("select count(*) from identity_admin_audit_events where target_user_id = ?", Integer.class, target.id())).isEqualTo(3);
    }

    @Test
    void treatsSameStatusAsANoOpOnlyAfterValidatingTheExpectedVersion() throws Exception {
        UserAccount admin = user("no-op-admin@campus.example", RoleCode.ADMIN);
        UserAccount target = user("no-op-target@campus.example", RoleCode.USER);
        Cookie refresh = login(target.email());
        UserAccount before = users.findById(target.id()).orElseThrow();
        String token = tokens.accessToken(admin);

        mockMvc.perform(patch("/api/v1/admin/users/" + target.id() + "/status").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACTIVE\",\"expectedVersion\":" + before.rowVersion() + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rowVersion").value(before.rowVersion())).andExpect(jsonPath("$.securityVersion").value(before.securityVersion()));

        assertThat(activeSessions(target.id())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from identity_admin_audit_events where target_user_id = ?", Integer.class, target.id())).isZero();
        UserAccount unchanged = users.findById(target.id()).orElseThrow();
        assertThat(unchanged.updatedAt()).isEqualTo(before.updatedAt());
        mockMvc.perform(patch("/api/v1/admin/users/" + target.id() + "/status").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACTIVE\",\"expectedVersion\":" + (before.rowVersion() + 1) + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", "http://localhost:3000").cookie(refresh)).andExpect(status().isOk());
    }

    @Test
    void mapsInvalidStatusTransitionsWithoutCatchingUnrelatedIllegalStateExceptions() throws Exception {
        UserAccount admin = user("transition-admin@campus.example", RoleCode.ADMIN);
        UserAccount target = user("transition-target@campus.example", RoleCode.USER);
        target.changeStatus(AccountStatus.DISABLED);
        target = users.save(target);
        mockMvc.perform(patch("/api/v1/admin/users/" + target.id() + "/status").header("Authorization", "Bearer " + tokens.accessToken(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SUSPENDED\",\"expectedVersion\":" + target.rowVersion() + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void validatesPasswordsRejectsSelfModificationAndMalformedIds() throws Exception {
        UserAccount admin = user("self@campus.example", RoleCode.ADMIN);
        String token = tokens.accessToken(admin);
        mockMvc.perform(post("/api/v1/admin/users").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"invalid@campus.example\",\"displayName\":\"Invalid\",\"initialPassword\":\"short\",\"roles\":[\"USER\"]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(patch("/api/v1/admin/users/" + admin.id() + "/status").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SUSPENDED\",\"expectedVersion\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SELF_MODIFICATION_NOT_ALLOWED"));
        mockMvc.perform(get("/api/v1/admin/users/not-a-uuid").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void preservesExpectedVersionValidationOverHttp() throws Exception {
        UserAccount admin = user("negative-version-admin@campus.example", RoleCode.ADMIN);
        UserAccount target = user("negative-version-target@campus.example", RoleCode.USER);

        mockMvc.perform(patch("/api/v1/admin/users/" + target.id() + "/status").header("Authorization", "Bearer " + tokens.accessToken(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SUSPENDED\",\"expectedVersion\":-1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void protectsUnknownRoutesAndRejectsInvalidBearerTokens() throws Exception {
        mockMvc.perform(get("/api/v1/not-registered"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("MISSING_ACCESS_TOKEN"));
        mockMvc.perform(get("/api/v1/not-registered").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
    }

    @Test
    void ordersEverySupportedSortAndUsesIdAsTheTieBreaker() throws Exception {
        String token = tokens.accessToken(user("sort-admin@campus.example", RoleCode.ADMIN));
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        UserAccount alpha = user(UUID.fromString("00000000-0000-0000-0000-000000000001"), "alpha@sort.example", "Same", AccountStatus.ACTIVE, Set.of(RoleCode.USER), base);
        UserAccount bravo = user(UUID.fromString("00000000-0000-0000-0000-000000000002"), "bravo@sort.example", "Same", AccountStatus.ACTIVE, Set.of(RoleCode.USER), base);
        UserAccount charlie = user(UUID.fromString("00000000-0000-0000-0000-000000000003"), "charlie@sort.example", "Other", AccountStatus.SUSPENDED, Set.of(RoleCode.USER), base.plusSeconds(1));
        UserAccount delta = user(UUID.fromString("00000000-0000-0000-0000-000000000004"), "delta@sort.example", "Other", AccountStatus.DISABLED, Set.of(RoleCode.USER), base.plusSeconds(2));
        List<UserAccount> expected = List.of(alpha, bravo, charlie, delta);
        setTimes(alpha.id(), base, base);
        setTimes(bravo.id(), base, base);
        setTimes(charlie.id(), base.plusSeconds(1), base.plusSeconds(1));
        setTimes(delta.id(), base.plusSeconds(2), base.plusSeconds(2));

        for (String field : List.of("email", "displayName", "status", "createdAt", "updatedAt")) {
            for (String direction : List.of("asc", "desc")) {
                List<UUID> ids = responseIds(mockMvc.perform(get("/api/v1/admin/users?q=sort.example&size=10&sort=" + field + "," + direction)
                        .header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
                Comparator<UserAccount> comparator = comparator(field).thenComparing(UserAccount::id);
                if (direction.equals("desc")) comparator = comparator(field).reversed().thenComparing(UserAccount::id);
                assertThat(ids).containsExactlyElementsOf(expected.stream().sorted(comparator).map(UserAccount::id).toList());
            }
        }
    }

    @Test
    void returnsDefaultAndAdjacentPagesAndCombinesFilters() throws Exception {
        String token = tokens.accessToken(user("page-admin@campus.example", RoleCode.ADMIN));
        for (int index = 0; index < 21; index++) {
            user("page-%02d@page.example".formatted(index), "Page %02d".formatted(index), index < 2 ? AccountStatus.SUSPENDED : AccountStatus.ACTIVE,
                    index < 2 ? Set.of(RoleCode.ADMIN, RoleCode.USER) : Set.of(RoleCode.USER));
        }
        mockMvc.perform(get("/api/v1/admin/users?q=page.example").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(21)).andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.content.length()").value(20));
        mockMvc.perform(get("/api/v1/admin/users?q=page.example&page=1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.content.length()").value(1));
        mockMvc.perform(get("/api/v1/admin/users?q=page-0&status=SUSPENDED&role=ADMIN").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content[0].status").value("SUSPENDED"))
                .andExpect(jsonPath("$.content[0].roles[0]").value("ADMIN"));
    }

    @Test
    void roleChangesRevokeSessionsWhileFailedChangesPreserveThem() throws Exception {
        UserAccount admin = user("role-admin@campus.example", RoleCode.ADMIN);
        UserAccount target = user("role-target@campus.example", RoleCode.USER);
        Cookie refresh = login(target.email());
        String token = tokens.accessToken(admin);
        UserAccount before = users.findById(target.id()).orElseThrow();
        mockMvc.perform(put("/api/v1/admin/users/" + target.id() + "/roles").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"ADMIN\"],\"expectedVersion\":999}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        assertThat(activeSessions(target.id())).isEqualTo(1);
        UserAccount unchanged = users.findById(target.id()).orElseThrow();
        assertThat(unchanged.roles()).extracting(Role::code).containsExactly(RoleCode.USER);
        assertThat(unchanged.securityVersion()).isEqualTo(before.securityVersion());
        assertThat(unchanged.rowVersion()).isEqualTo(before.rowVersion());
        assertThat(jdbcTemplate.queryForObject("select count(*) from identity_admin_audit_events where target_user_id = ?", Integer.class, target.id())).isZero();
        mockMvc.perform(put("/api/v1/admin/users/" + target.id() + "/roles").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"ADMIN\"],\"expectedVersion\":" + users.findById(target.id()).orElseThrow().rowVersion() + "}"))
                .andExpect(status().isOk());
        assertThat(activeSessions(target.id())).isZero();
        mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", "http://localhost:3000").cookie(refresh))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REVOKED"));
    }

    @Test
    void rejectsSelfRoleAndPasswordChangesWithoutChangingState() throws Exception {
        UserAccount admin = user("self-state@campus.example", RoleCode.ADMIN);
        Cookie refresh = login(admin.email());
        String hash = users.findById(admin.id()).orElseThrow().passwordHash();
        String token = tokens.accessToken(admin);
        mockMvc.perform(put("/api/v1/admin/users/" + admin.id() + "/roles").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"USER\"],\"expectedVersion\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SELF_MODIFICATION_NOT_ALLOWED"));
        mockMvc.perform(post("/api/v1/admin/users/" + admin.id() + "/password-reset").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"replacement-password\",\"expectedVersion\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SELF_MODIFICATION_NOT_ALLOWED"));
        UserAccount unchanged = users.findById(admin.id()).orElseThrow();
        assertThat(unchanged.passwordHash()).isEqualTo(hash); assertThat(unchanged.roles()).extracting(Role::code).containsExactly(RoleCode.ADMIN);
        assertThat(activeSessions(admin.id())).isEqualTo(1); assertThat(refresh.getValue()).isNotBlank();
        assertThat(jdbcTemplate.queryForObject("select count(*) from identity_admin_audit_events where target_user_id = ?", Integer.class, admin.id())).isZero();
    }

    @Test
    void createsAdminDirectlyHandlesDuplicatesAndUnknownUsersWithoutLeakingSecrets() throws Exception {
        UserAccount actor = user("create-direct-admin@campus.example", RoleCode.ADMIN);
        String token = tokens.accessToken(actor);
        String password = "direct-admin-password";
        String response = mockMvc.perform(post("/api/v1/admin/users").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"direct@campus.example\",\"displayName\":\"Direct Admin\",\"initialPassword\":\"" + password + "\",\"roles\":[\"ADMIN\"]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.roles[0]").value("ADMIN")).andReturn().getResponse().getContentAsString();
        String id = responseIds("{\"content\":[" + response + "]}").getFirst().toString();
        var audit = jdbcTemplate.queryForMap("select actor_user_id, target_user_id, action, metadata from identity_admin_audit_events where target_user_id = ?", UUID.fromString(id));
        assertThat(audit.get("actor_user_id")).isEqualTo(actor.id());
        assertThat(audit.get("target_user_id")).isEqualTo(UUID.fromString(id));
        assertThat(audit.get("action")).isEqualTo("USER_CREATED");
        assertThat((String) audit.get("metadata")).isEqualTo("{}").doesNotContain(password, "password", "hash");
        mockMvc.perform(post("/api/v1/admin/users").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"direct@campus.example\",\"displayName\":\"Duplicate\",\"initialPassword\":\"direct-admin-password\",\"roles\":[\"USER\"]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
        mockMvc.perform(get("/api/v1/admin/users/" + UUID.randomUUID()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void enforcesRequestValidationBounds() throws Exception {
        String token = tokens.accessToken(user("bounds-admin@campus.example", RoleCode.ADMIN));
        String name101 = "a".repeat(101);
        for (String request : List.of(
                "{\"email\":\"low@campus.example\",\"displayName\":\"A\",\"initialPassword\":\"12345678901\",\"roles\":[\"USER\"]}",
                "{\"email\":\"high@campus.example\",\"displayName\":\"" + name101 + "\",\"initialPassword\":\"123456789012\",\"roles\":[\"USER\"]}",
                "{\"email\":\"password-high@campus.example\",\"displayName\":\"Valid\",\"initialPassword\":\"" + "p".repeat(65) + "\",\"roles\":[\"USER\"]}")) {
            mockMvc.perform(post("/api/v1/admin/users").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(request))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        mockMvc.perform(get("/api/v1/admin/users?size=101").header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/admin/users?page=-1").header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/admin/users?q=" + "q".repeat(101)).header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/admin/users").param("q", "q".repeat(100)).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/users").param("q", "q".repeat(101)).header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUERY_PARAMETER"));
    }

    @Test
    void passwordResetRevokesOnlyTheTargetUsersSessions() throws Exception {
        UserAccount admin = user("reset-admin@campus.example", RoleCode.ADMIN);
        UserAccount target = user("reset-target@campus.example", RoleCode.USER);
        UserAccount other = user("reset-other@campus.example", RoleCode.USER);
        login(admin.email());
        Cookie targetRefresh = login(target.email());
        Cookie otherRefresh = login(other.email());
        target = users.findById(target.id()).orElseThrow();

        mockMvc.perform(post("/api/v1/admin/users/" + target.id() + "/password-reset").header("Authorization", "Bearer " + tokens.accessToken(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"replacement-password\",\"expectedVersion\":" + target.rowVersion() + "}"))
                .andExpect(status().isNoContent());

        UserAccount reset = users.findById(target.id()).orElseThrow();
        assertThat(reset.passwordHash()).isNotEqualTo(target.passwordHash());
        assertThat(reset.securityVersion()).isEqualTo(target.securityVersion() + 1);
        assertThat(reset.rowVersion()).isGreaterThan(target.rowVersion());
        assertThat(activeSessions(admin.id())).isEqualTo(1);
        assertThat(activeSessions(target.id())).isZero();
        assertThat(activeSessions(other.id())).isEqualTo(1);
        assertAudit(admin.id(), target.id(), "PASSWORD_RESET");
        mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", "http://localhost:3000").cookie(targetRefresh))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REVOKED"));
        mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", "http://localhost:3000").cookie(otherRefresh)).andExpect(status().isOk());
    }

    @Test
    void statusRoleAndPasswordMutationsRevokeEveryTargetSessionOverHttp() throws Exception {
        UserAccount admin = user("multi-session-admin@campus.example", RoleCode.ADMIN);
        String token = tokens.accessToken(admin);

        UserAccount statusTarget = user("multi-session-status@campus.example", RoleCode.USER);
        assertAllSessionsRevoked(statusTarget, login(statusTarget.email()), login(statusTarget.email()), () -> mockMvc.perform(
                patch("/api/v1/admin/users/" + statusTarget.id() + "/status").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"expectedVersion\":" + users.findById(statusTarget.id()).orElseThrow().rowVersion() + "}")));

        UserAccount rolesTarget = user("multi-session-roles@campus.example", RoleCode.USER);
        assertAllSessionsRevoked(rolesTarget, login(rolesTarget.email()), login(rolesTarget.email()), () -> mockMvc.perform(
                put("/api/v1/admin/users/" + rolesTarget.id() + "/roles").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ADMIN\"],\"expectedVersion\":" + users.findById(rolesTarget.id()).orElseThrow().rowVersion() + "}")));

        UserAccount passwordTarget = user("multi-session-password@campus.example", RoleCode.USER);
        assertAllSessionsRevoked(passwordTarget, login(passwordTarget.email()), login(passwordTarget.email()), () -> mockMvc.perform(
                post("/api/v1/admin/users/" + passwordTarget.id() + "/password-reset").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"replacement-password\",\"expectedVersion\":" + users.findById(passwordTarget.id()).orElseThrow().rowVersion() + "}")));
    }

    @Test
    void failedStatusAndPasswordResetPreserveTargetStateIndependently() throws Exception {
        UserAccount admin = user("failed-mutation-admin@campus.example", RoleCode.ADMIN);
        UserAccount target = user("failed-mutation-target@campus.example", RoleCode.USER);
        Cookie refresh = login(target.email());
        UserAccount before = users.findById(target.id()).orElseThrow();
        String token = tokens.accessToken(admin);

        mockMvc.perform(patch("/api/v1/admin/users/" + target.id() + "/status").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SUSPENDED\",\"expectedVersion\":" + (before.rowVersion() + 1) + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        assertUnchanged(target.id(), before);
        assertThat(activeSessions(target.id())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from identity_admin_audit_events where target_user_id = ?", Integer.class, target.id())).isZero();

        mockMvc.perform(post("/api/v1/admin/users/" + target.id() + "/password-reset").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"replacement-password\",\"expectedVersion\":" + (before.rowVersion() + 1) + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        assertUnchanged(target.id(), before);
        assertThat(activeSessions(target.id())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from identity_admin_audit_events where target_user_id = ?", Integer.class, target.id())).isZero();
        mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", "http://localhost:3000").cookie(refresh)).andExpect(status().isOk());
    }

    @Test
    void enforcesTheFinalAdministratorGuardForConcurrentCrossTargetRoleReplacements() throws Exception {
        UserAccount actor = user("concurrent-actor@campus.example", RoleCode.ADMIN);
        UserAccount firstAdmin = user("concurrent-first@campus.example", RoleCode.ADMIN);
        UserAccount secondAdmin = user("concurrent-second@campus.example", RoleCode.ADMIN);
        Cookie firstRefresh = login(firstAdmin.email());
        Cookie secondRefresh = login(secondAdmin.email());
        UserAccount firstAfterLogin = users.findById(firstAdmin.id()).orElseThrow();
        UserAccount secondAfterLogin = users.findById(secondAdmin.id()).orElseThrow();
        jdbcTemplate.update("delete from identity_user_roles where user_id not in (?, ?) and role_id = (select id from identity_roles where code = 'ADMIN')", firstAfterLogin.id(), secondAfterLogin.id());
        assertThat(jdbcTemplate.queryForObject("select count(*) from identity_users u join identity_user_roles ur on u.id = ur.user_id join identity_roles r on r.id = ur.role_id where u.status = 'ACTIVE' and r.code = 'ADMIN'", Integer.class)).isEqualTo(2);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokens.accessToken(actor));
        headers.setContentType(MediaType.APPLICATION_JSON);
        CyclicBarrier start = new CyclicBarrier(2);

        try (var executor = Executors.newFixedThreadPool(2)) {
            List<java.util.concurrent.Callable<ResponseEntity<String>>> requests = List.of(
                    () -> roleReplacementAfterBarrier(start, headers, firstAfterLogin.id(), firstAfterLogin.rowVersion()),
                    () -> roleReplacementAfterBarrier(start, headers, secondAfterLogin.id(), secondAfterLogin.rowVersion()));
            var responses = executor.invokeAll(requests);
            List<ResponseEntity<String>> results = responses.stream().map(response -> {
                try { return response.get(); } catch (Exception exception) { throw new AssertionError(exception); }
            }).toList();
            List<Integer> statuses = results.stream().map(response -> response.getStatusCode().value()).sorted().toList();
            assertThat(statuses).containsExactly(200, 409);
            assertThat(results).anySatisfy(response -> assertThat(response.getBody()).contains("LAST_ACTIVE_ADMIN_REQUIRED"));
        }
        assertThat(jdbcTemplate.queryForObject("select count(*) from identity_users u join identity_user_roles ur on u.id = ur.user_id join identity_roles r on r.id = ur.role_id where u.status = 'ACTIVE' and r.code = 'ADMIN'", Integer.class)).isEqualTo(1);
        UserAccount first = users.findById(firstAdmin.id()).orElseThrow();
        UserAccount second = users.findById(secondAdmin.id()).orElseThrow();
        UserAccount replaced = first.roles().stream().anyMatch(role -> role.code() == RoleCode.ADMIN) ? second : first;
        UserAccount preserved = replaced == first ? second : first;
        assertThat(replaced.roles()).extracting(Role::code).containsExactly(RoleCode.USER);
        assertThat(replaced.securityVersion()).isEqualTo(1);
        assertThat(replaced.rowVersion()).isGreaterThan(0);
        assertThat(activeSessions(replaced.id())).isZero();
        assertAudit(actor.id(), replaced.id(), "ROLES_REPLACED");
        UserAccount preservedBefore = preserved.id().equals(firstAfterLogin.id()) ? firstAfterLogin : secondAfterLogin;
        assertThat(preserved.roles()).extracting(Role::code).containsExactly(RoleCode.ADMIN);
        assertThat(preserved.securityVersion()).isEqualTo(preservedBefore.securityVersion());
        assertThat(preserved.rowVersion()).isEqualTo(preservedBefore.rowVersion());
        assertThat(preserved.updatedAt()).isEqualTo(preservedBefore.updatedAt());
        assertThat(activeSessions(preserved.id())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from identity_admin_audit_events where target_user_id = ?", Integer.class, preserved.id())).isZero();
        Cookie revokedRefresh = replaced.id().equals(firstAdmin.id()) ? firstRefresh : secondRefresh;
        Cookie preservedRefresh = preserved.id().equals(firstAdmin.id()) ? firstRefresh : secondRefresh;
        mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", "http://localhost:3000").cookie(revokedRefresh))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REVOKED"));
        mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", "http://localhost:3000").cookie(preservedRefresh)).andExpect(status().isOk());
    }

    private ResponseEntity<String> roleReplacementAfterBarrier(CyclicBarrier start, HttpHeaders headers, UUID userId, long version) throws Exception {
        start.await();
        return restTemplate.exchange("http://localhost:" + port + "/api/v1/admin/users/" + userId + "/roles", org.springframework.http.HttpMethod.PUT,
                new HttpEntity<>("{\"roles\":[\"USER\"],\"expectedVersion\":" + version + "}", headers), String.class);
    }

    private void assertAudit(UUID actorId, UUID targetId, String action) {
        var audit = jdbcTemplate.queryForMap("select actor_user_id, target_user_id, action, metadata from identity_admin_audit_events where target_user_id = ?", targetId);
        assertThat(audit.get("actor_user_id")).isEqualTo(actorId);
        assertThat(audit.get("target_user_id")).isEqualTo(targetId);
        assertThat(audit.get("action")).isEqualTo(action);
        assertThat((String) audit.get("metadata")).isEqualTo("{}").doesNotContain("password", "hash");
    }

    private void assertUnchanged(UUID userId, UserAccount expected) {
        UserAccount actual = users.findById(userId).orElseThrow();
        assertThat(actual.status()).isEqualTo(expected.status());
        assertThat(actual.roles()).isEqualTo(expected.roles());
        assertThat(actual.passwordHash()).isEqualTo(expected.passwordHash());
        assertThat(actual.securityVersion()).isEqualTo(expected.securityVersion());
        assertThat(actual.rowVersion()).isEqualTo(expected.rowVersion());
        assertThat(actual.updatedAt()).isEqualTo(expected.updatedAt());
    }

    private void assertAllSessionsRevoked(UserAccount target, Cookie first, Cookie second, ThrowingRequest mutation) throws Exception {
        assertThat(activeSessions(target.id())).isEqualTo(2);
        mutation.perform().andExpect(status().is2xxSuccessful());
        assertThat(activeSessions(target.id())).isZero();
        for (Cookie refresh : List.of(first, second)) {
            mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", "http://localhost:3000").cookie(refresh))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REVOKED"));
        }
    }

    private UserAccount user(String email, RoleCode code) {
        return user(UUID.randomUUID(), email, email, AccountStatus.ACTIVE, Set.of(code), Instant.now());
    }

    private UserAccount user(String email, String displayName, AccountStatus status, Set<RoleCode> codes) { return user(UUID.randomUUID(), email, displayName, status, codes, Instant.now()); }
    private UserAccount user(UUID id, String email, String displayName, AccountStatus status, Set<RoleCode> codes, Instant createdAt) {
        Set<Role> assigned = codes.stream().map(code -> roles.findByCode(code).orElseThrow()).collect(java.util.stream.Collectors.toSet());
        return users.save(UserAccount.create(id, email, displayName, passwords.encode("valid-password"), status, assigned, createdAt));
    }
    private void setTimes(UUID id, Instant createdAt, Instant updatedAt) { jdbcTemplate.update("update identity_users set created_at = ?, updated_at = ? where id = ?", java.sql.Timestamp.from(createdAt), java.sql.Timestamp.from(updatedAt), id); }
    private long activeSessions(UUID userId) { return jdbcTemplate.queryForObject("select count(*) from identity_auth_sessions where user_id = ? and revoked_at is null", Long.class, userId); }
    private List<UUID> responseIds(String response) { return java.util.regex.Pattern.compile("\\\"id\\\":\\\"([^\\\"]+)").matcher(response).results().map(match -> UUID.fromString(match.group(1))).toList(); }
    private Comparator<UserAccount> comparator(String field) {
        return switch (field) {
            case "email" -> Comparator.comparing(UserAccount::email);
            case "displayName" -> Comparator.comparing(UserAccount::displayName);
            case "status" -> Comparator.comparing(account -> account.status().name());
            case "createdAt", "updatedAt" -> Comparator.comparing(this::sortTime);
            default -> throw new IllegalArgumentException(field);
        };
    }
    private Instant sortTime(UserAccount account) {
        return switch (account.id().toString()) {
            case "00000000-0000-0000-0000-000000000001", "00000000-0000-0000-0000-000000000002" -> Instant.parse("2026-01-01T00:00:00Z");
            case "00000000-0000-0000-0000-000000000003" -> Instant.parse("2026-01-01T00:00:01Z");
            case "00000000-0000-0000-0000-000000000004" -> Instant.parse("2026-01-01T00:00:02Z");
            default -> throw new IllegalArgumentException(account.id().toString());
        };
    }

    private Cookie login(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\",\"password\":\"valid-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("CAMPUS_REFRESH");
    }

    @FunctionalInterface
    private interface ThrowingRequest { org.springframework.test.web.servlet.ResultActions perform() throws Exception; }
}
