package com.campus.testsupport;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Provisions a throwaway database migrated to the latest schema so entity validation is not
 * coupled to the historical version under test.
 */
public final class LatestSchemaDatabase {
    private LatestSchemaDatabase() { }

    public static String migrate(PostgreSQLContainer<?> postgres) {
        String name = "validation_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
        } catch (SQLException failure) {
            throw new IllegalStateException("Cannot create validation database", failure);
        }
        String url = "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/" + name;
        Flyway.configure().dataSource(url, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").load().migrate();
        return url;
    }
}
