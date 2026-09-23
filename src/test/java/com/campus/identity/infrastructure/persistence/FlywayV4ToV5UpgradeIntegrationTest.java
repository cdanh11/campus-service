package com.campus.identity.infrastructure.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.campus.identity.infrastructure.persistence.entity.UserAccountEntity;
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
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@Testcontainers
class FlywayV4ToV5UpgradeIntegrationTest {

    private static final String WHITESPACE = " \t\n\r\u000B\f";
    private static final List<LegacyUser> LEGACY_USERS = new ArrayList<>();
    private static JdbcTemplate jdbc;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6-alpine");

    @BeforeAll
    static void upgradeFromV4() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        assertThat(flyway("4").migrate().migrationsExecuted).isEqualTo(4);

        legacy("normal@campus.example", "normal@campus.example");
        legacy("", null);
        legacy("x", null);
        for (char whitespace : WHITESPACE.toCharArray()) {
            legacy(String.valueOf(whitespace), null);
        }
        legacy(WHITESPACE, null);
        legacy(WHITESPACE + "x" + WHITESPACE, null);
        legacy(WHITESPACE + "legacy@campus.example" + WHITESPACE, "legacy@campus.example");
        legacy("mixed\tinside\nname", "mixed\tinside\nname");
        legacy("x".repeat(150), "x".repeat(100));
        legacy(" Nguyễn Văn An ", "Nguyễn Văn An");
        legacy("😀".repeat(110), "😀".repeat(100));
        legacy("vividv", "vividv");
        legacy("ab" + " ".repeat(98) + "z", "ab");
        legacy("q" + " ".repeat(99) + "z", null);

