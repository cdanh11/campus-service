package com.campus.identity.infrastructure.security;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import com.campus.identity.api.SecurityErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final TokenService tokens;
    private final SecurityErrorResponseWriter errors;
    public JwtAuthenticationFilter(TokenService tokens, SecurityErrorResponseWriter errors) { this.tokens = tokens; this.errors = errors; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Jwt jwt = tokens.decoder().decode(header.substring(7));
                List<String> roles = jwt.getClaimAsStringList("roles");
                if (jwt.getSubject() == null || jwt.getId() == null || !(jwt.getClaim("roles") instanceof List<?>) || roles == null) throw new IllegalArgumentException("required claims");
                var authorities = roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(UUID.fromString(jwt.getSubject()), null, authorities));
            } catch (JwtValidationException exception) {
                boolean expired = exception.getErrors().stream().anyMatch(error -> error.getDescription() != null && error.getDescription().toLowerCase().contains("expired"));
                errors.write(request, response, 401, expired ? "ACCESS_TOKEN_EXPIRED" : "INVALID_ACCESS_TOKEN", expired ? "Access token has expired" : "Access token is invalid"); return;
            } catch (RuntimeException exception) { errors.write(request, response, 401, "INVALID_ACCESS_TOKEN", "Access token is invalid"); return; }
        }
        chain.doFilter(request, response);
    }
}
