package com.campus.academic.infrastructure.persistence;

import java.sql.SQLException;
import java.util.*;
import com.campus.academic.infrastructure.persistence.entity.AcademicCourseEntity;
import com.campus.academic.infrastructure.persistence.entity.AcademicProgramEntity;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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
class FlywayV11ToV13AcademicCatalogUpgradeIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6-alpine");
    static JdbcTemplate jdbc;
    static UUID unit = UUID.randomUUID();
    static final List<String> LEGACY_TABLES = List.of("identity_users", "identity_roles", "identity_user_roles",
            "organization_units", "students", "faculty_staff", "people_registry_audit_events");
    static Map<String, List<Map<String, Object>>> legacy;
    static List<Map<String, Object>> legacyHistory;

    @BeforeAll
    static void upgrade() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        assertThat(flyway("11").migrate().migrationsExecuted).isEqualTo(11);
        UUID actor = UUID.randomUUID();
        UUID student = UUID.randomUUID();
        jdbc.update("INSERT INTO identity_users (id, email, display_name, password_hash, status, security_version, row_version) VALUES (?, 'legacy@campus.example', 'Legacy User', 'legacy-hash', 'SUSPENDED', 7, 3)", actor);
        jdbc.update("INSERT INTO identity_user_roles (user_id, role_id) VALUES (?, '00000000-0000-0000-0000-000000000002')", actor);
        jdbc.update("INSERT INTO organization_units (id, code, name, unit_type, status, row_version) VALUES (?, 'ENG', 'Engineering', 'FACULTY', 'ACTIVE', 4)", unit);
        jdbc.update("INSERT INTO students (id, student_number, full_name, email, identity_user_id, organization_unit_id, status, row_version) VALUES (?, 'S001', 'Legacy Student', 'student@campus.example', ?, ?, 'INACTIVE', 2)", student, actor, unit);
        jdbc.update("INSERT INTO faculty_staff (id, personnel_number, full_name, personnel_type, identity_user_id, organization_unit_id, status, row_version) VALUES (?, 'P001', 'Legacy Staff', 'STAFF', ?, ?, 'ACTIVE', 5)", UUID.randomUUID(), actor, unit);
        jdbc.update("INSERT INTO people_registry_audit_events (id, actor_user_id, resource_type, target_id, action, occurred_at, metadata) VALUES (?, ?, 'STUDENT', ?, 'CREATED', now(), '{legacy}')", UUID.randomUUID(), actor, student);
        legacy = snapshot();
        legacyHistory = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        assertThat(flyway("13").migrate().migrationsExecuted).isEqualTo(2);
    }

    @Test
    void preservesEveryLegacyFieldAndOriginalMigrationHistory() {
        assertThat(snapshot()).isEqualTo(legacy);
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history WHERE installed_rank <= 11 ORDER BY installed_rank")).isEqualTo(legacyHistory);
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE installed_rank > 11 ORDER BY installed_rank", String.class)).containsExactly("12", "13");
        assertThat(flyway("13").validateWithResult().validationSuccessful).isTrue();
    }

    @ParameterizedTest @ValueSource(strings = {"academic_programs", "academic_courses"})
    void definesExactColumnsDefaultsKeysAndIndexes(String table) {
        String text = textColumn(table);
        Map<String, Map<String, Object>> columns = new LinkedHashMap<>();
        jdbc.queryForList("SELECT column_name, data_type, character_maximum_length, is_nullable, column_default FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ?", table)
                .forEach(row -> columns.put((String) row.get("column_name"), row));
        List<String> names = new ArrayList<>(List.of("id", "code", text, "organization_unit_id", "status", "row_version", "created_at", "updated_at"));
        if (table.equals("academic_courses")) names.add("credits");
        assertThat(columns.keySet()).containsExactlyInAnyOrderElementsOf(names);
        for (String name : names) assertThat(columns.get(name)).containsEntry("is_nullable", "NO");
        column(columns, "id", "uuid", null);
        column(columns, "organization_unit_id", "uuid", null);
        column(columns, "code", "character varying", 32);
        column(columns, text, "character varying", 160);
        column(columns, "status", "character varying", 20);
        column(columns, "row_version", "bigint", null);
        column(columns, "created_at", "timestamp with time zone", null);
        column(columns, "updated_at", "timestamp with time zone", null);
        if (table.equals("academic_courses")) column(columns, "credits", "integer", null);
        assertThat(columns.get("status").get("column_default")).isEqualTo("'ACTIVE'::character varying");
        assertThat(columns.get("row_version").get("column_default")).isEqualTo("0");
        assertThat(columns.get("created_at").get("column_default")).isEqualTo("CURRENT_TIMESTAMP");
        assertThat(columns.get("updated_at").get("column_default")).isEqualTo("CURRENT_TIMESTAMP");
        Map<String, String> constraints = new HashMap<>();
        jdbc.query("SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid = ?::regclass",
                result -> { while (result.next()) constraints.put(result.getString(1), result.getString(2)); return null; }, table);
        assertThat(constraints).containsEntry(table + "_pkey", "PRIMARY KEY (id)")
                .containsEntry(table + "_code_key", "UNIQUE (code)")
                .containsEntry(table + "_organization_unit_id_fkey", "FOREIGN KEY (organization_unit_id) REFERENCES organization_units(id)");
        assertThat(constraints.keySet()).contains("ck_" + table + "_code_not_blank", "ck_" + table + "_" + text + "_not_blank", "ck_" + table + "_status");
        if (table.equals("academic_courses")) assertThat(constraints.keySet()).contains("ck_academic_courses_credits");
        var indexes = jdbc.queryForList("SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND tablename = ?", String.class, table);
        assertThat(indexes).anySatisfy(definition -> assertThat(definition).contains("CREATE UNIQUE INDEX ux_" + table + "_code_lower", "lower((code)::text)"))
                .anySatisfy(definition -> assertThat(definition).contains("ix_" + table + "_organization_unit_id", "(organization_unit_id)"));
    }

    @ParameterizedTest @ValueSource(strings = {"academic_programs", "academic_courses"})
    void acceptsBoundariesAndRejectsOnlyApprovedInvalidWrites(String table) {
        String text = textColumn(table);
        UUID id = UUID.randomUUID();
        String code = table.equals("academic_programs") ? "PR01" : "CR01";
        insert(table, id, code, "Valid", unit, 1);
        var row = jdbc.queryForMap("SELECT * FROM " + table + " WHERE id = ?", id);
        assertThat(row).containsEntry("status", "ACTIVE").containsEntry("row_version", 0L);
        assertThat(row.get("created_at")).isNotNull();
        assertThat(row.get("updated_at")).isNotNull();
        insert(table, UUID.randomUUID(), "X".repeat(32), "😀".repeat(160), unit, 30);
        insert(table, UUID.randomUUID(), "AB", " \t\n\r\u000b\fAB ", unit, 1);
        state("23505", () -> insert(table, id, "OTHER", "Valid", unit, 1));
        state("23505", () -> insert(table, UUID.randomUUID(), code.toLowerCase(Locale.ROOT), "Valid", unit, 1));
        state("23503", () -> insert(table, UUID.randomUUID(), "MISSING", "Valid", UUID.randomUUID(), 1));
        state("23503", () -> jdbc.update("DELETE FROM organization_units WHERE id = ?", unit));
        state("22001", () -> insert(table, UUID.randomUUID(), "X".repeat(33), "Valid", unit, 1));
        state("22001", () -> insert(table, UUID.randomUUID(), "LONG", "😀".repeat(161), unit, 1));
        for (String invalid : List.of("", "x", " \t\n\r\u000b\f", " \tx\n")) {
            state("23514", () -> jdbc.update("UPDATE " + table + " SET code = ? WHERE id = ?", invalid, id));
            state("23514", () -> jdbc.update("UPDATE " + table + " SET " + text + " = ? WHERE id = ?", invalid, id));
        }
        state("23514", () -> jdbc.update("UPDATE " + table + " SET status = 'INVALID' WHERE id = ?", id));
        List<String> required = new ArrayList<>(List.of("id", "code", text, "organization_unit_id", "status", "row_version", "created_at", "updated_at"));
        if (table.equals("academic_courses")) required.add("credits");
        for (String column : required) state("23502", () -> jdbc.update("UPDATE " + table + " SET " + column + " = NULL WHERE id = ?", id));
        if (table.equals("academic_courses")) {
            for (int credits : List.of(0, 31)) state("23514", () -> jdbc.update("UPDATE academic_courses SET credits = ? WHERE id = ?", credits, id));
        }
        jdbc.update("UPDATE " + table + " SET status = 'INACTIVE' WHERE id = ?", id);
        assertThat(jdbc.queryForObject("SELECT status FROM " + table + " WHERE id = ?", String.class, id)).isEqualTo("INACTIVE");
    }

    @Test
    void hibernateValidatesAllProductionEntitiesAgainstTheExactUpgradedSchemaWithoutFlyway() {
        var history = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class, FlywayAutoConfiguration.class))
                .withUserConfiguration(ValidationConfiguration.class)
                .withPropertyValues("spring.datasource.url=" + postgres.getJdbcUrl(), "spring.datasource.username=" + postgres.getUsername(),
                        "spring.datasource.password=" + postgres.getPassword(), "spring.flyway.enabled=false",
                        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.properties.hibernate.default_schema=public")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(EntityManagerFactory.class).doesNotHaveBean(Flyway.class);
                    var metamodel = context.getBean(EntityManagerFactory.class).getMetamodel();
                    assertThat(metamodel.entity(AcademicProgramEntity.class)).isNotNull();
                    assertThat(metamodel.entity(AcademicCourseEntity.class)).isNotNull();
                    assertThat(metamodel.getEntities()).hasSizeGreaterThanOrEqualTo(11);
                });
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank")).isEqualTo(history);
        assertThat(snapshot()).isEqualTo(legacy);
    }

    @Configuration(proxyBeanMethods = false)
    @EntityScan("com.campus")
    static class ValidationConfiguration { }

    private static Map<String, List<Map<String, Object>>> snapshot() {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (String table : LEGACY_TABLES) result.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY 1, 2"));
        return result;
    }
    private static Flyway flyway(String target) {
        return Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").target(target).load();
    }
    private String textColumn(String table) { return table.equals("academic_programs") ? "name" : "title"; }
    private void column(Map<String, Map<String, Object>> columns, String name, String type, Integer length) {
        assertThat(columns.get(name)).containsEntry("data_type", type).containsEntry("character_maximum_length", length);
    }
    private void insert(String table, UUID id, String code, String text, UUID owner, int credits) {
        if (table.equals("academic_programs")) {
            jdbc.update("INSERT INTO academic_programs (id, code, name, organization_unit_id) VALUES (?, ?, ?, ?)", id, code, text, owner);
        } else {
            jdbc.update("INSERT INTO academic_courses (id, code, title, organization_unit_id, credits) VALUES (?, ?, ?, ?, ?)", id, code, text, owner, credits);
        }
    }
    private void state(String expected, Runnable operation) {
        Throwable failure = catchThrowable(operation::run);
        assertThat(failure).isNotNull();
        while (failure.getCause() != null) failure = failure.getCause();
        assertThat(failure).isInstanceOf(SQLException.class);
        assertThat(((SQLException) failure).getSQLState()).isEqualTo(expected);
    }
}
