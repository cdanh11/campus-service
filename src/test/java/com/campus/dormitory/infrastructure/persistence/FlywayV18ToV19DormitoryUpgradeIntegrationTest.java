package com.campus.dormitory.infrastructure.persistence;

import java.sql.SQLException;
import java.util.*;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
class FlywayV18ToV19DormitoryUpgradeIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6-alpine");
    static JdbcTemplate jdbc;
    static UUID actor = UUID.randomUUID(), building = UUID.randomUUID(), room = UUID.randomUUID();
    static Map<String, List<Map<String, Object>>> legacy;
    static List<Map<String, Object>> history;

    @BeforeAll static void upgrade() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        assertThat(flyway("18").migrate().migrationsExecuted).isEqualTo(18);
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
        legacy = snapshot(); history = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        assertThat(flyway("19").migrate().migrationsExecuted).isEqualTo(1);
        jdbc.update("INSERT INTO dormitory_buildings (id, code, name) VALUES (?, 'BL', 'Building')", building);
        jdbc.update("INSERT INTO dormitory_rooms (id, building_id, code, name) VALUES (?, ?, 'RO', 'Room')", room, building);
    }

    @Test void preservesAllBaselineTablesAndAppliesOnlyV19() {
        assertThat(snapshot()).isEqualTo(legacy);
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history WHERE installed_rank <= 18 ORDER BY installed_rank")).isEqualTo(history);
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE installed_rank > 18", String.class)).containsExactly("19");
        assertThat(flyway("19").validateWithResult().validationSuccessful).isTrue();
    }

    @ParameterizedTest @ValueSource(strings = {"buildings", "rooms", "beds"})
    void verifiesInventorySchemaAndApprovedValidInvalidWrites(String resource) {
        String table = "dormitory_" + resource;
        String parentColumn = resource.equals("buildings") ? null : resource.equals("rooms") ? "building_id" : "room_id";
        UUID parent = resource.equals("rooms") ? building : room;
        var columns = columns(table);
        var types = new LinkedHashMap<>(Map.of("id", "uuid", "code", "character varying", "name", "character varying",
                "status", "character varying", "row_version", "bigint", "created_at", "timestamp with time zone", "updated_at", "timestamp with time zone"));
        if (parentColumn != null) types.put(parentColumn, "uuid");
        assertThat(columns).hasSize(types.size());
        types.forEach((column, type) -> assertThat(columns.get(column)).containsEntry("data_type", type).containsEntry("is_nullable", "NO"));
        assertThat(columns.get("code").get("character_maximum_length")).isEqualTo(32);
        assertThat(columns.get("name").get("character_maximum_length")).isEqualTo(160);
        assertThat(columns.get("status").get("character_maximum_length")).isEqualTo(16);
        assertThat(columns.get("status").get("column_default").toString()).contains("ACTIVE");
        assertThat(columns.get("row_version").get("column_default").toString()).contains("0");
        for (String column : List.of("created_at", "updated_at")) assertThat(columns.get(column).get("column_default").toString()).contains("CURRENT_TIMESTAMP");
        for (String column : List.of("id", "code", "name")) assertThat(columns.get(column).get("column_default")).isNull();
        if (parentColumn != null) assertThat(columns.get(parentColumn).get("column_default")).isNull();
        var constraints = jdbc.queryForList("SELECT contype::text, pg_get_constraintdef(oid) AS definition FROM pg_constraint WHERE conrelid = ?::regclass", table);
        assertThat(constraints).anySatisfy(c -> assertThat(c).containsEntry("contype", "p").containsEntry("definition", "PRIMARY KEY (id)"));
        assertThat(constraints).filteredOn(c -> c.get("contype").equals("f")).hasSize(parentColumn == null ? 0 : 1);
        if (parentColumn != null) assertThat(constraints).anySatisfy(c ->
                assertThat(c.get("definition").toString()).contains("FOREIGN KEY (" + parentColumn + ")", "REFERENCES dormitory_" + (resource.equals("rooms") ? "buildings" : "rooms") + "(id)"));
        var indexes = jdbc.queryForList("SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND tablename = ?", String.class, table);
        assertThat(indexes).anyMatch(i -> i.contains("UNIQUE") && i.contains("lower(") && (parentColumn == null || i.contains(parentColumn)));
        assertThat(indexes).anyMatch(i -> i.contains(parentColumn == null ? "(status)" : "(" + parentColumn + ", status)"));
        UUID id = UUID.randomUUID();
        String code = UUID.randomUUID().toString().substring(0, 8);
        insert(table, parentColumn, parent, id, code);
        var row = jdbc.queryForMap("SELECT * FROM " + table + " WHERE id = ?", id);
        assertThat(row).containsEntry("status", "ACTIVE").containsEntry("row_version", 0L);
        assertThat(row.get("created_at")).isNotNull(); assertThat(row.get("updated_at")).isNotNull();
        state("23505", () -> insert(table, parentColumn, parent, id, "OTHER"));
        state("23505", () -> insert(table, parentColumn, parent, UUID.randomUUID(), code.toUpperCase(Locale.ROOT)));
        for (String column : types.keySet()) state("23502", () -> jdbc.update("UPDATE " + table + " SET " + column + " = NULL WHERE id = ?", id));
        if (parentColumn != null) state("23503", () -> jdbc.update("UPDATE " + table + " SET " + parentColumn + " = ? WHERE id = ?", UUID.randomUUID(), id));
        state("23514", () -> jdbc.update("UPDATE " + table + " SET status = 'UNKNOWN' WHERE id = ?", id));
        state("23514", () -> jdbc.update("UPDATE " + table + " SET row_version = -1 WHERE id = ?", id));
        for (String column : List.of("code", "name")) {
            for (String invalid : List.of("", "x", " ", "\t", "\n", "\r", "\u000b", "\f", " \t\nx\r\u000b\f")) {
                state("23514", () -> jdbc.update("UPDATE " + table + " SET " + column + " = ? WHERE id = ?", invalid, id));
            }
            int length = column.equals("code") ? 32 : 160;
            state("22001", () -> jdbc.update("UPDATE " + table + " SET " + column + " = ? WHERE id = ?", "😀".repeat(length + 1), id));
            jdbc.update("UPDATE " + table + " SET " + column + " = ? WHERE id = ?", "😀".repeat(length), id);
            jdbc.update("UPDATE " + table + " SET " + column + " = 'OK' WHERE id = ?", id);
        }
        jdbc.update("UPDATE " + table + " SET status = 'INACTIVE', row_version = 7 WHERE id = ?", id);
    }

    @Test void verifiesAuditColumnsDefaultsChecksForeignKeyIndexesAndWrites() {
        var columns = columns("dormitory_audit_events");
        var types = Map.of("id", "uuid", "actor_user_id", "uuid", "resource_type", "character varying", "target_id", "uuid",
                "action", "character varying", "resource_version", "bigint", "occurred_at", "timestamp with time zone", "metadata", "jsonb");
        assertThat(columns).hasSize(8);
        types.forEach((column, type) -> assertThat(columns.get(column)).containsEntry("data_type", type).containsEntry("is_nullable", "NO"));
        for (String column : List.of("resource_type", "action")) assertThat(columns.get(column).get("character_maximum_length")).isEqualTo(16);
        assertThat(columns.get("occurred_at").get("column_default").toString()).contains("CURRENT_TIMESTAMP");
        assertThat(columns.get("metadata").get("column_default").toString()).contains("'{}'::jsonb");
        for (String column : List.of("id", "actor_user_id", "resource_type", "target_id", "action", "resource_version")) assertThat(columns.get(column).get("column_default")).isNull();
        assertThat(jdbc.queryForList("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid = 'dormitory_audit_events'::regclass AND contype = 'p'", String.class)).containsExactly("PRIMARY KEY (id)");
        assertThat(jdbc.queryForList("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid = 'dormitory_audit_events'::regclass AND contype = 'f'", String.class)).containsExactly("FOREIGN KEY (actor_user_id) REFERENCES identity_users(id)");
        var indexes = jdbc.queryForList("SELECT indexdef FROM pg_indexes WHERE tablename = 'dormitory_audit_events' AND schemaname = 'public'", String.class);
        assertThat(indexes).anyMatch(i -> i.contains("(resource_type, target_id, occurred_at)")).anyMatch(i -> i.contains("(actor_user_id, occurred_at)"));
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO dormitory_audit_events (id, actor_user_id, resource_type, target_id, action, resource_version) VALUES (?, ?, 'BUILDING', ?, 'CREATED', 0)", id, actor, building);
        var row = jdbc.queryForMap("SELECT * FROM dormitory_audit_events WHERE id = ?", id);
        assertThat(row.get("metadata").toString()).isEqualTo("{}"); assertThat(row.get("occurred_at")).isNotNull();
        state("23505", () -> jdbc.update("INSERT INTO dormitory_audit_events SELECT * FROM dormitory_audit_events WHERE id = ?", id));
        for (String column : types.keySet()) state("23502", () -> jdbc.update("UPDATE dormitory_audit_events SET " + column + " = NULL WHERE id = ?", id));
        state("23503", () -> jdbc.update("UPDATE dormitory_audit_events SET actor_user_id = ? WHERE id = ?", UUID.randomUUID(), id));
        for (String column : List.of("resource_type", "action")) {
            state("23514", () -> jdbc.update("UPDATE dormitory_audit_events SET " + column + " = 'UNKNOWN' WHERE id = ?", id));
            state("22001", () -> jdbc.update("UPDATE dormitory_audit_events SET " + column + " = ? WHERE id = ?", "X".repeat(17), id));
        }
        state("23514", () -> jdbc.update("UPDATE dormitory_audit_events SET resource_version = -1 WHERE id = ?", id));
        for (String invalid : List.of("[]", "null", "true", "1", "\"text\"")) state("23514", () -> jdbc.update("UPDATE dormitory_audit_events SET metadata = ?::jsonb WHERE id = ?", invalid, id));
        state("22P02", () -> jdbc.update("UPDATE dormitory_audit_events SET metadata = ?::jsonb WHERE id = ?", "{", id));
        for (String resource : List.of("BUILDING", "ROOM", "BED")) for (String action : List.of("CREATED", "UPDATED")) {
            jdbc.update("UPDATE dormitory_audit_events SET resource_type = ?, action = ?, metadata = ?::jsonb WHERE id = ?", resource, action, "{\"status\":\"" + "漢".repeat(5000) + "\"}", id);
        }
    }

    @Test void validatesAllProductionEntitiesOnExactUpgradedSchemaWithoutFlyway() {
        var before = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class, FlywayAutoConfiguration.class))
                .withUserConfiguration(ValidationConfiguration.class)
                .withPropertyValues("spring.datasource.url=" + postgres.getJdbcUrl(), "spring.datasource.username=" + postgres.getUsername(),
                        "spring.datasource.password=" + postgres.getPassword(), "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.properties.hibernate.default_schema=public")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(EntityManagerFactory.class).doesNotHaveBean(Flyway.class);
                    assertThat(context.getBean(EntityManagerFactory.class).getMetamodel().getEntities()).hasSize(20);
                    assertThat(context.getBean(EntityManagerFactory.class).getMetamodel().entity(ResidenceBedEntity.class)).isNotNull();
                });
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank")).isEqualTo(before);
        assertThat(snapshot()).isEqualTo(legacy);
    }

    @Configuration(proxyBeanMethods = false) @EntityScan({
            "com.campus.identity.infrastructure.persistence", "com.campus.organization.infrastructure.persistence",
            "com.campus.student.infrastructure.persistence", "com.campus.personnel.infrastructure.persistence",
            "com.campus.shared.infrastructure.persistence", "com.campus.academic.infrastructure.persistence",
            "com.campus.dormitory.infrastructure.persistence"}) static class ValidationConfiguration { }
    private static Flyway flyway(String target) { return Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).locations("classpath:db/migration").target(target).load(); }
    private static Map<String, List<Map<String, Object>>> snapshot() {
        var result = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (String table : jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history' AND tablename NOT LIKE 'dormitory_%' ORDER BY tablename", String.class)) {
            result.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY 1"));
        }
        return result;
    }
    private Map<String, Map<String, Object>> columns(String table) {
        var result = new LinkedHashMap<String, Map<String, Object>>();
        for (var row : jdbc.queryForList("SELECT column_name, data_type, character_maximum_length, is_nullable, column_default FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ?", table)) result.put(row.get("column_name").toString(), row);
        return result;
    }
    private void insert(String table, String parentColumn, UUID parent, UUID id, String code) {
        if (parentColumn == null) jdbc.update("INSERT INTO " + table + " (id, code, name) VALUES (?, ?, 'Valid')", id, code);
        else jdbc.update("INSERT INTO " + table + " (id, code, name, " + parentColumn + ") VALUES (?, ?, 'Valid', ?)", id, code, parent);
    }
    private void state(String expected, Runnable operation) {
        Throwable failure = catchThrowable(operation::run); assertThat(failure).isNotNull();
        while (failure.getCause() != null) failure = failure.getCause();
        assertThat(failure).isInstanceOf(SQLException.class); assertThat(((SQLException) failure).getSQLState()).isEqualTo(expected);
    }
}
