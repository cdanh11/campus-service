package com.campus.identity.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("campus.security.rate-limit")
public record RateLimitProperties(boolean enabled, int loginPerMinute, int refreshPerMinute) {

    public RateLimitProperties {
        loginPerMinute = loginPerMinute > 0 ? loginPerMinute : 20;
        refreshPerMinute = refreshPerMinute > 0 ? refreshPerMinute : 60;
    }
}
