package com.campus.identity.api;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.campus.identity.domain.*;
import com.campus.identity.infrastructure.security.TokenService;
import com.campus.testsupport.PostgresApplicationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@PostgresApplicationTest
class FunctionalAdministrationAuthorizationIntegrationTest {
    private static final String BASE = "/api/v1/admin/";
    private static final String HASH = "{bcrypt}$2b$12$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZabcde";
    // Independent expected contract, not imported from the production access policy.
    private static final Map<RoleCode, String> OWNERS = Map.ofEntries(
            Map.entry(RoleCode.ORGANIZATION_ADMIN, "organization-units"),
            Map.entry(RoleCode.STUDENT_ADMIN, "students"),
            Map.entry(RoleCode.PERSONNEL_ADMIN, "faculty-staff"),
            Map.entry(RoleCode.ACADEMIC_ADMIN, "academic/programs"),
            Map.entry(RoleCode.DORMITORY_ADMIN, "dormitory/buildings"),
            Map.entry(RoleCode.FINANCE_ADMIN, "finance/fees"),
            Map.entry(RoleCode.NOTIFICATION_ADMIN, "notifications/templates"),
            Map.entry(RoleCode.EVENT_ADMIN, "events"),
            Map.entry(RoleCode.LIBRARY_ADMIN, "library/titles"),
            Map.entry(RoleCode.AUDIT_VIEWER, "audits/IDENTITY"),
            Map.entry(RoleCode.REPORTING_VIEWER, "reports/dashboard"));
    private static final Map<RoleCode, Set<String>> REFERENCES = Map.ofEntries(
            Map.entry(RoleCode.STUDENT_ADMIN, Set.of("organization-units")),
            Map.entry(RoleCode.PERSONNEL_ADMIN, Set.of("organization-units")),
            Map.entry(RoleCode.ACADEMIC_ADMIN, Set.of("organization-units", "students", "faculty-staff")),
            Map.entry(RoleCode.DORMITORY_ADMIN, Set.of("students")),
            Map.entry(RoleCode.FINANCE_ADMIN, Set.of("students")),
            Map.entry(RoleCode.EVENT_ADMIN, Set.of("students")),
            Map.entry(RoleCode.LIBRARY_ADMIN, Set.of("students")),
            Map.entry(RoleCode.REPORTING_VIEWER, Set.of("students", "events")));
    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired RoleRepository roles;
    @Autowired TokenService tokens;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;

