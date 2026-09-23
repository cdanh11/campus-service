package com.campus.identity.infrastructure.security;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.campus.identity.api.SecurityErrorResponseWriter;
import com.campus.identity.domain.UserAccount;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {
    private static final String SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Test
    void rejectsInvalidSignatureExpiredIssuerAndAudience() throws Exception {
        assertInvalid(token(new SecurityProperties.Jwt("campus-service", "campus-service-clients", "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXowMTIzNDU2Nzg=", java.time.Duration.ofMinutes(15)), Clock.systemUTC()), "INVALID_ACCESS_TOKEN");
        assertInvalid(token(new SecurityProperties.Jwt("campus-service", "campus-service-clients", SECRET, java.time.Duration.ofMinutes(15)), Clock.fixed(Instant.parse("2000-01-01T00:00:00Z"), ZoneOffset.UTC)), "ACCESS_TOKEN_EXPIRED");
        assertInvalid(token(new SecurityProperties.Jwt("wrong-issuer", "campus-service-clients", SECRET, java.time.Duration.ofMinutes(15)), Clock.systemUTC()), "INVALID_ACCESS_TOKEN");
        assertInvalid(token(new SecurityProperties.Jwt("campus-service", "wrong-audience", SECRET, java.time.Duration.ofMinutes(15)), Clock.systemUTC()), "INVALID_ACCESS_TOKEN");
    }

    @Test
    void serializesEscapedSecurityErrorJson() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/quoted\\path");
        MockHttpServletResponse response = new MockHttpServletResponse();
        new SecurityErrorResponseWriter(mapper()).write(request, response, 403, "FORBIDDEN", "Quote: \" and slash: \\");
        var body = mapper().readTree(response.getContentAsString());
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(body.get("message").asText()).isEqualTo("Quote: \" and slash: \\");
    }

    @Test
    void rejectsMissingIssuerAudienceAndRequiredClaims() throws Exception {
        assertInvalid(custom(null, List.of("campus-service-clients"), true, true, List.of()), "INVALID_ACCESS_TOKEN");
        assertInvalid(custom("campus-service", null, true, true, List.of()), "INVALID_ACCESS_TOKEN");
        assertInvalid(custom("campus-service", List.of("campus-service-clients"), false, true, List.of()), "INVALID_ACCESS_TOKEN");
        assertInvalid(custom("campus-service", List.of("campus-service-clients"), true, false, List.of()), "INVALID_ACCESS_TOKEN");
        assertInvalid(custom("campus-service", List.of("campus-service-clients"), true, true, "USER"), "INVALID_ACCESS_TOKEN");
    }

    private String token(SecurityProperties.Jwt jwt, Clock clock) {
        TokenService service = new TokenService(new SecurityProperties(jwt, java.time.Duration.ofDays(30), new SecurityProperties.RefreshCookie("CAMPUS_REFRESH", false, "Lax", "/"), List.of("http://localhost:3000")), clock);
        return service.accessToken(UserAccount.create(UUID.randomUUID(), "jwt@campus.example", "{bcrypt}$2b$12$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZabcde", Instant.now()));
    }

    private void assertInvalid(String token, String code) throws Exception {
        TokenService valid = new TokenService(new SecurityProperties(new SecurityProperties.Jwt("campus-service", "campus-service-clients", SECRET, java.time.Duration.ofMinutes(15)), java.time.Duration.ofDays(30), new SecurityProperties.RefreshCookie("CAMPUS_REFRESH", false, "Lax", "/"), List.of("http://localhost:3000")), Clock.systemUTC());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me"); request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        new JwtAuthenticationFilter(valid, new SecurityErrorResponseWriter(mapper())).doFilter(request, response, (req, res) -> { });
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(mapper().readTree(response.getContentAsString()).get("code").asText()).isEqualTo(code);
    }
    private String custom(String issuer, List<String> audience, boolean subject, boolean id, Object roles) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder().issueTime(java.util.Date.from(Instant.now())).expirationTime(java.util.Date.from(Instant.now().plusSeconds(300))).claim("roles", roles);
        if (issuer != null) claims.issuer(issuer); if (audience != null) claims.audience(audience); if (subject) claims.subject(UUID.randomUUID().toString()); if (id) claims.jwtID(UUID.randomUUID().toString());
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build()); jwt.sign(new MACSigner(java.util.Base64.getDecoder().decode(SECRET))); return jwt.serialize();
    }
    private ObjectMapper mapper() { return new ObjectMapper().findAndRegisterModules(); }
}
