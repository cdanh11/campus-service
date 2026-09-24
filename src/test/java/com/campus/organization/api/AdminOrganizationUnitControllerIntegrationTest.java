package com.campus.organization.api;

import java.time.Instant;
import java.util.Set;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AdminOrganizationUnitControllerIntegrationTest {
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6-alpine");
    @Autowired MockMvc mockMvc;
    @Autowired UserAccountRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @Autowired TokenService tokens;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void restrictsOrganizationUnitAdministrationAndCreatesListsAndUpdatesAUnit() throws Exception {
        mockMvc.perform(get("/api/v1/admin/organization-units")).andExpect(status().isUnauthorized());
        String student = tokens.accessToken(user("organization-student@campus.example", RoleCode.USER));
        mockMvc.perform(get("/api/v1/admin/organization-units").header("Authorization", "Bearer " + student)).andExpect(status().isForbidden());
        String admin = tokens.accessToken(user("organization-admin@campus.example", RoleCode.ADMIN));
        String created = mockMvc.perform(post("/api/v1/admin/organization-units").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\" ENG \" ,\"name\":\" Engineering \" ,\"unitType\":\"FACULTY\"}"))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.code").value("ENG")).andExpect(jsonPath("$.name").value("Engineering"))
                .andExpect(jsonPath("$.status").value("ACTIVE")).andReturn().getResponse().getContentAsString();
        String id = created.replaceFirst(".*\\\"id\\\":\\\"([^\\\"]+).*", "$1");
        mockMvc.perform(get("/api/v1/admin/organization-units").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == '" + id + "')].code").value("ENG"));
        mockMvc.perform(put("/api/v1/admin/organization-units/" + id).header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ENG\",\"name\":\"Engineering and Technology\",\"unitType\":\"FACULTY\",\"status\":\"INACTIVE\",\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE")).andExpect(jsonPath("$.rowVersion").value(1));
    }

    @Test
    void rejectsInvalidDuplicateMissingAndStaleOrganizationUnitRequests() throws Exception {
        String admin = tokens.accessToken(user("organization-errors-admin@campus.example", RoleCode.ADMIN));
        String invalid = "{\"code\":\"\\t\\n\",\"name\":\"Name\",\"unitType\":\"DEPARTMENT\"}";
        mockMvc.perform(post("/api/v1/admin/organization-units").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        String body = mockMvc.perform(post("/api/v1/admin/organization-units").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SCI\",\"name\":\"Science\",\"unitType\":\"DEPARTMENT\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = body.replaceFirst(".*\\\"id\\\":\\\"([^\\\"]+).*", "$1");
        mockMvc.perform(post("/api/v1/admin/organization-units").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SCI\",\"name\":\"Different\",\"unitType\":\"DEPARTMENT\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORGANIZATION_UNIT_CODE_ALREADY_EXISTS"));
        mockMvc.perform(put("/api/v1/admin/organization-units/" + id).header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SCI\",\"name\":\"Science\",\"unitType\":\"DEPARTMENT\",\"status\":\"ACTIVE\",\"expectedVersion\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        mockMvc.perform(get("/api/v1/admin/organization-units/not-a-uuid").header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void migrationDefinesOrganizationUnitDatabaseConstraintsAndDefaults() {
        java.util.UUID id = java.util.UUID.randomUUID();
        jdbcTemplate.update("insert into organization_units (id, code, name, unit_type) values (?, ?, ?, ?)", id, "LIB", "Library", "ADMINISTRATIVE");
        assertThat(jdbcTemplate.queryForObject("select row_version from organization_units where id = ?", Long.class, id)).isZero();
        assertThat(jdbcTemplate.queryForObject("select status from organization_units where id = ?", String.class, id)).isEqualTo("ACTIVE");
        assertThatThrownBy(() -> jdbcTemplate.update("insert into organization_units (id, code, name, unit_type) values (?, ?, ?, ?)", java.util.UUID.randomUUID(), "\t\n", "Invalid", "FACULTY"))
                .hasMessageContaining("ck_organization_units_code_not_blank");
        assertThatThrownBy(() -> jdbcTemplate.update("insert into organization_units (id, code, name, unit_type) values (?, ?, ?, ?)", java.util.UUID.randomUUID(), "BAD", "Invalid", "SCHOOL"))
                .hasMessageContaining("ck_organization_units_unit_type");
    }

    private UserAccount user(String email, RoleCode roleCode) {
        Set<Role> assigned = Set.of(roles.findByCode(roleCode).orElseThrow());
        return users.save(UserAccount.create(java.util.UUID.randomUUID(), email, email, passwords.encode("valid-password"), AccountStatus.ACTIVE, assigned, Instant.now()));
    }
}