    @ParameterizedTest @EnumSource(RoleCode.class)
    void everyRoleHasOnlyItsOwnMutationAndApprovedReads(RoleCode role) throws Exception {
        String token = token(role);
        for (var owner : OWNERS.entrySet()) {
            boolean read = role == RoleCode.ADMIN || owner.getKey() == role
                    || REFERENCES.getOrDefault(role, Set.of()).contains(owner.getValue());
            mvc.perform(get(BASE + owner.getValue()).header("Authorization", "Bearer " + token))
                    .andExpect(status().is(read ? 200 : 403));
            if (owner.getKey() != RoleCode.AUDIT_VIEWER && owner.getKey() != RoleCode.REPORTING_VIEWER) {
                boolean write = role == RoleCode.ADMIN || owner.getKey() == role;
                // Malformed input exercises the real filter before controller validation, with no fixture mutation.
                mvc.perform(post(BASE + owner.getValue()).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                        .andExpect(status().is(write ? 400 : 403));
            }
        }
        mvc.perform(post(BASE + "users").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().is(role == RoleCode.ADMIN ? 400 : 403));
        mvc.perform(put(BASE + "users/" + UUID.randomUUID() + "/roles").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().is(role == RoleCode.ADMIN ? 400 : 403));
        mvc.perform(get(BASE + "future-owner").header("Authorization", "Bearer " + token))
                .andExpect(status().is(role == RoleCode.ADMIN ? 404 : 403));
    }

    @Test void viewersHaveMinimalReferenceReadsAndCannotMutateReferencesOrEachOthersSources() throws Exception {
        String report = token(RoleCode.REPORTING_VIEWER);
        for (String reference : new String[] {"academic/sections", "dormitory/beds", "library/copies", "students", "events"}) {
            mvc.perform(get(BASE + reference).header("Authorization", "Bearer " + report)).andExpect(status().isOk());
            mvc.perform(head(BASE + reference).header("Authorization", "Bearer " + report)).andExpect(status().isOk());
            mvc.perform(post(BASE + reference).header("Authorization", "Bearer " + report)
                    .contentType(MediaType.APPLICATION_JSON).content("{")).andExpect(status().isForbidden());
        }
        mvc.perform(get(BASE + "library/loans").header("Authorization", "Bearer " + report)).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "event-registrations").header("Authorization", "Bearer " + report)).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "audits/IDENTITY").header("Authorization", "Bearer " + report)).andExpect(status().isForbidden());
        String audit = token(RoleCode.AUDIT_VIEWER);
        mvc.perform(get(BASE + "users").header("Authorization", "Bearer " + audit)).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "reports/dashboard").header("Authorization", "Bearer " + audit)).andExpect(status().isForbidden());
    }

    @Test void accountReferenceReadsNeverAuthorizeIdentityMutationOrAnonymousAccess() throws Exception {
        for (RoleCode role : new RoleCode[] {RoleCode.STUDENT_ADMIN, RoleCode.PERSONNEL_ADMIN, RoleCode.NOTIFICATION_ADMIN}) {
            String token = token(role);
            mvc.perform(get(BASE + "users").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
            mvc.perform(get(BASE + "users/" + UUID.randomUUID()).header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
            mvc.perform(get(BASE + "users/future-reference").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
            mvc.perform(patch(BASE + "users/" + UUID.randomUUID() + "/status").header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content("{")).andExpect(status().isForbidden());
            mvc.perform(post(BASE + "users/" + UUID.randomUUID() + "/password-reset").header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content("{")).andExpect(status().isForbidden());
        }
        for (String owner : OWNERS.values()) mvc.perform(get(BASE + owner)).andExpect(status().isUnauthorized());
    }

    @Test void multipleFunctionalRolesCombineAccessWithoutBecomingGlobalAdministrator() throws Exception {
        String token = token(RoleCode.EVENT_ADMIN, RoleCode.LIBRARY_ADMIN);
        mvc.perform(get(BASE + "events").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get(BASE + "library/titles").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(post(BASE + "events").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("{")).andExpect(status().isBadRequest());
        mvc.perform(post(BASE + "library/titles").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("{")).andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "reports/dashboard").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(value = RoleCode.class, names = {"USER", "ADMIN"}, mode = EnumSource.Mode.EXCLUDE)
    void globalAdminCanProvisionEveryScopedRoleAndReplacementRevokesRefreshButNotIssuedJwt(RoleCode role) throws Exception {
        String administrator = token(RoleCode.ADMIN);
        String email = UUID.randomUUID() + "@example.test";
        String password = "functional-test-password";
        var created = mvc.perform(post(BASE + "users").header("Authorization", "Bearer " + administrator)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                                "email", email, "displayName", "Functional operator", "initialPassword", password, "roles", java.util.List.of(role.name())))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.roles[0]").value(role.name()))
                .andReturn().getResponse();
        String id = mapper.readTree(created.getContentAsString()).path("id").asText();
        var login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.roles[0]").value(role.name()))
                .andReturn().getResponse();
        String access = mapper.readTree(login.getContentAsString()).path("accessToken").asText();
        String owner = BASE + OWNERS.get(role);
        mvc.perform(get(owner).header("Authorization", "Bearer " + access)).andExpect(status().isOk());
        var current = mvc.perform(get(BASE + "users/" + id).header("Authorization", "Bearer " + administrator))
                .andExpect(status().isOk()).andReturn().getResponse();
        long version = mapper.readTree(current.getContentAsString()).path("rowVersion").asLong();
        mvc.perform(put(BASE + "users/" + id + "/roles").header("Authorization", "Bearer " + administrator)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("roles", java.util.List.of("USER"), "expectedVersion", version))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("USER"))
                .andExpect(jsonPath("$.securityVersion").value(1));
        mvc.perform(put(BASE + "users/" + id + "/roles").header("Authorization", "Bearer " + administrator)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("roles", java.util.List.of(role.name()), "expectedVersion", version))))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/auth/refresh").header("Origin", "http://localhost:3000")
                        .cookie(login.getCookie("CAMPUS_REFRESH"))).andExpect(status().isUnauthorized());
        // The accepted stateless strategy deliberately preserves issued JWT grants until expiry.
        mvc.perform(get(owner).header("Authorization", "Bearer " + access)).andExpect(status().isOk());
        var newLogin = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.roles[0]").value("USER")).andReturn().getResponse();
        String newAccess = mapper.readTree(newLogin.getContentAsString()).path("accessToken").asText();
        mvc.perform(get(owner).header("Authorization", "Bearer " + newAccess)).andExpect(status().isForbidden());
    }

    private String token(RoleCode... codes) {
        Set<Role> assigned = java.util.Arrays.stream(codes).map(code -> roles.findByCode(code).orElseThrow()).collect(java.util.stream.Collectors.toSet());
        UUID id = UUID.randomUUID();
        return tokens.accessToken(users.save(UserAccount.create(id, id + "@example.test", "Permission fixture", HASH,
                AccountStatus.ACTIVE, assigned, Instant.now())));
    }
}
