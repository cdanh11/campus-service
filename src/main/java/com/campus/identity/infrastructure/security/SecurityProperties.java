package com.campus.identity.infrastructure.security;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("campus.security")
public record SecurityProperties(Jwt jwt, Duration refreshTokenTtl, RefreshCookie refreshCookie, List<String> allowedOrigins) {

    public record Jwt(String issuer, String audience, String secret, Duration accessTokenTtl) {
    }

    public record RefreshCookie(String name, boolean secure, String sameSite, String path) {
    }
}
