package com.campus.academic.infrastructure.persistence;

import java.sql.SQLException;
import java.util.*;
import com.campus.academic.infrastructure.persistence.delivery.AcademicTermEntity;
import com.campus.academic.infrastructure.persistence.delivery.CourseOfferingEntity;
import com.campus.academic.infrastructure.persistence.delivery.ClassSectionEntity;
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
class FlywayV13ToV16AcademicDeliveryUpgradeIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6-alpine");
    static JdbcTemplate jdbc;
    static UUID unit = UUID.randomUUID(), course = UUID.randomUUID(), faculty = UUID.randomUUID();
    static Map<String, List<Map<String, Object>>> legacy;
    static List<Map<String, Object>> history;
    static final List<String> LEGACY = List.of("identity_users", "identity_roles", "identity_user_roles", "organization_units",
            "students", "faculty_staff", "academic_programs", "academic_courses", "people_registry_audit_events");

    @BeforeAll
    static void upgrade() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        assertThat(flyway("13").migrate().migrationsExecuted).isEqualTo(13);
        UUID user = UUID.randomUUID(), student = UUID.randomUUID();
        jdbc.update("INSERT INTO identity_users (id, email, display_name, password_hash, status, security_version, row_version) VALUES (?, 'upgrade@campus.example', 'Upgrade User', 'legacy-hash', 'ACTIVE', 9, 3)", user);
        jdbc.update("INSERT INTO identity_user_roles (user_id, role_id) VALUES (?, '00000000-0000-0000-0000-000000000002')", user);
        jdbc.update("INSERT INTO organization_units (id, code, name, unit_type, status, row_version) VALUES (?, 'ENG', 'Engineering', 'FACULTY', 'ACTIVE', 3)", unit);
        jdbc.update("INSERT INTO students (id, student_number, full_name, identity_user_id, organization_unit_id, row_version) VALUES (?, 'S001', 'Legacy Student', ?, ?, 4)", student, user, unit);
        jdbc.update("INSERT INTO faculty_staff (id, personnel_number, full_name, personnel_type, organization_unit_id, row_version) VALUES (?, 'F001', 'Legacy Faculty', 'FACULTY', ?, 2)", faculty, unit);
        jdbc.update("INSERT INTO academic_programs (id, code, name, organization_unit_id, status, row_version) VALUES (?, 'PR01', 'Program', ?, 'INACTIVE', 5)", UUID.randomUUID(), unit);
        jdbc.update("INSERT INTO academic_courses (id, code, title, credits, organization_unit_id, row_version) VALUES (?, 'CR01', 'Course', 3, ?, 7)", course, unit);
        jdbc.update("INSERT INTO people_registry_audit_events (id, actor_user_id, resource_type, target_id, action, occurred_at) VALUES (?, ?, 'STUDENT', ?, 'CREATED', now())", UUID.randomUUID(), user, student);
        legacy = snapshot(); history = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        assertThat(flyway("16").migrate().migrationsExecuted).isEqualTo(3);
    }

    @Test
    void preservesAllLegacyDataAndHistoryAndAppliesExactlyThreeMigrations() {
        assertThat(snapshot()).isEqualTo(legacy);
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history WHERE installed_rank <= 13 ORDER BY installed_rank")).isEqualTo(history);
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE installed_rank > 13 ORDER BY installed_rank", String.class)).containsExactly("14", "15", "16");
        assertThat(flyway("16").validateWithResult().validationSuccessful).isTrue();
    }

    @ParameterizedTest @ValueSource(strings = {"academic_terms", "academic_course_offerings", "academic_class_sections"})
    void verifiesEveryColumnDefaultForeignKeyAndIndex(String table) {
        Map<String, Map<String, Object>> columns = new HashMap<>();
        jdbc.queryForList("SELECT column_name, data_type, character_maximum_length, is_nullable, column_default FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ?", table)
                .forEach(row -> columns.put((String) row.get("column_name"), row));
        Map<String, String> expected = new LinkedHashMap<>(Map.of("id", "uuid", "status", "character varying", "row_version", "bigint",
                "created_at", "timestamp with time zone", "updated_at", "timestamp with time zone"));
        if (table.equals("academic_terms")) expected.putAll(Map.of("code", "character varying", "name", "character varying", "start_date", "date", "end_date", "date"));
        else if (table.equals("academic_course_offerings")) expected.putAll(Map.of("term_id", "uuid", "course_id", "uuid", "organization_unit_id", "uuid"));
        else expected.putAll(Map.of("offering_id", "uuid", "code", "character varying", "capacity", "integer", "faculty_id", "uuid"));
        assertThat(columns.keySet()).containsExactlyInAnyOrderElementsOf(expected.keySet());
        expected.forEach((name, type) -> {
            Integer length = Map.of("code", 32, "name", 160, "status", 20).get(name);
            assertThat(columns.get(name)).containsEntry("data_type", type).containsEntry("character_maximum_length", length)
                    .containsEntry("is_nullable", name.equals("faculty_id") ? "YES" : "NO");
        });
        assertThat(columns.get("row_version").get("column_default")).isEqualTo("0");
        assertThat(columns.get("status").get("column_default")).isEqualTo(table.equals("academic_terms") ? "'PLANNED'::character varying" : "'DRAFT'::character varying");
        for (String name : List.of("created_at", "updated_at")) assertThat(columns.get(name).get("column_default")).isEqualTo("CURRENT_TIMESTAMP");
        var definitions = jdbc.queryForList("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid = ?::regclass", String.class, table);
        assertThat(definitions).contains("PRIMARY KEY (id)");
        if (table.equals("academic_course_offerings")) assertThat(definitions).contains("FOREIGN KEY (term_id) REFERENCES academic_terms(id)",
                "FOREIGN KEY (course_id) REFERENCES academic_courses(id)", "FOREIGN KEY (organization_unit_id) REFERENCES organization_units(id)", "UNIQUE (term_id, course_id)");
        if (table.equals("academic_class_sections")) assertThat(definitions).contains("FOREIGN KEY (offering_id) REFERENCES academic_course_offerings(id)", "FOREIGN KEY (faculty_id) REFERENCES faculty_staff(id)");
        var indexes = jdbc.queryForList("SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND tablename = ?", String.class, table);
        if (table.equals("academic_terms")) assertThat(indexes).anySatisfy(index -> assertThat(index).contains("CREATE UNIQUE INDEX ux_academic_terms_code_lower", "lower((code)::text)"))
                .anySatisfy(index -> assertThat(index).contains("ix_academic_terms_status_start", "(status, start_date)"));
        if (table.equals("academic_course_offerings")) {
            for (String part : List.of("(term_id, status)", "(course_id)", "(organization_unit_id)")) assertThat(indexes).anyMatch(index -> index.contains(part));
        }
        if (table.equals("academic_class_sections")) assertThat(indexes).anySatisfy(index -> assertThat(index).contains("CREATE UNIQUE INDEX ux_academic_section_offering_code_lower", "(offering_id, lower((code)::text))"))
                .anySatisfy(index -> assertThat(index).contains("(offering_id, status)"))
                .anySatisfy(index -> assertThat(index).contains("(faculty_id)"));
    }

    @Test
    void exercisesTermDatesUniquenessTextBoundariesDefaultsAndNullability() {
        UUID id = term("T001");
        defaults("academic_terms", id, "PLANNED");
        state("23505", () -> term("t001"));
        state("23505", () -> jdbc.update("INSERT INTO academic_terms (id, code, name, start_date, end_date) VALUES (?, 'OTHER', 'Term', DATE '2027-01-01', DATE '2027-01-01')", id));
        state("23514", () -> jdbc.update("UPDATE academic_terms SET end_date = DATE '2026-01-01' WHERE id = ?", id));
        state("23514", () -> jdbc.update("UPDATE academic_terms SET status = 'UNKNOWN' WHERE id = ?", id));
        for (String invalid : List.of("", "x", " \t\n\r\u000b\f", " x ")) for (String column : List.of("code", "name")) {
            state("23514", () -> jdbc.update("UPDATE academic_terms SET " + column + " = ? WHERE id = ?", invalid, id));
        }
        state("22001", () -> jdbc.update("UPDATE academic_terms SET code = ? WHERE id = ?", "X".repeat(33), id));
        state("22001", () -> jdbc.update("UPDATE academic_terms SET name = ? WHERE id = ?", "😀".repeat(161), id));
        jdbc.update("UPDATE academic_terms SET code = ?, name = ?, end_date = start_date WHERE id = ?", "X".repeat(32), "😀".repeat(160), id);
        required("academic_terms", id, List.of("id", "code", "name", "start_date", "end_date", "status", "row_version", "created_at", "updated_at"));
    }

    @Test
    void exercisesOfferingReferencesDefaultsScopedUniquenessAndNullability() {
        UUID term = term("O001"), id = offering(term);
        defaults("academic_course_offerings", id, "DRAFT");
        state("23505", () -> offering(term));
        for (String column : List.of("term_id", "course_id", "organization_unit_id")) state("23503", () -> jdbc.update("UPDATE academic_course_offerings SET " + column + " = ? WHERE id = ?", UUID.randomUUID(), id));
        state("23514", () -> jdbc.update("UPDATE academic_course_offerings SET status = 'UNKNOWN' WHERE id = ?", id));
        required("academic_course_offerings", id, List.of("id", "term_id", "course_id", "organization_unit_id", "status", "row_version", "created_at", "updated_at"));
    }

    @Test
    void exercisesSectionCapacityOptionalFacultyAndApprovedConstraints() {
        UUID offering = offering(term("S001")), id = UUID.randomUUID();
        jdbc.update("INSERT INTO academic_class_sections (id, offering_id, code, capacity) VALUES (?, ?, 'CS01', 1)", id, offering);
        defaults("academic_class_sections", id, "DRAFT");
        assertThat(jdbc.queryForObject("SELECT faculty_id FROM academic_class_sections WHERE id = ?", UUID.class, id)).isNull();
        state("23505", () -> jdbc.update("INSERT INTO academic_class_sections (id, offering_id, code, capacity) VALUES (?, ?, 'cs01', 30)", UUID.randomUUID(), offering));
        state("23514", () -> jdbc.update("UPDATE academic_class_sections SET status = 'OPEN' WHERE id = ?", id));
        for (int capacity : List.of(0, -1)) state("23514", () -> jdbc.update("UPDATE academic_class_sections SET capacity = ? WHERE id = ?", capacity, id));
        for (String column : List.of("offering_id", "faculty_id")) state("23503", () -> jdbc.update("UPDATE academic_class_sections SET " + column + " = ? WHERE id = ?", UUID.randomUUID(), id));
        state("23514", () -> jdbc.update("UPDATE academic_class_sections SET code = ? WHERE id = ?", " \t\n\r\u000b\f", id));
        state("22001", () -> jdbc.update("UPDATE academic_class_sections SET code = ? WHERE id = ?", "X".repeat(33), id));
        state("23514", () -> jdbc.update("UPDATE academic_class_sections SET status = 'UNKNOWN' WHERE id = ?", id));
        jdbc.update("UPDATE academic_class_sections SET faculty_id = ?, status = 'OPEN', capacity = ?, code = ? WHERE id = ?", faculty, Integer.MAX_VALUE, "X".repeat(32), id);
        required("academic_class_sections", id, List.of("id", "offering_id", "code", "capacity", "status", "row_version", "created_at", "updated_at"));
        UUID other = offering(term("S002"));
        jdbc.update("INSERT INTO academic_class_sections (id, offering_id, code, capacity) VALUES (?, ?, ?, 1)", UUID.randomUUID(), other, "X".repeat(32));
    }

    @Test
    void validatesAllProductionEntitiesAgainstExactlyTheUpgradedSchemaWithoutFlyway() {
        var before = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class, FlywayAutoConfiguration.class))
                .withUserConfiguration(ValidationConfiguration.class)
                .withPropertyValues("spring.datasource.url=" + postgres.getJdbcUrl(), "spring.datasource.username=" + postgres.getUsername(),
                        "spring.datasource.password=" + postgres.getPassword(), "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.properties.hibernate.default_schema=public")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(EntityManagerFactory.class).doesNotHaveBean(Flyway.class);
                    var model = context.getBean(EntityManagerFactory.class).getMetamodel();
                    assertThat(model.entity(AcademicTermEntity.class)).isNotNull();
                    assertThat(model.entity(CourseOfferingEntity.class)).isNotNull();
                    assertThat(model.entity(ClassSectionEntity.class)).isNotNull();
                    assertThat(model.getEntities()).hasSize(14);
                });
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank")).isEqualTo(before);
        assertThat(snapshot()).isEqualTo(legacy);
    }
    @Configuration(proxyBeanMethods = false) @EntityScan("com.campus")
    static class ValidationConfiguration { }
    private static Flyway flyway(String version) { return Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).locations("classpath:db/migration").target(version).load(); }
    private static Map<String, List<Map<String, Object>>> snapshot() {
        var result = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (String table : LEGACY) result.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY 1, 2"));
        return result;
    }
    private UUID term(String code) { UUID id = UUID.randomUUID(); jdbc.update("INSERT INTO academic_terms (id, code, name, start_date, end_date) VALUES (?, ?, 'Term', DATE '2027-01-01', DATE '2027-04-30')", id, code); return id; }
    private UUID offering(UUID term) { UUID id = UUID.randomUUID(); jdbc.update("INSERT INTO academic_course_offerings (id, term_id, course_id, organization_unit_id) VALUES (?, ?, ?, ?)", id, term, course, unit); return id; }
    private void defaults(String table, UUID id, String status) {
        var row = jdbc.queryForMap("SELECT * FROM " + table + " WHERE id = ?", id);
        assertThat(row).containsEntry("status", status).containsEntry("row_version", 0L);
        assertThat(row.get("created_at")).isNotNull(); assertThat(row.get("updated_at")).isNotNull();
    }
    private void required(String table, UUID id, List<String> columns) { for (String column : columns) state("23502", () -> jdbc.update("UPDATE " + table + " SET " + column + " = NULL WHERE id = ?", id)); }
    private void state(String expected, Runnable operation) {
        Throwable failure = catchThrowable(operation::run); assertThat(failure).isNotNull();
        while (failure.getCause() != null) failure = failure.getCause();
        assertThat(failure).isInstanceOf(SQLException.class); assertThat(((SQLException) failure).getSQLState()).isEqualTo(expected);
    }
}
