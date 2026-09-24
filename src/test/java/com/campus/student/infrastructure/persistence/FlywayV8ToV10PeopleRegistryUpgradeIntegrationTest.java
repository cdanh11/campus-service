package com.campus.student.infrastructure.persistence;

import java.util.List;
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
class FlywayV8ToV10PeopleRegistryUpgradeIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6-alpine");
    static JdbcTemplate jdbc;
    static UUID userId;
    static UUID unitId;
    static UUID studentId;
    static UUID memberId;

    @BeforeAll
    static void upgrade() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        assertThat(flyway("8").migrate().migrationsExecuted).isEqualTo(8);
        userId = UUID.randomUUID(); unitId = UUID.randomUUID(); studentId = UUID.randomUUID(); memberId = UUID.randomUUID();
        jdbc.update("INSERT INTO identity_users (id, email, display_name, password_hash, status) VALUES (?, ?, 'Linked User', 'legacy-hash', 'ACTIVE')", userId, "linked@campus.example");
        jdbc.update("INSERT INTO organization_units (id, code, name, unit_type, status) VALUES (?, 'ENG', 'Engineering', 'FACULTY', 'ACTIVE')", unitId);
        jdbc.update("INSERT INTO students (id, student_number, full_name, organization_unit_id, status) VALUES (?, 'S001', 'Legacy Student', ?, 'ACTIVE')", studentId, unitId);
        jdbc.update("INSERT INTO faculty_staff (id, personnel_number, full_name, personnel_type, organization_unit_id, status) VALUES (?, 'P001', 'Legacy Staff', 'STAFF', ?, 'ACTIVE')", memberId, unitId);
        assertThat(flyway("10").migrate().migrationsExecuted).isEqualTo(2);
    }

    @Test
    void upgradesLegacyRecordsAndEnforcesOptionalOneToOneIdentityLinks() {
        assertThat(jdbc.queryForObject("SELECT identity_user_id FROM students WHERE id = ?", UUID.class, studentId)).isNull();
        assertThat(jdbc.queryForObject("SELECT identity_user_id FROM faculty_staff WHERE id = ?", UUID.class, memberId)).isNull();
        jdbc.update("UPDATE students SET identity_user_id = ? WHERE id = ?", userId, studentId);
        jdbc.update("UPDATE faculty_staff SET identity_user_id = ? WHERE id = ?", userId, memberId);
        assertThat(indexes("students")).contains("ux_students_student_number_lower", "ux_students_identity_user_id");
        assertThat(indexes("faculty_staff")).contains("ux_faculty_staff_personnel_number_lower", "ux_faculty_staff_identity_user_id");
        assertSqlState("23505", () -> jdbc.update("INSERT INTO students (id, student_number, full_name, identity_user_id, organization_unit_id, status) VALUES (?, 'S002', 'Other Student', ?, ?, 'ACTIVE')", UUID.randomUUID(), userId, unitId));
        assertSqlState("23503", () -> jdbc.update("UPDATE students SET identity_user_id = ? WHERE id = ?", UUID.randomUUID(), studentId));
        assertSqlState("23505", () -> jdbc.update("INSERT INTO organization_units (id, code, name, unit_type, status) VALUES (?, 'eng', 'Duplicate', 'FACULTY', 'ACTIVE')", UUID.randomUUID()));
    }

    @Test
    void hibernateValidatesExactlyTheUpgradedSchemaWithoutFlyway() {
        List<java.util.Map<String, Object>> history = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class, FlywayAutoConfiguration.class))
                .withUserConfiguration(ValidationConfiguration.class)
                .withPropertyValues("spring.datasource.url=" + postgres.getJdbcUrl(), "spring.datasource.username=" + postgres.getUsername(),
                        "spring.datasource.password=" + postgres.getPassword(), "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.properties.hibernate.default_schema=public")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(EntityManagerFactory.class).doesNotHaveBean(Flyway.class);
                    assertThat(context.getBean(EntityManagerFactory.class).getMetamodel().getEntities()).hasSizeGreaterThanOrEqualTo(8);
                });
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank")).isEqualTo(history);
    }

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = {UserAccountEntity.class, OrganizationUnitEntity.class, StudentEntity.class, FacultyStaffEntity.class})
    static class ValidationConfiguration { }

    private static Flyway flyway(String target) {
        var configuration = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).locations("classpath:db/migration");
        if (target != null) configuration.target(target);
        return configuration.load();
    }
    private List<String> indexes(String table) { return jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE schemaname = 'public' AND tablename = ?", String.class, table); }
    private void assertSqlState(String expected, Runnable operation) {
        Throwable failure = catchThrowable(operation::run); assertThat(failure).isNotNull();
        while (failure.getCause() != null) failure = failure.getCause();
        assertThat(failure).isInstanceOf(java.sql.SQLException.class);
        assertThat(((java.sql.SQLException) failure).getSQLState()).isEqualTo(expected);
    }
}
