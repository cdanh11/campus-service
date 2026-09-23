package com.campus.identity.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityPropertiesConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(SecurityPropertiesConfiguration.class));

    @Test
    void usesSecureRefreshCookiesOutsideTheLocalProfile() {
        contextRunner.withPropertyValues("spring.profiles.active=production")
                .run(context -> assertThat(context.getBean(SecurityProperties.class).refreshCookie().secure()).isTrue());
    }

    @Test
    void testProfileUsesSecureRefreshCookies() {
        contextRunner.withPropertyValues("spring.profiles.active=test")
                .run(context -> assertThat(context.getBean(SecurityProperties.class).refreshCookie().secure()).isTrue());
    }

    @Test
    void localProfileDefaultsRefreshCookiesToInsecure() {
        contextRunner.withPropertyValues("spring.profiles.active=local")
                .run(context -> assertThat(context.getBean(SecurityProperties.class).refreshCookie().secure()).isFalse());
    }

    @Test
    void localProfileAllowsAnExplicitSecureCookieOverride() {
        contextRunner.withPropertyValues("spring.profiles.active=local", "REFRESH_COOKIE_SECURE=true")
                .run(context -> assertThat(context.getBean(SecurityProperties.class).refreshCookie().secure()).isTrue());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SecurityProperties.class)
    static class SecurityPropertiesConfiguration {
    }
}
