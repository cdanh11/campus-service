package com.campus.identity.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class FlywayV25ToV26PermissionsUpgradeIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6-alpine");
    static JdbcTemplate jdbc;
    static List<Map<String, Object>> history;
    static List<Map<String, Object>> users;
    static List<Map<String, Object>> memberships;
    static List<Map<String, Object>> legacyRoles;
    static List<Map<String, Object>> columns;

    @BeforeAll static void upgradePopulatedV25() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        flyway("25").migrate();
        UUID user = UUID.randomUUID();
        jdbc.update("INSERT INTO identity_users(id,email,display_name,password_hash,status,security_version,row_version) VALUES (?,'legacy@example.test','Legacy administrator','legacy-hash','ACTIVE',7,3)", user);
        jdbc.update("INSERT INTO identity_user_roles(user_id,role_id) SELECT ?,id FROM identity_roles WHERE code IN ('ADMIN','USER')", user);
        history = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        users = jdbc.queryForList("SELECT * FROM identity_users ORDER BY id");
        memberships = jdbc.queryForList("SELECT * FROM identity_user_roles ORDER BY user_id,role_id");
        legacyRoles = jdbc.queryForList("SELECT * FROM identity_roles ORDER BY id");
        columns = jdbc.queryForList("SELECT table_name,column_name,data_type,is_nullable,column_default FROM information_schema.columns WHERE table_schema='public' ORDER BY table_name,ordinal_position");
        assertThat(flyway("26").migrate().migrationsExecuted).isEqualTo(1);
    }

    @Test void addsExactlyApprovedRolesWithoutChangingLegacyUsersMembershipsSchemaOrHistory() {
        assertThat(jdbc.queryForList("SELECT * FROM identity_users ORDER BY id")).isEqualTo(users);
        assertThat(jdbc.queryForList("SELECT * FROM identity_user_roles ORDER BY user_id,role_id")).isEqualTo(memberships);
        assertThat(jdbc.queryForList("SELECT * FROM identity_roles WHERE code IN ('ADMIN','USER') ORDER BY id")).isEqualTo(legacyRoles);
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history WHERE installed_rank<=25 ORDER BY installed_rank")).isEqualTo(history);
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE installed_rank>25", String.class)).containsExactly("26");
        assertThat(jdbc.queryForList("SELECT code FROM identity_roles WHERE code NOT IN ('ADMIN','USER') ORDER BY code", String.class))
                .containsExactly("ACADEMIC_ADMIN", "AUDIT_VIEWER", "DORMITORY_ADMIN", "EVENT_ADMIN", "FINANCE_ADMIN",
                        "LIBRARY_ADMIN", "NOTIFICATION_ADMIN", "ORGANIZATION_ADMIN", "PERSONNEL_ADMIN", "REPORTING_VIEWER", "STUDENT_ADMIN");
        assertThat(jdbc.queryForList("SELECT table_name,column_name,data_type,is_nullable,column_default FROM information_schema.columns WHERE table_schema='public' ORDER BY table_name,ordinal_position")).isEqualTo(columns);
        assertThat(flyway("26").migrate().migrationsExecuted).isZero();
        assertThat(flyway("26").validateWithResult().validationSuccessful).isTrue();
    }

    @Test void validatesAllProductionEntitiesAgainstTheExactUpgradeWithFlywayDisabled() {
        var before = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class, FlywayAutoConfiguration.class))
                .withUserConfiguration(ValidationConfiguration.class)
                .withPropertyValues("spring.datasource.url=" + postgres.getJdbcUrl(), "spring.datasource.username=" + postgres.getUsername(),
                        "spring.datasource.password=" + postgres.getPassword(), "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
                        "spring.jpa.properties.hibernate.default_schema=public", "spring.jpa.open-in-view=false")
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(Flyway.class).hasSingleBean(EntityManagerFactory.class);
                    var factory = context.getBean(EntityManagerFactory.class);
                    assertThat(factory.getMetamodel().getEntities()).hasSize(36);
                    assertThat(factory.getProperties().get("hibernate.hbm2ddl.auto")).isEqualTo("validate");
                });
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank")).isEqualTo(before);
    }

    private static Flyway flyway(String target) {
        return Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").target(target).load();
    }

    @Configuration(proxyBeanMethods = false) @EntityScan("com.campus")
    static class ValidationConfiguration { }
}
