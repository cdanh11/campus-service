package com.campus.identity.infrastructure.security;

import com.campus.identity.api.SecurityErrorResponseWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

class JsonAccessDeniedHandlerTest {
    @Test void writesApprovedEscapedForbiddenResponse() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/denied\\path");
        MockHttpServletResponse response = new MockHttpServletResponse();
        new JsonAccessDeniedHandler(new SecurityErrorResponseWriter(mapper)).handle(request, response, new AccessDeniedException("secret detail"));
        var body = mapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(403); assertThat(response.getContentType()).startsWith("application/json");
        assertThat(body.get("code").asText()).isEqualTo("FORBIDDEN"); assertThat(body.get("message").asText()).isEqualTo("Access is forbidden");
        assertThat(body.get("path").asText()).isEqualTo("/denied\\path"); assertThat(response.getContentAsString()).doesNotContain("secret detail");
    }
}
