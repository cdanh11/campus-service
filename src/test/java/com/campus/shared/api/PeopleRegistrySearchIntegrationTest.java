package com.campus.shared.api;

import com.campus.testsupport.PostgresApplicationTest;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.campus.identity.domain.AccountStatus;
import com.campus.identity.domain.RoleCode;
import com.campus.identity.domain.RoleRepository;
import com.campus.identity.domain.UserAccount;
import com.campus.identity.domain.UserAccountRepository;
import com.campus.identity.infrastructure.security.TokenService;
import com.campus.organization.application.OrganizationUnitManagementService;
import com.campus.organization.domain.OrganizationUnitStatus;
import com.campus.organization.domain.OrganizationUnitType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@PostgresApplicationTest
class PeopleRegistrySearchIntegrationTest {


    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @Autowired TokenService tokens;
    @Autowired OrganizationUnitManagementService organizations;

    @ParameterizedTest @ValueSource(strings = {"organization-units", "students", "faculty-staff"})
    void rejectsPaginationOffsetsThatCannotBeRepresentedByJpa(String route) throws Exception {
        String token = tokens.accessToken(admin());
        mvc.perform(get("/api/v1/admin/" + route).param("page", "2147483647").param("size", "2").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUERY_PARAMETER"));
        mvc.perform(get("/api/v1/admin/" + route).param("page", "2147483647").param("size", "1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void searchesPaginatesAndSortsStudentAndFacultyStaffRegistries() throws Exception {
        String token = tokens.accessToken(admin());
        UUID unitId = organizations.create("QRY", "Query Unit", OrganizationUnitType.FACULTY, OrganizationUnitStatus.ACTIVE).id();
        create(token, "/api/v1/admin/students", "{\"studentNumber\":\"S-Q-2\",\"fullName\":\"Beta Student\",\"organizationUnitId\":\"" + unitId + "\"}");
        create(token, "/api/v1/admin/students", "{\"studentNumber\":\"S-Q-1\",\"fullName\":\"Alpha Student\",\"organizationUnitId\":\"" + unitId + "\"}");
        create(token, "/api/v1/admin/faculty-staff", "{\"personnelNumber\":\"P-Q-2\",\"fullName\":\"Beta Staff\",\"personnelType\":\"STAFF\",\"organizationUnitId\":\"" + unitId + "\"}");
        create(token, "/api/v1/admin/faculty-staff", "{\"personnelNumber\":\"P-Q-1\",\"fullName\":\"Alpha Faculty\",\"personnelType\":\"FACULTY\",\"organizationUnitId\":\"" + unitId + "\"}");

        mvc.perform(get("/api/v1/admin/students?page=0&size=1&q=student&sort=studentNumber,asc").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].studentNumber").value("S-Q-1"));
        mvc.perform(get("/api/v1/admin/faculty-staff?page=0&size=1&q=staff&sort=personnelNumber,desc").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].personnelNumber").value("P-Q-2"));
        mvc.perform(get("/api/v1/admin/faculty-staff?personnelType=FACULTY&sort=personnelNumber,asc").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].personnelNumber").value("P-Q-1"));
    }

    private void create(String token, String path, String body) throws Exception {
        mvc.perform(post(path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
    }

    private UserAccount admin() {
        return users.save(UserAccount.create(UUID.randomUUID(), UUID.randomUUID() + "@campus.example", "Search Admin", passwords.encode("valid-password"), AccountStatus.ACTIVE,
                Set.of(roles.findByCode(RoleCode.ADMIN).orElseThrow()), Instant.now()));
    }
}
