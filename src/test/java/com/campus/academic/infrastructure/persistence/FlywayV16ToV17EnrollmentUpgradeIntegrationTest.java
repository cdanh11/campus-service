package com.campus.academic.infrastructure.persistence;

import java.sql.SQLException;
import java.util.*;
import com.campus.academic.infrastructure.persistence.enrollment.EnrollmentEntity;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
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
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class FlywayV16ToV17EnrollmentUpgradeIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6-alpine");
    static JdbcTemplate jdbc;
    static UUID student = UUID.randomUUID(), section = UUID.randomUUID();
    static Map<String, List<Map<String, Object>>> legacy;
    static List<Map<String, Object>> history;

    @BeforeAll static void upgrade() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        assertThat(flyway("16").migrate().migrationsExecuted).isEqualTo(16);
        UUID unit = UUID.randomUUID(), course = UUID.randomUUID(), term = UUID.randomUUID(), offering = UUID.randomUUID(), faculty = UUID.randomUUID(), user = UUID.randomUUID();
        jdbc.update("INSERT INTO identity_users (id, email, display_name, password_hash, status, security_version, row_version) VALUES (?, 'enrollment@campus.example', 'Legacy User', 'legacy-hash', 'ACTIVE', 7, 3)", user);
        jdbc.update("INSERT INTO identity_user_roles (user_id, role_id) VALUES (?, '00000000-0000-0000-0000-000000000002')", user);
        jdbc.update("INSERT INTO organization_units (id, code, name, unit_type, row_version) VALUES (?, 'EN01', 'Engineering', 'FACULTY', 4)", unit);
        jdbc.update("INSERT INTO students (id, student_number, full_name, identity_user_id, organization_unit_id, row_version) VALUES (?, 'ST01', 'Student', ?, ?, 5)", student, user, unit);
        jdbc.update("INSERT INTO faculty_staff (id, personnel_number, full_name, personnel_type, organization_unit_id, row_version) VALUES (?, 'FA01', 'Faculty', 'FACULTY', ?, 3)", faculty, unit);
        jdbc.update("INSERT INTO academic_programs (id, code, name, organization_unit_id, row_version) VALUES (?, 'PR01', 'Program', ?, 2)", UUID.randomUUID(), unit);
        jdbc.update("INSERT INTO academic_courses (id, code, title, credits, organization_unit_id, row_version) VALUES (?, 'CR01', 'Course', 3, ?, 6)", course, unit);
        jdbc.update("INSERT INTO academic_terms (id, code, name, start_date, end_date, status, row_version) VALUES (?, 'TE01', 'Term', DATE '2026-01-01', DATE '2026-06-01', 'ACTIVE', 3)", term);
        jdbc.update("INSERT INTO academic_course_offerings (id, term_id, course_id, organization_unit_id, status, row_version) VALUES (?, ?, ?, ?, 'OPEN', 2)", offering, term, course, unit);
        jdbc.update("INSERT INTO academic_class_sections (id, offering_id, code, capacity, faculty_id, status, row_version) VALUES (?, ?, 'SC01', 1, ?, 'OPEN', 4)", section, offering, faculty);
        jdbc.update("INSERT INTO people_registry_audit_events (id, actor_user_id, resource_type, target_id, action, occurred_at) VALUES (?, ?, 'STUDENT', ?, 'CREATED', now())", UUID.randomUUID(), user, student);
        legacy = snapshot();
        history = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        assertThat(flyway("17").migrate().migrationsExecuted).isEqualTo(1);
    }

    @Test void preservesEveryExistingTableAndMigrationHistory() {
        assertThat(snapshot()).isEqualTo(legacy);
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history WHERE installed_rank <= 16 ORDER BY installed_rank")).isEqualTo(history);
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE installed_rank > 16", String.class)).containsExactly("17");
        assertThat(flyway("17").validateWithResult().validationSuccessful).isTrue();
    }

    @Test void verifiesColumnsDefaultsKeysChecksAndIndexes() {
        var columns = jdbc.queryForList("SELECT column_name, data_type, character_maximum_length, is_nullable, column_default FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'academic_enrollments'");
        var types = Map.of("id", "uuid", "student_id", "uuid", "section_id", "uuid", "status", "character varying", "row_version", "bigint", "created_at", "timestamp with time zone", "updated_at", "timestamp with time zone");
        assertThat(columns).hasSize(7);
        for (var column : columns) {
            var name = (String) column.get("column_name");
            assertThat(column).containsEntry("data_type", types.get(name)).containsEntry("is_nullable", "NO")
                    .containsEntry("character_maximum_length", name.equals("status") ? 20 : null);
            if (name.equals("status")) assertThat(column.get("column_default")).isEqualTo("'ENROLLED'::character varying");
            if (name.equals("row_version")) assertThat(column.get("column_default")).isEqualTo("0");
            if (name.endsWith("_at")) assertThat(column.get("column_default")).isEqualTo("CURRENT_TIMESTAMP");
        }
        var definitions = jdbc.queryForList("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid = 'academic_enrollments'::regclass", String.class);
        assertThat(definitions).contains("PRIMARY KEY (id)", "UNIQUE (student_id, section_id)", "FOREIGN KEY (student_id) REFERENCES students(id)", "FOREIGN KEY (section_id) REFERENCES academic_class_sections(id)");
        var indexes = jdbc.queryForList("SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'academic_enrollments'", String.class);
        assertThat(indexes).anyMatch(value -> value.contains("(section_id, status)")).anyMatch(value -> value.contains("(student_id, status)"));
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO academic_enrollments (id, student_id, section_id) VALUES (?, ?, ?)", id, student, section);
        var row = jdbc.queryForMap("SELECT * FROM academic_enrollments WHERE id = ?", id);
        assertThat(row).containsEntry("status", "ENROLLED").containsEntry("row_version", 0L);
        assertThat(row.get("created_at")).isNotNull(); assertThat(row.get("updated_at")).isNotNull();
        state("23505", () -> jdbc.update("INSERT INTO academic_enrollments (id, student_id, section_id) VALUES (?, ?, ?)", UUID.randomUUID(), student, section));
        state("23505", () -> jdbc.update("INSERT INTO academic_enrollments (id, student_id, section_id) VALUES (?, ?, ?)", id, student, section));
        for (String column : types.keySet()) state("23502", () -> jdbc.update("UPDATE academic_enrollments SET " + column + " = NULL WHERE id = ?", id));
        for (String column : List.of("student_id", "section_id")) state("23503", () -> jdbc.update("UPDATE academic_enrollments SET " + column + " = ? WHERE id = ?", UUID.randomUUID(), id));
        state("23514", () -> jdbc.update("UPDATE academic_enrollments SET status = 'UNKNOWN' WHERE id = ?", id));
        state("23514", () -> jdbc.update("UPDATE academic_enrollments SET row_version = -1 WHERE id = ?", id));
        state("22001", () -> jdbc.update("UPDATE academic_enrollments SET status = ? WHERE id = ?", "X".repeat(21), id));
        jdbc.update("UPDATE academic_enrollments SET status = 'WITHDRAWN', row_version = 1 WHERE id = ?", id);
        state("23505", () -> jdbc.update("INSERT INTO academic_enrollments (id, student_id, section_id) VALUES (?, ?, ?)", UUID.randomUUID(), student, section));
        jdbc.update("UPDATE academic_enrollments SET status = 'ENROLLED', row_version = 2 WHERE id = ?", id);
    }

    @Test void validatesProductionEntitiesOnExactUpgradedSchemaWithFlywayDisabled() {
        var before = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class, FlywayAutoConfiguration.class))
                .withUserConfiguration(ValidationConfiguration.class)
                .withPropertyValues("spring.datasource.url=" + postgres.getJdbcUrl(), "spring.datasource.username=" + postgres.getUsername(),
                        "spring.datasource.password=" + postgres.getPassword(), "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.properties.hibernate.default_schema=public")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(EntityManagerFactory.class).doesNotHaveBean(Flyway.class);
                    var model = context.getBean(EntityManagerFactory.class).getMetamodel();
                    assertThat(model.entity(EnrollmentEntity.class)).isNotNull(); assertThat(model.getEntities()).hasSize(15);
                });
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank")).isEqualTo(before);
        assertThat(snapshot()).isEqualTo(legacy);
    }

    @Configuration(proxyBeanMethods = false) @EntityScan("com.campus") static class ValidationConfiguration { }
    private static Flyway flyway(String version) { return Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).locations("classpath:db/migration").target(version).load(); }
    private static Map<String, List<Map<String, Object>>> snapshot() {
        var result = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (String table : jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history' AND tablename <> 'academic_enrollments' ORDER BY tablename", String.class)) {
            result.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY 1"));
        }
        return result;
    }
    private void state(String expected, Runnable operation) {
        Throwable failure = catchThrowable(operation::run); assertThat(failure).isNotNull();
        while (failure.getCause() != null) failure = failure.getCause();
        assertThat(failure).isInstanceOf(SQLException.class); assertThat(((SQLException) failure).getSQLState()).isEqualTo(expected);
    }
}
