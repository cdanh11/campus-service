package com.campus.identity.infrastructure.security;

import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.campus.identity.api.SecurityErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/** Fixed-window, in-process throttling for credential endpoints; one instance is the monolith deployment. */
final class LoginRateLimitFilter extends OncePerRequestFilter {
    private static final long WINDOW_MILLIS = 60_000;
    private static final String LOGIN = "/api/v1/auth/login";
    private static final String REFRESH = "/api/v1/auth/refresh";

    private final RateLimitProperties properties;
    private final SecurityErrorResponseWriter errors;
    private final Clock clock;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    LoginRateLimitFilter(RateLimitProperties properties, SecurityErrorResponseWriter errors, Clock clock) {
        this.properties = properties;
        this.errors = errors;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.enabled() || !"POST".equals(request.getMethod())) return true;
        String path = request.getRequestURI();
        return !LOGIN.equals(path) && !REFRESH.equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean login = LOGIN.equals(request.getRequestURI());
        int limit = login ? properties.loginPerMinute() : properties.refreshPerMinute();
        long now = clock.millis();
        if (windows.size() > 10_000) windows.entrySet().removeIf(entry -> now - entry.getValue().start() >= WINDOW_MILLIS);
        Window window = windows.compute((login ? "login|" : "refresh|") + request.getRemoteAddr(), (key, current) ->
                current == null || now - current.start() >= WINDOW_MILLIS
                        ? new Window(now, 1)
                        : new Window(current.start(), current.count() + 1));
        if (window.count() > limit) {
            long retrySeconds = Math.max(1, (window.start() + WINDOW_MILLIS - now + 999) / 1000);
            response.setHeader("Retry-After", Long.toString(retrySeconds));
            errors.write(request, response, 429, "RATE_LIMITED", "Too many authentication attempts");
            return;
        }
        chain.doFilter(request, response);
    }

    private record Window(long start, int count) {
    }
}
