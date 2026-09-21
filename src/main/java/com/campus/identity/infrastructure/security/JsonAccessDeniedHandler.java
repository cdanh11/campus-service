package com.campus.identity.infrastructure.security;

import java.io.IOException;

import com.campus.identity.api.SecurityErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

public final class JsonAccessDeniedHandler implements AccessDeniedHandler {
    private final SecurityErrorResponseWriter errors;
    public JsonAccessDeniedHandler(SecurityErrorResponseWriter errors) { this.errors = errors; }
    @Override public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception) throws IOException {
        errors.write(request, response, 403, "FORBIDDEN", "Access is forbidden");
    }
}
