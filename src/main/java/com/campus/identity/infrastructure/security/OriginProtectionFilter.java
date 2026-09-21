package com.campus.identity.infrastructure.security;

import java.io.IOException;
import java.net.URI;
import java.util.Set;
import java.util.stream.Collectors;

import com.campus.identity.api.SecurityErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

public class OriginProtectionFilter extends OncePerRequestFilter {
    private final Set<String> allowed;
    private final SecurityErrorResponseWriter errors;
    public OriginProtectionFilter(SecurityProperties properties, SecurityErrorResponseWriter errors) { this.allowed = properties.allowedOrigins().stream().map(OriginProtectionFilter::normalize).collect(Collectors.toUnmodifiableSet()); this.errors = errors; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) { return !(request.getMethod().equals("POST") && (request.getRequestURI().equals("/api/v1/auth/refresh") || request.getRequestURI().equals("/api/v1/auth/logout"))); }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        try { if (!allowed.contains(normalize(request.getHeader("Origin")))) { errors.write(request, response, 403, "INVALID_REQUEST_ORIGIN", "Request origin is not allowed"); return; } }
        catch (RuntimeException exception) { errors.write(request, response, 403, "INVALID_REQUEST_ORIGIN", "Request origin is not allowed"); return; }
        chain.doFilter(request, response);
    }
    private static String normalize(String origin) { URI uri = URI.create(origin); if (uri.getScheme() == null || uri.getHost() == null || uri.getPath() != null && !uri.getPath().isEmpty() && !uri.getPath().equals("/")) throw new IllegalArgumentException(); return uri.getScheme().toLowerCase() + "://" + uri.getHost().toLowerCase() + (uri.getPort() < 0 ? "" : ":" + uri.getPort()); }
}
