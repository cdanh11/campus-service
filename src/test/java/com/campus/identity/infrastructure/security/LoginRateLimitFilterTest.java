package com.campus.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;

import com.campus.identity.api.SecurityErrorResponseWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LoginRateLimitFilterTest {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Test
    void rejectsLoginAttemptsBeyondTheWindow() throws Exception {
        var filter = filter(new RateLimitProperties(true, 2, 5));

        assertThat(post(filter, "/api/v1/auth/login", "10.0.0.1").getStatus()).isEqualTo(200);
        assertThat(post(filter, "/api/v1/auth/login", "10.0.0.1").getStatus()).isEqualTo(200);
        var limited = post(filter, "/api/v1/auth/login", "10.0.0.1");

        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isNotBlank();
        assertThat(limited.getContentAsString()).contains("RATE_LIMITED");
    }

    @Test
    void keepsClientsAndOtherEndpointsIndependent() throws Exception {
        var filter = filter(new RateLimitProperties(true, 1, 1));

        assertThat(post(filter, "/api/v1/auth/login", "10.0.0.1").getStatus()).isEqualTo(200);
        assertThat(post(filter, "/api/v1/auth/login", "10.0.0.2").getStatus()).isEqualTo(200);
        assertThat(post(filter, "/api/v1/auth/logout", "10.0.0.1").getStatus()).isEqualTo(200);
    }

    @Test
    void doesNothingWhenDisabled() throws Exception {
        var filter = filter(new RateLimitProperties(false, 1, 1));

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(post(filter, "/api/v1/auth/login", "10.0.0.1").getStatus()).isEqualTo(200);
        }
    }

    private static LoginRateLimitFilter filter(RateLimitProperties properties) {
        return new LoginRateLimitFilter(properties, new SecurityErrorResponseWriter(JSON), Clock.systemUTC());
    }

    private static MockHttpServletResponse post(LoginRateLimitFilter filter, String path, String client) throws Exception {
        var request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(client);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
