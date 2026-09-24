package com.campus;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues(
                    "spring.profiles.active=production",
                    "POSTGRES_HOST=db.internal",
                    "POSTGRES_DB=campus_production",
                    "POSTGRES_USERNAME=campus_runtime",
                    "POSTGRES_PASSWORD=external-secret",
                    "JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                    "ALLOWED_ORIGINS=https://campus.example");

    @Test
    void bindsProductionSettingsWithoutDevelopmentFallbacks() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                    .isEqualTo("jdbc:postgresql://db.internal:5432/campus_production");
            assertThat(context.getEnvironment().getProperty("spring.datasource.username"))
                    .isEqualTo("campus_runtime");
            assertThat(context.getEnvironment().getProperty("spring.datasource.password"))
                    .isEqualTo("external-secret");
            assertThat(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"))
                    .isEqualTo("validate");
            assertThat(context.getEnvironment().getProperty("spring.flyway.enabled", Boolean.class)).isTrue();
            assertThat(context.getEnvironment().getProperty("campus.security.refresh-cookie.secure", Boolean.class)).isTrue();
            assertThat(context.getEnvironment().getProperty("campus.security.allowed-origins"))
                    .isEqualTo("https://campus.example");
        });
    }
}
