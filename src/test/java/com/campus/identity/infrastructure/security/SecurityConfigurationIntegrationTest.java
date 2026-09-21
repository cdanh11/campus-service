package com.campus.identity.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class SecurityConfigurationIntegrationTest {
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6-alpine");
    @Autowired JsonAccessDeniedHandler handler;
    @Autowired FilterChainProxy filterChainProxy;
    @Autowired ObjectMapper objectMapper;

    @Test
    void accessDeniedHandlerIsSpringManagedAndWiredIntoFilterChain() throws Exception {
        ExceptionTranslationFilter filter = filterChainProxy.getFilterChains().stream().flatMap(chain -> chain.getFilters().stream())
                .filter(ExceptionTranslationFilter.class::isInstance).map(ExceptionTranslationFilter.class::cast).findFirst().orElseThrow();
        assertThat(ReflectionTestUtils.getField(filter, "accessDeniedHandler")).isSameAs(handler);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/denied\\path");
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.handle(request, response, new AccessDeniedException("secret detail"));
        var body = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(403); assertThat(response.getContentType()).startsWith("application/json");
        assertThat(body.get("code").asText()).isEqualTo("FORBIDDEN"); assertThat(body.get("message").asText()).isEqualTo("Access is forbidden");
        assertThat(body.get("path").asText()).isEqualTo("/denied\\path"); assertThat(response.getContentAsString()).doesNotContain("secret detail");
    }
}
