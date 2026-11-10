package com.campus.student.api;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @PostgresApplicationTest
class OwnStudentProfileIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @Autowired TokenService tokens;
    @Autowired OrganizationUnitManagementService organizations;

    @Test
    void studentMaintainsContactFieldsWithOptimisticLocking() throws Exception {
        UserAccount account = account("profile-student@campus.example");
        String studentToken = tokens.accessToken(account);
        String admin = tokens.accessToken(account("profile-admin@campus.example", RoleCode.ADMIN));
        UUID unit = organizations.create("PRF", "Profile", OrganizationUnitType.FACULTY, OrganizationUnitStatus.ACTIVE).id();
        mvc.perform(post("/api/v1/admin/students").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentNumber\":\"P001\",\"fullName\":\"Profile Student\",\"email\":\"OLD@campus.example\",\"identityUserId\":\""
                        + account.id() + "\",\"organizationUnitId\":\"" + unit + "\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/me/student").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentNumber").value("P001"))
                .andExpect(jsonPath("$.email").value("old@campus.example"))
                .andExpect(jsonPath("$.profileVersion").value(0));

        mvc.perform(put("/api/v1/me/student").header("Authorization", "Bearer " + studentToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\" NEW@campus.example \",\"phone\":\" 0901234567 \",\"address\":\" 12 Nguyễn Trãi \",\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@campus.example"))
                .andExpect(jsonPath("$.phone").value("0901234567"))
                .andExpect(jsonPath("$.address").value("12 Nguyễn Trãi"))
                .andExpect(jsonPath("$.profileVersion").value(0));

        mvc.perform(put("/api/v1/me/student").header("Authorization", "Bearer " + studentToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"new@campus.example\",\"phone\":\"0907654321\",\"address\":\"12 Nguyễn Trãi\",\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("0907654321"))
                .andExpect(jsonPath("$.profileVersion").value(1));

        mvc.perform(put("/api/v1/me/student").header("Authorization", "Bearer " + studentToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"0907654321\",\"expectedVersion\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROFILE_CONCURRENT_MODIFICATION"));

        mvc.perform(put("/api/v1/me/student").header("Authorization", "Bearer " + studentToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"123\",\"expectedVersion\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STUDENT_CONTACT_INVALID"));

        mvc.perform(get("/api/v1/me/student").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("0907654321"))
                .andExpect(jsonPath("$.profileVersion").value(1));
    }

    private UserAccount account(String email) {
        return account(email, RoleCode.USER);
    }

    private UserAccount account(String email, RoleCode code) {
        return users.save(UserAccount.create(UUID.randomUUID(), email, email, passwords.encode("valid-password"), AccountStatus.ACTIVE,
                Set.of(roles.findByCode(code).orElseThrow()), Instant.now()));
    }
}
