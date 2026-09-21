package com.campus.identity.api;

import java.time.Instant;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import com.campus.identity.domain.AccountStatus;
import com.campus.identity.domain.RefreshToken;
import com.campus.identity.domain.RefreshTokenRepository;
import com.campus.identity.domain.UserAccount;
import com.campus.identity.domain.UserAccountRepository;
import com.campus.identity.infrastructure.security.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.persistence.EntityManager;
import com.campus.identity.infrastructure.persistence.entity.RefreshTokenEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AuthControllerIntegrationTest {
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6-alpine");
    @Autowired MockMvc mockMvc;
    @Autowired UserAccountRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired TokenService tokens;
    @Autowired EntityManager entityManager;

    @Test
    void authenticatesActiveUserAndProtectsCurrentUser() throws Exception {
        UserAccount user = users.save(UserAccount.create(UUID.randomUUID(), "login@campus.example", passwordEncoder.encode("test-password"), Instant.now()));
        String body = "{\"email\":\"login@campus.example\",\"password\":\"test-password\"}";
        String response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(cookie().httpOnly("CAMPUS_REFRESH", true))
                .andExpect(jsonPath("$.accessToken").isNotEmpty()).andExpect(jsonPath("$.user.passwordHash").doesNotExist()).andReturn().getResponse().getContentAsString();
        String token = response.replaceAll(".*\\\"accessToken\\\":\\\"([^\\\"]+).*", "$1");
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(user.id().toString())).andExpect(jsonPath("$.passwordHash").doesNotExist());
        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("MISSING_ACCESS_TOKEN"))
                .andExpect(jsonPath("$.message").value("Access token is required")).andExpect(jsonPath("$.path").value("/api/v1/auth/me"));
    }

    @Test
    void returnsIndistinguishableFailureForInvalidCredentialsAndInactiveAccount() throws Exception {
        UserAccount inactive = UserAccount.create(UUID.randomUUID(), "inactive@campus.example", passwordEncoder.encode("test-password"), Instant.now());
        inactive.changeStatus(AccountStatus.DISABLED);
        users.save(inactive);
        String unknown = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"missing@campus.example\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED")).andReturn().getResponse().getContentAsString();
        String disabled = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"inactive@campus.example\",\"password\":\"test-password\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED")).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(disabled).contains("Authentication failed");
        org.assertj.core.api.Assertions.assertThat(unknown).contains("Authentication failed");
    }

    @Test
    void rejectsMalformedRequestsAndInvalidAccessTokensWithJsonErrors() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
    }

    @Test
    void protectsCookieEndpointsByExactAllowedOrigin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("CAMPUS_REFRESH", "unknown")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("INVALID_REQUEST_ORIGIN"));
        mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", "https://evil.example").cookie(new Cookie("CAMPUS_REFRESH", "unknown")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("INVALID_REQUEST_ORIGIN"));
    }

    @Test
    void rotatesRefreshTokenAndRejectsItsReuse() throws Exception {
        users.save(UserAccount.create(UUID.randomUUID(), "refresh@campus.example", passwordEncoder.encode("test-password"), Instant.now()));
        var login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"refresh@campus.example\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        Cookie refresh = login.getCookie("CAMPUS_REFRESH");
        var rotated = mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", "http://localhost:3000").cookie(refresh))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty()).andReturn().getResponse();
        mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", "http://localhost:3000").cookie(refresh))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));
        mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", "http://localhost:3000").cookie(rotated.getCookie("CAMPUS_REFRESH")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REVOKED"));
    }

    @Test
    void logoutRevokesTokenClearsCookieAndIsIdempotent() throws Exception {
        Cookie refresh = login("logout@campus.example");
        var logout = mockMvc.perform(post("/api/v1/auth/logout").header("Origin", "http://localhost:3000").cookie(refresh))
                .andExpect(status().isNoContent()).andExpect(cookie().maxAge("CAMPUS_REFRESH", 0)).andExpect(cookie().httpOnly("CAMPUS_REFRESH", true)).andReturn().getResponse();
        assertThat(logout.getHeader("Set-Cookie")).contains("CAMPUS_REFRESH=", "Path=/api/v1/auth", "Max-Age=0", "HttpOnly", "Secure", "SameSite=Lax");
        assertThat(refreshTokens.findByTokenHash(tokens.hash(refresh.getValue())).orElseThrow().revokedAt()).isNotNull();
        mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", "http://localhost:3000").cookie(refresh))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REVOKED"));
        mockMvc.perform(post("/api/v1/auth/logout").header("Origin", "http://localhost:3000").cookie(refresh)).andExpect(status().isNoContent());
    }

    @Test
    void rejectedLogoutOriginDoesNotRevokeToken() throws Exception {
        Cookie refresh = login("logout-origin@campus.example");
        mockMvc.perform(post("/api/v1/auth/logout").header("Origin", "https://evil.example").cookie(refresh))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("INVALID_REQUEST_ORIGIN"));
        assertThat(refreshTokens.findByTokenHash(tokens.hash(refresh.getValue())).orElseThrow().revokedAt()).isNull();
    }

    @Test
    void classifiesExpiredAndIndependentlyRevokedRefreshTokens() throws Exception {
        Cookie expiredCookie = login("expired@campus.example");
        RefreshToken expired = refreshTokens.findByTokenHash(tokens.hash(expiredCookie.getValue())).orElseThrow();
        long beforeCount = countTokens(expired.sessionId());
        refreshTokens.save(new RefreshToken(expired.id(), expired.sessionId(), expired.tokenHash(), Instant.now().minusSeconds(2), Instant.now().minusSeconds(1), null, null));
        var expiredResponse = mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", "http://localhost:3000").cookie(expiredCookie))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("REFRESH_TOKEN_EXPIRED")).andReturn().getResponse();
        RefreshToken unchanged = refreshTokens.findById(expired.id()).orElseThrow();
        assertThat(countTokens(expired.sessionId())).isEqualTo(beforeCount); assertThat(unchanged.replacedById()).isNull(); assertThat(unchanged.revokedAt()).isNull();
        assertThat(expiredResponse.getHeader("Set-Cookie")).isNull(); assertThat(expiredResponse.getContentAsString()).doesNotContain("accessToken");
        Cookie revokedCookie = login("revoked@campus.example");
        RefreshToken revoked = refreshTokens.findByTokenHash(tokens.hash(revokedCookie.getValue())).orElseThrow();
        revoked.revoke(Instant.now(), null); refreshTokens.save(revoked);
        mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", "http://localhost:3000").cookie(revokedCookie))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REVOKED"));
    }

    @Test
    void checksCurrentAccountStateAndStoresOnlyRefreshDigest() throws Exception {
        UserAccount user = users.save(UserAccount.create(UUID.randomUUID(), "state@campus.example", passwordEncoder.encode("test-password"), Instant.now()));
        var response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"state@campus.example\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        Cookie refresh = response.getCookie("CAMPUS_REFRESH");
        RefreshToken stored = refreshTokens.findByTokenHash(tokens.hash(refresh.getValue())).orElseThrow();
        byte[] raw = refresh.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(stored.tokenHash()).hasSize(32).isEqualTo(tokens.hash(refresh.getValue())).isNotEqualTo(raw);
        assertThat(contains(stored.tokenHash(), raw)).isFalse();
        assertThat(java.util.Arrays.stream(RefreshTokenEntity.class.getDeclaredFields()).noneMatch(field -> CharSequence.class.isAssignableFrom(field.getType()))).isTrue();
        @SuppressWarnings("unchecked") java.util.List<Object[]> columns = entityManager.createNativeQuery("select column_name, data_type from information_schema.columns where table_schema = 'public' and table_name = 'identity_refresh_tokens'").getResultList();
        assertThat(columns).anyMatch(column -> column[0].equals("token_hash") && column[1].equals("bytea"));
        assertThat(columns).noneMatch(column -> ((String) column[1]).contains("character") || column[1].equals("text"));
        user.changeStatus(AccountStatus.DISABLED); users.save(user);
        String access = response.getContentAsString().replaceAll(".*\\\"accessToken\\\":\\\"([^\\\"]+).*", "$1");
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE")).andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void keepsOnlyApprovedRoutesPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("MISSING_ACCESS_TOKEN"));
        mockMvc.perform(get("/api/v1/unregistered")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("MISSING_ACCESS_TOKEN"));
        mockMvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("INVALID_REQUEST_ORIGIN"));
    }

    private Cookie login(String email) throws Exception {
        users.save(UserAccount.create(UUID.randomUUID(), email, passwordEncoder.encode("test-password"), Instant.now()));
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("CAMPUS_REFRESH");
    }
    private long countTokens(UUID sessionId) { return entityManager.createQuery("select count(token) from RefreshTokenEntity token where token.session.id = :sessionId", Long.class).setParameter("sessionId", sessionId).getSingleResult(); }
    private boolean contains(byte[] value, byte[] part) { if (part.length > value.length) return false; for (int i = 0; i <= value.length - part.length; i++) { int j = 0; while (j < part.length && value[i + j] == part[j]) j++; if (j == part.length) return true; } return false; }
}
