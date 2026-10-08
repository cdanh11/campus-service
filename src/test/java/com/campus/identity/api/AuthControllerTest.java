package com.campus.identity.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.campus.identity.application.AuthenticationService;
import com.campus.identity.domain.UserAccount;
import com.campus.identity.infrastructure.security.SecurityProperties;
import com.campus.identity.infrastructure.security.TokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTest {
    @ParameterizedTest
    @ValueSource(longs = {300, 900})
    void loginAndRefreshReportTheActualConfiguredJwtLifetime(long seconds) {
        var properties = new SecurityProperties(new SecurityProperties.Jwt("campus-service", "campus-client",
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=", Duration.ofSeconds(seconds)),
                Duration.ofDays(30), new SecurityProperties.RefreshCookie("CAMPUS_REFRESH", true, "Lax", "/api/v1/auth"),
                List.of("http://localhost:3000"));
        var tokens = new TokenService(properties, Clock.systemUTC());
        var user = UserAccount.create(UUID.randomUUID(), "ttl@example.test",
                "{bcrypt}$2b$12$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZabcde", Instant.now());
        var result = new AuthenticationService.AuthResult(user, tokens.accessToken(user), "test-refresh");
        var service = mock(AuthenticationService.class);
        when(service.login("ttl@example.test", "test-password")).thenReturn(result);
        when(service.refresh("test-refresh")).thenReturn(result);
        var controller = new AuthController(service, properties);
        var request = new MockHttpServletRequest();
        request.setCookies(new Cookie("CAMPUS_REFRESH", "test-refresh"));

        var login = controller.login(new AuthController.LoginRequest("ttl@example.test", "test-password"));
        var refresh = controller.refresh(request);
        var signed = tokens.decoder().decode(result.accessToken());
        long actualLifetime = Duration.between(signed.getIssuedAt(), signed.getExpiresAt()).toSeconds();
        assertThat(actualLifetime).isEqualTo(seconds);
        assertThat(login.getBody().expiresIn()).isEqualTo(actualLifetime);
        assertThat(refresh.getBody().expiresIn()).isEqualTo(actualLifetime);
        assertThat(login.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(refresh.getHeaders().getCacheControl()).isEqualTo("no-store");
    }
}
