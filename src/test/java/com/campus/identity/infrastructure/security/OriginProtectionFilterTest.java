package com.campus.identity.infrastructure.security;

import java.util.List;

import com.campus.identity.api.SecurityErrorResponseWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class OriginProtectionFilterTest {

    private final OriginProtectionFilter filter = new OriginProtectionFilter(
            new SecurityProperties(null, null, null, List.of("http://localhost:3000")),
            new SecurityErrorResponseWriter(new ObjectMapper()));

    @Test
    void appliesOriginProtectionToCookieEndpointsEvenWithPathParameters() {
        assertThat(filter.shouldNotFilter(request("POST", "/api/v1/auth/refresh;x=1"))).isFalse();
        assertThat(filter.shouldNotFilter(request("POST", "/api/v1/auth/logout;x=1"))).isFalse();
    }

    @Test
    void skipsRequestsThatDoNotUseRefreshCookies() {
        assertThat(filter.shouldNotFilter(request("GET", "/api/v1/auth/refresh"))).isTrue();
        assertThat(filter.shouldNotFilter(request("POST", "/api/v1/events"))).isTrue();
    }

    private MockHttpServletRequest request(String method, String requestUri) {
        return new MockHttpServletRequest(method, requestUri);
    }
}
