package com.campus.identity.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.campus.testsupport.LatestSchemaDatabase;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class FlywayV27ToV28RoleConsolidationIntegrationTest {
    private static final Set<String> FUNCTION_ROLES = Set.of(
            "ORGANIZATION_ADMIN", "STUDENT_ADMIN", "PERSONNEL_ADMIN", "ACADEMIC_ADMIN",
            "DORMITORY_ADMIN", "NOTIFICATION_ADMIN", "EVENT_ADMIN", "LIBRARY_ADMIN",
            "FINANCE_ADMIN", "AUDIT_VIEWER", "REPORTING_VIEWER");
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6-alpine");
    static JdbcTemplate jdbc;
    static UUID consolidated;
    static UUID duplicate;
    static List<Map<String, Object>> users;
    static List<Map<String, Object>> history;
    static List<Map<String, Object>> columns;

    @BeforeAll static void upgradePopulatedV27() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        flyway("27").migrate();
        consolidated = user("consolidated@example.test");
        duplicate = user("duplicate@example.test");
        for (String code : FUNCTION_ROLES) assign(consolidated, code);
        assign(consolidated, "USER");
        assign(consolidated, "ADMIN");
        assign(duplicate, "STUDENT_ADMIN");
        assign(duplicate, "ACADEMIC_ADMIN");
        users = jdbc.queryForList("SELECT * FROM identity_users ORDER BY id");
        history = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        columns = jdbc.queryForList("SELECT table_name,column_name,data_type,is_nullable,column_default FROM information_schema.columns WHERE table_schema='public' ORDER BY table_name,ordinal_position");
        assertThat(flyway("28").migrate().migrationsExecuted).isEqualTo(1);
    }

    @Test void replacesFunctionRolesWithDepartmentRolesAndPreservesMemberships() {
        assertThat(jdbc.queryForList("SELECT code FROM identity_roles ORDER BY code", String.class))
                .containsExactly("ACADEMIC_AFFAIRS", "ADMIN", "AUDITOR", "CAMPUS_SERVICES", "FINANCE", "USER");
        assertThat(jdbc.queryForList("SELECT code FROM identity_roles WHERE code NOT IN ('USER','ADMIN','ACADEMIC_AFFAIRS','CAMPUS_SERVICES','FINANCE','AUDITOR')", String.class)).isEmpty();
        assertThat(codesOf(consolidated)).containsExactly("ACADEMIC_AFFAIRS", "ADMIN", "AUDITOR", "CAMPUS_SERVICES", "FINANCE", "USER");
        assertThat(codesOf(duplicate)).containsExactly("ACADEMIC_AFFAIRS");
        assertThat(jdbc.queryForList("SELECT * FROM identity_users ORDER BY id")).isEqualTo(users);
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history WHERE installed_rank<=27 ORDER BY installed_rank")).isEqualTo(history);
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE installed_rank>27", String.class)).containsExactly("28");
        assertThat(jdbc.queryForList("SELECT table_name,column_name,data_type,is_nullable,column_default FROM information_schema.columns WHERE table_schema='public' ORDER BY table_name,ordinal_position")).isEqualTo(columns);
        assertThat(flyway("28").migrate().migrationsExecuted).isZero();
        assertThat(flyway("28").validateWithResult().validationSuccessful).isTrue();
    }

    @Test void validatesAllProductionEntitiesAgainstTheLatestSchema() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class, FlywayAutoConfiguration.class))
                .withUserConfiguration(ValidationConfiguration.class)
                .withPropertyValues("spring.datasource.url=" + LatestSchemaDatabase.migrate(postgres),
                        "spring.datasource.username=" + postgres.getUsername(),
                        "spring.datasource.password=" + postgres.getPassword(),
                        "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
                        "spring.jpa.properties.hibernate.default_schema=public", "spring.jpa.open-in-view=false")
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(Flyway.class).hasSingleBean(EntityManagerFactory.class);
                    assertThat(context.getBean(EntityManagerFactory.class).getMetamodel().getEntities()).hasSize(37);
                });
    }

    private static UUID user(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO identity_users(id,email,display_name,password_hash,status,security_version,row_version) VALUES (?,?,'Migration fixture','migration-hash','ACTIVE',0,0)", id, email);
        return id;
    }

    private static void assign(UUID userId, String roleCode) {
        jdbc.update("INSERT INTO identity_user_roles(user_id,role_id) SELECT ?,id FROM identity_roles WHERE code=?", userId, roleCode);
    }

    private static List<String> codesOf(UUID userId) {
        return jdbc.queryForList("SELECT role.code FROM identity_user_roles membership JOIN identity_roles role ON role.id=membership.role_id WHERE membership.user_id=? ORDER BY role.code", String.class, userId);
    }

    private static Flyway flyway(String target) {
        return Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").target(target).load();
    }

    @Configuration(proxyBeanMethods = false) @EntityScan("com.campus")
    static class ValidationConfiguration { }
}