        Flyway v5 = flyway(null);
        assertThat(v5.migrate().migrationsExecuted).isEqualTo(1);
        v5.validate();
        assertThat(v5.info().applied()).extracting(info -> info.getVersion().getVersion())
                .containsExactly("1", "2", "3", "4", "5");
    }

    @Test
    void normalizesLegacyNamesWithoutChangingIdentityOrRoles() {
        for (LegacyUser user : LEGACY_USERS) {
            Map<String, Object> row = jdbc.queryForMap("SELECT * FROM identity_users WHERE id = ?", user.id());
            assertThat(row).containsEntry("id", user.id()).containsEntry("email", user.email())
                    .containsEntry("password_hash", user.passwordHash()).containsEntry("status", user.status())
                    .containsEntry("security_version", user.securityVersion()).containsEntry("row_version", 0L)
                    .containsEntry("display_name", user.expectedName());
            assertThat(jdbc.queryForList("""
                    SELECT r.code FROM identity_user_roles ur JOIN identity_roles r ON r.id = ur.role_id
                    WHERE ur.user_id = ? ORDER BY r.code
                    """, String.class, user.id())).containsExactlyElementsOf(user.roles());
        }
    }

    @Test
    void enforcesDisplayNameConstraintsAndRowVersionDefaultOnTheUpgradedSchema() {
        column("identity_users", "display_name", "character varying", 100, null);
        column("identity_users", "row_version", "bigint", null, "0");
        assertThat(constraints("identity_users", "c"))
                .anySatisfy(definition -> assertThat(definition).contains("display_name", "btrim"));

        for (String valid : List.of("ab", "x".repeat(100), "😀".repeat(100), "名姓", WHITESPACE + "ab" + WHITESPACE)) {
            UUID id = insertUser(valid);
            assertThat(jdbc.queryForObject("SELECT row_version FROM identity_users WHERE id = ?", Long.class, id)).isZero();
        }
        sqlState("23502", () -> insertUser(null));
        for (String invalid : List.of("", "a", WHITESPACE, WHITESPACE + "a" + WHITESPACE)) {
            sqlState("23514", () -> insertUser(invalid));
        }
        for (char whitespace : WHITESPACE.toCharArray()) {
            sqlState("23514", () -> insertUser(String.valueOf(whitespace).repeat(2)));
        }
        sqlState("22001", () -> insertUser("x".repeat(101)));
        sqlState("22001", () -> insertUser("😀".repeat(101)));
        UUID id = insertUser("Version test");
        sqlState("23502", () -> jdbc.update("UPDATE identity_users SET row_version = NULL WHERE id = ?", id));
        sqlState("23502", () -> jdbc.update("""
                INSERT INTO identity_users (id, email, display_name, password_hash, status, row_version)
                VALUES (?, ?, 'Valid name', 'fixture-hash', 'ACTIVE', NULL)
                """, UUID.randomUUID(), UUID.randomUUID() + "@campus.example"));
        jdbc.update("UPDATE identity_users SET row_version = ? WHERE id = ?", 42L, id);
        assertThat(jdbc.queryForObject("SELECT row_version FROM identity_users WHERE id = ?", Long.class, id)).isEqualTo(42L);
    }

    @Test
    void seedsAndConstrainsTheSingletonGuard() {
        column("identity_admin_guard", "guard_id", "smallint", null, null);
        assertThat(jdbc.queryForList("SELECT guard_id FROM identity_admin_guard", Short.class)).containsExactly((short) 1);
        assertThat(constraints("identity_admin_guard", "p")).containsExactly("PRIMARY KEY (guard_id)");
        assertThat(constraints("identity_admin_guard", "c")).containsExactly("CHECK ((guard_id = 1))");
        sqlState("23505", () -> jdbc.update("INSERT INTO identity_admin_guard VALUES (1)"));
        sqlState("23502", () -> jdbc.update("INSERT INTO identity_admin_guard VALUES (NULL)"));
        for (int invalid : List.of(-1, 0, 2)) {
            sqlState("23514", () -> jdbc.update("INSERT INTO identity_admin_guard VALUES (?)", invalid));
        }
    }

    @Test
    void productionGuardQueryLocksUntilTransactionCompletion() throws SQLException {
        try (Connection owner = postgres.createConnection(""); Connection contender = postgres.createConnection("")) {
            owner.setAutoCommit(false);
            contender.setAutoCommit(false);
            JdbcTemplate ownerJdbc = new JdbcTemplate(new SingleConnectionDataSource(owner, true));
            JdbcTemplate contenderJdbc = new JdbcTemplate(new SingleConnectionDataSource(contender, true));
            AdminGuardPersistenceAdapter ownerGuard = new AdminGuardPersistenceAdapter(ownerJdbc);
            AdminGuardPersistenceAdapter contenderGuard = new AdminGuardPersistenceAdapter(contenderJdbc);
            try {
                for (boolean commit : List.of(true, false)) {
                    ownerJdbc.execute("SET LOCAL lock_timeout = '500ms'");
                    contenderJdbc.execute("SET LOCAL lock_timeout = '500ms'");
                    ownerGuard.lock();
                    sqlState("55P03", contenderGuard::lock);
                    contender.rollback();
                    if (commit) {
                        owner.commit();
                    } else {
                        owner.rollback();
                    }
                    contenderJdbc.execute("SET LOCAL lock_timeout = '500ms'");
                    contenderGuard.lock();
                    contender.commit();
                }
            } finally {
                owner.rollback();
                contender.rollback();
            }
        }
    }

    @Test
    void verifiesAuditSchemaDefaultsAndValidAndInvalidInserts() {
        String table = "identity_admin_audit_events";
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? ORDER BY ordinal_position
                """, String.class, table)).containsExactly("id", "actor_user_id", "target_user_id", "action", "occurred_at", "metadata");
        for (String name : List.of("id", "actor_user_id", "target_user_id")) {
            column(table, name, "uuid", null, null);
        }
        column(table, "action", "character varying", 64, null);
        column(table, "occurred_at", "timestamp with time zone", null, null);
        column(table, "metadata", "character varying", 1000, "'{}'::character varying");
        assertThat(constraints(table, "p")).containsExactly("PRIMARY KEY (id)");
        assertThat(constraints(table, "f")).containsExactlyInAnyOrder(
                "FOREIGN KEY (actor_user_id) REFERENCES identity_users(id)",
                "FOREIGN KEY (target_user_id) REFERENCES identity_users(id)");
        assertThat(jdbc.queryForObject("""
                SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND tablename = ?
                AND indexname = 'ix_identity_admin_audit_target_occurred'
                """, String.class, table)).contains("(target_user_id, occurred_at)");

        UUID actor = LEGACY_USERS.get(0).id();
        UUID target = LEGACY_USERS.get(1).id();
        UUID defaultId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO identity_admin_audit_events (id, actor_user_id, target_user_id, action, occurred_at)
                VALUES (?, ?, ?, 'USER_CREATED', CURRENT_TIMESTAMP)
                """, defaultId, actor, target);
        assertThat(jdbc.queryForObject("SELECT metadata FROM identity_admin_audit_events WHERE id = ?", String.class, defaultId))
                .isEqualTo("{}");
        Timestamp occurredAt = Timestamp.from(Instant.parse("2026-09-22T10:00:00Z"));
        UUID explicitId = UUID.randomUUID();
        audit(explicitId, actor, target, "USER_CREATED", occurredAt, "{\"source\":\"migration-test\"}");
        assertThat(jdbc.queryForMap("SELECT * FROM identity_admin_audit_events WHERE id = ?", explicitId))
                .containsEntry("id", explicitId).containsEntry("actor_user_id", actor).containsEntry("target_user_id", target)
                .containsEntry("action", "USER_CREATED").containsEntry("occurred_at", occurredAt)
                .containsEntry("metadata", "{\"source\":\"migration-test\"}");
        audit(UUID.randomUUID(), actor, target, "x".repeat(64), occurredAt, "m".repeat(1000));
        sqlState("23505", () -> audit(explicitId, actor, target, "USER_CREATED", occurredAt, "{}"));
        sqlState("23503", () -> audit(UUID.randomUUID(), UUID.randomUUID(), target, "USER_CREATED", occurredAt, "{}"));
        sqlState("23503", () -> audit(UUID.randomUUID(), actor, UUID.randomUUID(), "USER_CREATED", occurredAt, "{}"));
        sqlState("22001", () -> audit(UUID.randomUUID(), actor, target, "x".repeat(65), occurredAt, "{}"));
        sqlState("22001", () -> audit(UUID.randomUUID(), actor, target, "USER_CREATED", occurredAt, "m".repeat(1001)));
        for (int index = 0; index < 6; index++) {
            Object[] values = {UUID.randomUUID(), actor, target, "USER_CREATED", occurredAt, "{}"};
            values[index] = null;
            sqlState("23502", () -> audit(values));
        }
    }

    @Test
    void hibernateValidatesTheExactUpgradedSchemaWithoutRunningFlyway() {
        List<Map<String, Object>> history = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,
                        HibernateJpaAutoConfiguration.class, FlywayAutoConfiguration.class))
                .withUserConfiguration(ValidationConfiguration.class)
                .withPropertyValues(
                        "spring.datasource.url=" + postgres.getJdbcUrl(),
                        "spring.datasource.username=" + postgres.getUsername(),
                        "spring.datasource.password=" + postgres.getPassword(),
                        "spring.flyway.enabled=false",
                        "spring.jpa.hibernate.ddl-auto=validate",
                        "spring.jpa.properties.hibernate.default_schema=public")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(EntityManagerFactory.class).doesNotHaveBean(Flyway.class);
                    EntityManagerFactory factory = context.getBean(EntityManagerFactory.class);
                    assertThat(factory.isOpen()).isTrue();
                    assertThat(factory.getMetamodel().getEntities()).hasSize(5);
                    assertThat(new JdbcTemplate(context.getBean(javax.sql.DataSource.class))
                            .queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank")).isEqualTo(history);
                });
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank")).isEqualTo(history);
    }

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = UserAccountEntity.class)
    static class ValidationConfiguration { }

    private static Flyway flyway(String target) {
        var configuration = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private static void legacy(String email, String expectedName) {
        int index = LEGACY_USERS.size();
        UUID id = UUID.randomUUID();
        String hash = "legacy-fixture-hash-" + index;
        String status = List.of("ACTIVE", "SUSPENDED", "DISABLED").get(index % 3);
        long securityVersion = index + 7L;
        List<String> roles = index % 2 == 0 ? List.of("ADMIN", "USER") : List.of("USER");
        jdbc.update("INSERT INTO identity_users (id, email, password_hash, status, security_version) VALUES (?, ?, ?, ?, ?)",
                id, email, hash, status, securityVersion);
        for (String role : roles) {
            jdbc.update("INSERT INTO identity_user_roles (user_id, role_id) SELECT ?, id FROM identity_roles WHERE code = ?", id, role);
        }
        LEGACY_USERS.add(new LegacyUser(id, email, hash, status, securityVersion, roles,
                expectedName == null ? "User " + id : expectedName));
    }

    private UUID insertUser(String displayName) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO identity_users (id, email, display_name, password_hash, status)
                VALUES (?, ?, ?, 'fixture-hash', 'ACTIVE')
                """, id, id + "@campus.example", displayName);
        return id;
    }

    private void column(String table, String name, String type, Integer length, String defaultValue) {
        assertThat(jdbc.queryForMap("""
                SELECT data_type, character_maximum_length, is_nullable, column_default FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """, table, name)).containsEntry("data_type", type).containsEntry("character_maximum_length", length)
                .containsEntry("is_nullable", "NO").containsEntry("column_default", defaultValue);
    }

    private List<String> constraints(String table, String type) {
        return jdbc.queryForList("""
                SELECT pg_get_constraintdef(oid) FROM pg_constraint
                WHERE conrelid = CAST(? AS regclass) AND contype = CAST(? AS "char")
                """, String.class, "public." + table, type);
    }

    private void audit(Object... values) {
        jdbc.update("""
                INSERT INTO identity_admin_audit_events (id, actor_user_id, target_user_id, action, occurred_at, metadata)
                VALUES (?, ?, ?, ?, ?, ?)
                """, values);
    }

    private void sqlState(String expected, Runnable operation) {
        Throwable failure = catchThrowable(operation::run);
        assertThat(failure).as("Expected SQLSTATE %s", expected).isNotNull();
        while (failure.getCause() != null) {
            failure = failure.getCause();
        }
        assertThat(failure).isInstanceOf(SQLException.class);
        assertThat(((SQLException) failure).getSQLState()).isEqualTo(expected);
    }

    private record LegacyUser(UUID id, String email, String passwordHash, String status,
                              long securityVersion, List<String> roles, String expectedName) { }
}
