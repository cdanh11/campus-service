package com.campus.finance.api;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.campus.finance.application.FinanceObligationService;
import com.campus.identity.domain.AccountStatus;
import com.campus.identity.domain.RoleCode;
import com.campus.identity.domain.RoleRepository;
import com.campus.identity.domain.UserAccount;
import com.campus.identity.domain.UserAccountRepository;
import com.campus.identity.infrastructure.security.TokenService;
import com.campus.testsupport.PostgresApplicationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@PostgresApplicationTest
class UnexpectedErrorIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @Autowired TokenService tokens;
    @MockitoBean FinanceObligationService finance;

    @Test
    void answersUnexpectedFailuresAsJsonWithTheCorrelationId() throws Exception {
        UserAccount admin = admin();
        when(finance.fee(any())).thenThrow(new IllegalStateException("simulated failure"));
        var response = mvc.perform(get("/api/v1/admin/finance/fees/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokens.accessToken(admin)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn().getResponse();
        assertThat(response.getHeader("X-Request-ID")).isNotBlank();
        assertThat(response.getContentAsString()).contains(response.getHeader("X-Request-ID"));
    }

    @Test
    void answersUnsupportedMethodsAsMethodNotAllowed() throws Exception {
        mvc.perform(delete("/api/v1/admin/finance/payments")
                        .header("Authorization", "Bearer " + tokens.accessToken(admin())))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void answersUnsupportedMediaTypesAsUnsupportedMediaType() throws Exception {
        mvc.perform(post("/api/v1/admin/finance/payments")
                        .contentType(TEXT_PLAIN).content("not json")
                        .header("Authorization", "Bearer " + tokens.accessToken(admin())))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.status").value(415));
    }

    private UserAccount admin() {
        return users.save(UserAccount.create(UUID.randomUUID(), "unexpected-" + UUID.randomUUID() + "@campus.example",
                "Unexpected", passwords.encode("test-password"), AccountStatus.ACTIVE,
                Set.of(roles.findByCode(RoleCode.ADMIN).orElseThrow()), Instant.now()));
    }
}
