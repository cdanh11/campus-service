package com.campus.shared.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.campus.identity.infrastructure.persistence.entity.UserAccountEntity;
import com.campus.organization.infrastructure.persistence.entity.OrganizationUnitEntity;
import com.campus.personnel.infrastructure.persistence.entity.FacultyStaffEntity;
import com.campus.student.infrastructure.persistence.entity.StudentEntity;
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
import static org.assertj.core.api.Assertions.catchThrowable;

@Testcontainers
class FlywayV10ToV11PeopleAuditUpgradeIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6-alpine");
    static JdbcTemplate jdbc;
    static UUID actorId;

    @BeforeAll
    static void upgrade() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        assertThat(flyway("10").migrate().migrationsExecuted).isEqualTo(10);
        actorId = UUID.randomUUID();
        jdbc.update("INSERT INTO identity_users (id, email, display_name, password_hash, status) VALUES (?, 'audit-upgrade@campus.example', 'Audit Upgrade', 'hash', 'ACTIVE')", actorId);
        assertThat(flyway("11").migrate().migrationsExecuted).isEqualTo(1);
    }

    @Test
    void createsTheApprovedAuditSchemaAndRejectsInvalidWrites() {
        Map<String, Map<String, Object>> columns = jdbc.queryForList("""
                SELECT column_name, data_type, character_maximum_length, is_nullable, column_default
                FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'people_registry_audit_events'
                """).stream().collect(java.util.stream.Collectors.toMap(row -> (String) row.get("column_name"), row -> row));
        assertThat(columns.keySet()).containsExactlyInAnyOrder("id", "actor_user_id", "resource_type", "target_id", "action", "occurred_at", "metadata");
        assertColumn(columns, "id", "uuid", null, "NO");
        assertColumn(columns, "actor_user_id", "uuid", null, "NO");
        assertColumn(columns, "resource_type", "character varying", 32, "NO");
        assertColumn(columns, "target_id", "uuid", null, "NO");
        assertColumn(columns, "action", "character varying", 32, "NO");
        assertColumn(columns, "occurred_at", "timestamp with time zone", null, "NO");
        assertColumn(columns, "metadata", "character varying", 1000, "NO");
        assertThat(columns.get("metadata").get("column_default")).isEqualTo("'{}'::character varying");
        assertThat(constraints()).contains("people_registry_audit_events_pkey", "people_registry_audit_events_actor_user_id_fkey",
                "ck_people_registry_audit_resource_type", "ck_people_registry_audit_action");
        assertThat(indexes()).contains("ix_people_registry_audit_target_occurred");

        UUID eventId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        jdbc.update("INSERT INTO people_registry_audit_events (id, actor_user_id, resource_type, target_id, action, occurred_at) VALUES (?, ?, 'STUDENT', ?, 'CREATED', now())", eventId, actorId, targetId);
        assertThat(jdbc.queryForObject("SELECT metadata FROM people_registry_audit_events WHERE id = ?", String.class, eventId)).isEqualTo("{}");
        assertSqlState("23505", () -> jdbc.update("INSERT INTO people_registry_audit_events (id, actor_user_id, resource_type, target_id, action, occurred_at) VALUES (?, ?, 'STUDENT', ?, 'CREATED', now())", eventId, actorId, UUID.randomUUID()));
        assertSqlState("23503", () -> jdbc.update("INSERT INTO people_registry_audit_events (id, actor_user_id, resource_type, target_id, action, occurred_at) VALUES (?, ?, 'STUDENT', ?, 'CREATED', now())", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
        assertSqlState("23514", () -> jdbc.update("INSERT INTO people_registry_audit_events (id, actor_user_id, resource_type, target_id, action, occurred_at) VALUES (?, ?, 'INVALID', ?, 'CREATED', now())", UUID.randomUUID(), actorId, UUID.randomUUID()));
        assertSqlState("23514", () -> jdbc.update("INSERT INTO people_registry_audit_events (id, actor_user_id, resource_type, target_id, action, occurred_at) VALUES (?, ?, 'STUDENT', ?, 'DELETED', now())", UUID.randomUUID(), actorId, UUID.randomUUID()));
        assertSqlState("23502", () -> jdbc.update("INSERT INTO people_registry_audit_events (id, actor_user_id, resource_type, target_id, action, occurred_at) VALUES (?, ?, 'STUDENT', ?, 'CREATED', NULL)", UUID.randomUUID(), actorId, UUID.randomUUID()));
        assertSqlState("22001", () -> jdbc.update("INSERT INTO people_registry_audit_events (id, actor_user_id, resource_type, target_id, action, occurred_at) VALUES (?, ?, ?, ?, 'CREATED', now())", UUID.randomUUID(), actorId, "X".repeat(33), UUID.randomUUID()));
    }

    @Test
    void hibernateValidatesTheExactV11UpgradeWithoutRunningFlyway() {
        List<Map<String, Object>> history = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class, FlywayAutoConfiguration.class))
                .withUserConfiguration(ValidationConfiguration.class)
                .withPropertyValues("spring.datasource.url=" + postgres.getJdbcUrl(), "spring.datasource.username=" + postgres.getUsername(),
                        "spring.datasource.password=" + postgres.getPassword(), "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.properties.hibernate.default_schema=public")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(EntityManagerFactory.class).doesNotHaveBean(Flyway.class));
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank")).isEqualTo(history);
    }

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = {UserAccountEntity.class, OrganizationUnitEntity.class, StudentEntity.class, FacultyStaffEntity.class, PeopleRegistryAuditEntity.class})
    static class ValidationConfiguration { }

    private static Flyway flyway(String target) {
        return Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).locations("classpath:db/migration").target(target).load();
    }
    private List<String> constraints() { return jdbc.queryForList("SELECT conname FROM pg_constraint WHERE conrelid = 'people_registry_audit_events'::regclass", String.class); }
    private List<String> indexes() { return jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'people_registry_audit_events'", String.class); }
    private void assertColumn(Map<String, Map<String, Object>> columns, String name, String type, Integer length, String nullable) {
        assertThat(columns.get(name)).containsEntry("data_type", type).containsEntry("character_maximum_length", length).containsEntry("is_nullable", nullable);
    }
    private void assertSqlState(String expected, Runnable operation) {
        Throwable failure = catchThrowable(operation::run); assertThat(failure).isNotNull();
        while (failure.getCause() != null) failure = failure.getCause();
        assertThat(failure).isInstanceOf(java.sql.SQLException.class);
        assertThat(((java.sql.SQLException) failure).getSQLState()).isEqualTo(expected);
    }
}
