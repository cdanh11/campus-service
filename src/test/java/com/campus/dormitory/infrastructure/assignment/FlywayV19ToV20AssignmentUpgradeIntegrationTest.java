package com.campus.dormitory.infrastructure.assignment;

import java.sql.SQLException;
import java.util.*;
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
class FlywayV19ToV20AssignmentUpgradeIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6-alpine");
    static JdbcTemplate jdbc;
    static UUID actor = UUID.randomUUID(), building = UUID.randomUUID(), room = UUID.randomUUID(), bed = UUID.randomUUID();
    static UUID legacyStudent;
    static Map<String, List<Map<String, Object>>> legacy;
    static List<Map<String, Object>> history;

    @BeforeAll static void upgrade() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        assertThat(flyway("19").migrate().migrationsExecuted).isEqualTo(19);
        UUID unit = UUID.randomUUID(), student = UUID.randomUUID(), faculty = UUID.randomUUID(), course = UUID.randomUUID();
        UUID term = UUID.randomUUID(), offering = UUID.randomUUID(), section = UUID.randomUUID(), enrollment = UUID.randomUUID();
        jdbc.update("INSERT INTO identity_users (id, email, display_name, password_hash, status, security_version, row_version) VALUES (?, 'legacy@campus.example', 'Legacy User', 'legacy-hash', 'ACTIVE', 7, 3)", actor);
        jdbc.update("INSERT INTO identity_user_roles (user_id, role_id) VALUES (?, '00000000-0000-0000-0000-000000000002')", actor);
        jdbc.update("INSERT INTO organization_units (id, code, name, unit_type, row_version) VALUES (?, 'UN', 'Unit', 'FACULTY', 2)", unit);
        jdbc.update("INSERT INTO students (id, student_number, full_name, organization_unit_id, identity_user_id, row_version) VALUES (?, 'ST', 'Student', ?, ?, 4)", student, unit, actor);
        jdbc.update("INSERT INTO faculty_staff (id, personnel_number, full_name, personnel_type, organization_unit_id) VALUES (?, 'FA', 'Faculty', 'FACULTY', ?)", faculty, unit);
        jdbc.update("INSERT INTO academic_programs (id, code, name, organization_unit_id) VALUES (?, 'PR', 'Program', ?)", UUID.randomUUID(), unit);
        jdbc.update("INSERT INTO academic_courses (id, code, title, credits, organization_unit_id) VALUES (?, 'CR', 'Course', 3, ?)", course, unit);
        jdbc.update("INSERT INTO academic_terms (id, code, name, start_date, end_date, status) VALUES (?, 'TE', 'Term', DATE '2026-01-01', DATE '2026-06-01', 'ACTIVE')", term);
        jdbc.update("INSERT INTO academic_course_offerings (id, term_id, course_id, organization_unit_id, status) VALUES (?, ?, ?, ?, 'OPEN')", offering, term, course, unit);
        jdbc.update("INSERT INTO academic_class_sections (id, offering_id, code, capacity, faculty_id, status) VALUES (?, ?, 'SC', 1, ?, 'OPEN')", section, offering, faculty);
        jdbc.update("INSERT INTO academic_enrollments (id, student_id, section_id, status, row_version) VALUES (?, ?, ?, 'WITHDRAWN', 2)", enrollment, student, section);
        jdbc.update("INSERT INTO people_registry_audit_events (id, actor_user_id, resource_type, target_id, action, occurred_at) VALUES (?, ?, 'STUDENT', ?, 'CREATED', now())", UUID.randomUUID(), actor, student);
        jdbc.update("INSERT INTO academic_audit_events (id, actor_user_id, resource_type, target_id, action, resource_version, metadata) VALUES (?, ?, 'ENROLLMENT', ?, 'WITHDRAWN', 2, '{\"status\":\"WITHDRAWN\"}')", UUID.randomUUID(), actor, enrollment);

        legacyStudent = student;
        jdbc.update("INSERT INTO dormitory_buildings (id, code, name, row_version) VALUES (?, 'BL', 'Building', 3)", building);
        jdbc.update("INSERT INTO dormitory_rooms (id, building_id, code, name, row_version) VALUES (?, ?, 'RO', 'Room', 2)", room, building);
        jdbc.update("INSERT INTO dormitory_beds (id, room_id, code, name, row_version) VALUES (?, ?, 'BE', 'Bed', 1)", bed, room);
        jdbc.update("INSERT INTO dormitory_audit_events (id, actor_user_id, resource_type, target_id, action, resource_version, metadata) VALUES (?, ?, 'BED', ?, 'UPDATED', 1, '{\"status\":\"ACTIVE\"}')", UUID.randomUUID(), actor, bed);
        legacy = snapshot(); history = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        assertThat(flyway("20").migrate().migrationsExecuted).isEqualTo(1);
    }

    @Test void preservesAllLegacyTablesAndHistoryAndAppliesOnlyV20() {
        assertThat(snapshot()).isEqualTo(legacy);
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history WHERE installed_rank <= 19 ORDER BY installed_rank")).isEqualTo(history);
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE installed_rank > 19", String.class)).containsExactly("20");
        assertThat(flyway("20").validateWithResult().validationSuccessful).isTrue();
    }

    @Test void verifiesAssignmentSchemaUniquenessAndAuditPolicy() {
        var columns = new LinkedHashMap<String, Map<String, Object>>();
        for (var column : jdbc.queryForList("SELECT column_name, data_type, character_maximum_length, is_nullable, column_default FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'dormitory_assignments'")) columns.put(column.get("column_name").toString(), column);
        var types = Map.of("id", "uuid", "student_id", "uuid", "bed_id", "uuid", "status", "character varying",
                "row_version", "bigint", "assigned_at", "timestamp with time zone", "released_at", "timestamp with time zone",
                "created_at", "timestamp with time zone", "updated_at", "timestamp with time zone");
        assertThat(columns).hasSize(9);
        types.forEach((column, type) -> assertThat(columns.get(column)).containsEntry("data_type", type).containsEntry("is_nullable", column.equals("released_at") ? "YES" : "NO"));
        assertThat(columns.get("status").get("character_maximum_length")).isEqualTo(16);
        assertThat(columns.get("status").get("column_default").toString()).contains("ASSIGNED");
        assertThat(columns.get("row_version").get("column_default").toString()).contains("0");
        for (String column : List.of("assigned_at", "created_at", "updated_at")) assertThat(columns.get(column).get("column_default").toString()).contains("CURRENT_TIMESTAMP");
        for (String column : List.of("id", "student_id", "bed_id", "released_at")) assertThat(columns.get(column).get("column_default")).isNull();
        var keys = jdbc.queryForList("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid = 'dormitory_assignments'::regclass AND contype IN ('p','f')", String.class);
        assertThat(keys).containsExactlyInAnyOrder("PRIMARY KEY (id)", "FOREIGN KEY (student_id) REFERENCES students(id)", "FOREIGN KEY (bed_id) REFERENCES dormitory_beds(id)");
        var indexes = jdbc.queryForList("SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'dormitory_assignments'", String.class);
        for (String column : List.of("student_id", "bed_id")) {
            assertThat(indexes).anySatisfy(index -> assertThat(index).contains("UNIQUE", "(" + column + ")", "WHERE", "ASSIGNED"));
            assertThat(indexes).anyMatch(index -> index.contains("(" + column + ", status)"));
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO dormitory_assignments (id, student_id, bed_id) VALUES (?, ?, ?)", id, legacyStudent, bed);
        var row = jdbc.queryForMap("SELECT * FROM dormitory_assignments WHERE id = ?", id);
        assertThat(row).containsEntry("status", "ASSIGNED").containsEntry("row_version", 0L).containsEntry("released_at", null);
        for (String column : List.of("assigned_at", "created_at", "updated_at")) assertThat(row.get(column)).isNotNull();
        for (String column : types.keySet()) if (!column.equals("released_at")) state("23502", () -> jdbc.update("UPDATE dormitory_assignments SET " + column + " = NULL WHERE id = ?", id));
        state("23505", () -> jdbc.update("INSERT INTO dormitory_assignments SELECT * FROM dormitory_assignments WHERE id = ?", id));
        for (String column : List.of("student_id", "bed_id")) state("23503", () -> jdbc.update("UPDATE dormitory_assignments SET " + column + " = ? WHERE id = ?", UUID.randomUUID(), id));
        state("23514", () -> jdbc.update("UPDATE dormitory_assignments SET status = 'UNKNOWN' WHERE id = ?", id));
        state("22001", () -> jdbc.update("UPDATE dormitory_assignments SET status = ? WHERE id = ?", "X".repeat(17), id));
        state("23514", () -> jdbc.update("UPDATE dormitory_assignments SET row_version = -1 WHERE id = ?", id));
        state("23514", () -> jdbc.update("UPDATE dormitory_assignments SET released_at = assigned_at WHERE id = ?", id));
        state("23514", () -> jdbc.update("UPDATE dormitory_assignments SET status = 'RELEASED' WHERE id = ?", id));
        state("23514", () -> jdbc.update("UPDATE dormitory_assignments SET status = 'RELEASED', released_at = assigned_at - INTERVAL '1 second' WHERE id = ?", id));
        UUID otherBed = UUID.randomUUID(), otherStudent = UUID.randomUUID();
        jdbc.update("INSERT INTO dormitory_beds (id, room_id, code, name) VALUES (?, ?, 'OTHER', 'Other Bed')", otherBed, room);
        UUID unit = jdbc.queryForObject("SELECT organization_unit_id FROM students WHERE id = ?", UUID.class, legacyStudent);
        jdbc.update("INSERT INTO students (id, student_number, full_name, organization_unit_id) VALUES (?, 'OTHER', 'Other Student', ?)", otherStudent, unit);
        state("23505", () -> jdbc.update("INSERT INTO dormitory_assignments (id, student_id, bed_id) VALUES (?, ?, ?)", UUID.randomUUID(), legacyStudent, otherBed));
        state("23505", () -> jdbc.update("INSERT INTO dormitory_assignments (id, student_id, bed_id) VALUES (?, ?, ?)", UUID.randomUUID(), otherStudent, bed));
        jdbc.update("UPDATE dormitory_assignments SET status = 'RELEASED', released_at = assigned_at WHERE id = ?", id);
        jdbc.update("INSERT INTO dormitory_assignments (id, student_id, bed_id) VALUES (?, ?, ?)", UUID.randomUUID(), legacyStudent, bed);
        // Audit changes strengthen resource/action compatibility without inventing a JSON length cap.
        UUID event = UUID.randomUUID();
        jdbc.update("INSERT INTO dormitory_audit_events (id, actor_user_id, resource_type, target_id, action, resource_version) VALUES (?, ?, 'ASSIGNMENT', ?, 'ASSIGNED', 0)", event, actor, id);
        jdbc.update("UPDATE dormitory_audit_events SET action = 'RELEASED', resource_version = 1 WHERE id = ?", event);
        state("23514", () -> jdbc.update("UPDATE dormitory_audit_events SET action = 'CREATED' WHERE id = ?", event));
        state("23514", () -> jdbc.update("UPDATE dormitory_audit_events SET resource_type = 'BED' WHERE id = ?", event));
        // Clean up only test rows added to legacy tables, keeping preservation assertions order-independent.
        jdbc.update("DELETE FROM dormitory_assignments WHERE student_id = ? AND bed_id = ?", legacyStudent, bed);
        jdbc.update("DELETE FROM students WHERE id = ?", otherStudent);
        jdbc.update("DELETE FROM dormitory_beds WHERE id = ?", otherBed);
        jdbc.update("DELETE FROM dormitory_audit_events WHERE id = ?", event);
    }

    @Test void validatesAll21ProductionEntitiesAgainstExactUpgradeWithoutFlyway() {
        var before = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class, FlywayAutoConfiguration.class))
                .withUserConfiguration(ValidationConfiguration.class)
                .withPropertyValues("spring.datasource.url=" + postgres.getJdbcUrl(), "spring.datasource.username=" + postgres.getUsername(),
                        "spring.datasource.password=" + postgres.getPassword(), "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.properties.hibernate.default_schema=public")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(EntityManagerFactory.class).doesNotHaveBean(Flyway.class);
                    var model = context.getBean(EntityManagerFactory.class).getMetamodel();
                    assertThat(model.getEntities()).hasSize(21); assertThat(model.entity(AccommodationAssignmentEntity.class)).isNotNull();
                });
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank")).isEqualTo(before);
        assertThat(snapshot()).isEqualTo(legacy);
    }
    @Configuration(proxyBeanMethods = false) @EntityScan("com.campus") static class ValidationConfiguration { }
    private static Flyway flyway(String target) { return Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).locations("classpath:db/migration").target(target).load(); }
    private static Map<String, List<Map<String, Object>>> snapshot() {
        var result = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (String table : jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename NOT IN ('flyway_schema_history','dormitory_assignments') ORDER BY tablename", String.class)) result.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY 1"));
        return result;
    }
    private void state(String expected, Runnable task) {
        Throwable failure = catchThrowable(task::run); assertThat(failure).isNotNull();
        while (failure.getCause() != null) failure = failure.getCause();
        assertThat(failure).isInstanceOf(SQLException.class); assertThat(((SQLException) failure).getSQLState()).isEqualTo(expected);
    }
}
