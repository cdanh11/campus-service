package com.campus.identity.api;

import java.time.Instant;
import java.util.List;
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
    private static final List<String> OWNER_PATHS = List.of(
            "organization-units", "students", "faculty-staff", "academic/programs",
            "dormitory/buildings", "notifications/templates", "events", "library/titles",
            "finance/fees", "audits/IDENTITY", "reports/dashboard");
    // Independent expected contract, not imported from the production access policy.
    private static final Map<RoleCode, Set<String>> WRITABLE = Map.of(
            RoleCode.ACADEMIC_AFFAIRS, Set.of("organization-units", "students", "faculty-staff", "academic/programs"),
            RoleCode.CAMPUS_SERVICES, Set.of("dormitory/buildings", "notifications/templates", "events", "library/titles"),
            RoleCode.FINANCE, Set.of("finance/fees"),
            RoleCode.AUDITOR, Set.of());
    private static final Map<RoleCode, Set<String>> READABLE = Map.of(
            RoleCode.ACADEMIC_AFFAIRS, Set.of("organization-units", "students", "faculty-staff", "academic/programs", "users"),
            RoleCode.CAMPUS_SERVICES, Set.of("dormitory/buildings", "notifications/templates", "events", "library/titles", "students", "users"),
            RoleCode.FINANCE, Set.of("finance/fees", "students"),
            RoleCode.AUDITOR, Set.of("audits/IDENTITY", "reports/dashboard", "students", "academic/sections",
                    "dormitory/beds", "library/copies", "events"),
            RoleCode.USER, Set.of());
    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired RoleRepository roles;
    @Autowired TokenService tokens;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;

    @ParameterizedTest @EnumSource(RoleCode.class)
    void everyRoleHasOnlyItsOwnMutationAndApprovedReads(RoleCode role) throws Exception {
        String token = token(role);
        for (String owner : OWNER_PATHS) {
            boolean read = role == RoleCode.ADMIN || READABLE.getOrDefault(role, Set.of()).contains(owner);
            mvc.perform(get(BASE + owner).header("Authorization", "Bearer " + token))
                    .andExpect(status().is(read ? 200 : 403));
            if (!owner.startsWith("audits/") && !owner.startsWith("reports/")) {
                boolean write = role == RoleCode.ADMIN || WRITABLE.getOrDefault(role, Set.of()).contains(owner);
                // Malformed input exercises the real filter before controller validation, with no fixture mutation.
                mvc.perform(post(BASE + owner).header("Authorization", "Bearer " + token)
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

    @Test void auditorReadsOversightAndApprovedReferencesWithoutAnyMutation() throws Exception {
        String auditor = token(RoleCode.AUDITOR);
        for (String reference : new String[] {"academic/sections", "dormitory/beds", "library/copies", "students", "events"}) {
            mvc.perform(get(BASE + reference).header("Authorization", "Bearer " + auditor)).andExpect(status().isOk());
            mvc.perform(head(BASE + reference).header("Authorization", "Bearer " + auditor)).andExpect(status().isOk());
            mvc.perform(post(BASE + reference).header("Authorization", "Bearer " + auditor)
                    .contentType(MediaType.APPLICATION_JSON).content("{")).andExpect(status().isForbidden());
        }
        for (String lookup : new String[] {"academic/sections/lookup", "dormitory/lookup/beds", "library/copies/lookup", "students/lookup", "events/lookup"}) {
            mvc.perform(get(BASE + lookup).header("Authorization", "Bearer " + auditor)).andExpect(status().isBadRequest());
            mvc.perform(post(BASE + lookup).header("Authorization", "Bearer " + auditor)
                    .contentType(MediaType.APPLICATION_JSON).content("{")).andExpect(status().isForbidden());
        }
        mvc.perform(get(BASE + "reports/dashboard").header("Authorization", "Bearer " + auditor)).andExpect(status().isOk());
        mvc.perform(get(BASE + "audits/IDENTITY").header("Authorization", "Bearer " + auditor)).andExpect(status().isOk());
        for (String forbidden : new String[] {"library/loans", "event-registrations", "users", "users/lookup",
                "notifications/notices", "finance/fees", "dormitory/buildings"}) {
            mvc.perform(get(BASE + forbidden).header("Authorization", "Bearer " + auditor)).andExpect(status().isForbidden());
        }
        String finance = token(RoleCode.FINANCE);
        mvc.perform(get(BASE + "reports/dashboard").header("Authorization", "Bearer " + finance)).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "audits/IDENTITY").header("Authorization", "Bearer " + finance)).andExpect(status().isForbidden());
    }

    @Test void departmentRolesReachAccountReferencesWithoutIdentityMutation() throws Exception {
        for (RoleCode role : new RoleCode[] {RoleCode.ACADEMIC_AFFAIRS, RoleCode.CAMPUS_SERVICES}) {
            String token = token(role);
            mvc.perform(get(BASE + "users").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
            mvc.perform(get(BASE + "users/" + UUID.randomUUID()).header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
            mvc.perform(get(BASE + "users/lookup").header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
            mvc.perform(get(BASE + "users/future-reference").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
            mvc.perform(patch(BASE + "users/" + UUID.randomUUID() + "/status").header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content("{")).andExpect(status().isForbidden());
            mvc.perform(post(BASE + "users/" + UUID.randomUUID() + "/password-reset").header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content("{")).andExpect(status().isForbidden());
        }
        for (String owner : OWNER_PATHS) mvc.perform(get(BASE + owner)).andExpect(status().isUnauthorized());
    }

    @Test void multipleDepartmentRolesCombineAccessWithoutBecomingGlobalAdministrator() throws Exception {
        String token = token(RoleCode.ACADEMIC_AFFAIRS, RoleCode.FINANCE);
        mvc.perform(get(BASE + "students").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get(BASE + "finance/fees").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(post(BASE + "students").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("{")).andExpect(status().isBadRequest());
        mvc.perform(post(BASE + "finance/fees").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("{")).andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "reports/dashboard").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "notifications/templates").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "users").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{")).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(value = RoleCode.class, names = {"USER", "ADMIN"}, mode = EnumSource.Mode.EXCLUDE)
    void globalAdminCanProvisionEveryDepartmentRoleAndReplacementRevokesRefreshButNotIssuedJwt(RoleCode role) throws Exception {
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
        String owner = BASE + sampleOwner(role);
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

    private String sampleOwner(RoleCode role) {
        return switch (role) {
            case ACADEMIC_AFFAIRS -> "academic/programs";
            case CAMPUS_SERVICES -> "events";
            case FINANCE -> "finance/fees";
            case AUDITOR -> "reports/dashboard";
            default -> throw new IllegalArgumentException("No sample owner for " + role);
        };
    }

    private String token(RoleCode... codes) {
        Set<Role> assigned = java.util.Arrays.stream(codes).map(code -> roles.findByCode(code).orElseThrow()).collect(java.util.stream.Collectors.toSet());
        UUID id = UUID.randomUUID();
        return tokens.accessToken(users.save(UserAccount.create(id, id + "@example.test", "Permission fixture", HASH,
                AccountStatus.ACTIVE, assigned, Instant.now())));
    }
}
