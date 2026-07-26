package org.gtalent;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseSchemaInitializerTest {

    @Test
    void initializesAllTablesAndIsIdempotent() throws Exception {
        String url = "jdbc:h2:mem:schema-test;DB_CLOSE_DELAY=-1";
        DatabaseSchemaInitializer.ConnectionProvider provider =
                () -> DriverManager.getConnection(url, "sa", "");

        DatabaseSchemaInitializer.initialize(provider);
        DatabaseSchemaInitializer.initialize(provider);

        try (Connection conn = provider.getConnection();
             ResultSet rs = conn.createStatement().executeQuery(
                     "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
                             "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME IN " +
                             "('STOCK_DATA', 'STOCK_UNIVERSE', 'INSTITUTIONAL_DATA', 'SCAN_HISTORY', " +
                             "'MARKET_BREADTH', 'MONTHLY_REVENUE', 'FINMIND_SHAREHOLDING', " +
                             "'FINMIND_DAY_TRADING', 'APP_META', 'FINANCIAL_QUARTER_DATA')")) {
            rs.next();
            assertEquals(10, rs.getInt(1));
        }
    }

    @Test
    void failsFastWhenConnectionCannotBeCreated() {
        assertThrows(IllegalStateException.class,
                () -> DatabaseSchemaInitializer.initialize(
                        () -> {
                            throw new java.sql.SQLException("connection unavailable");
                        }));
    }

    @Test
    void flywayBaselinesAndMigratesAnExistingSchema() throws Exception {
        String url = "jdbc:h2:mem:existing-schema;DB_CLOSE_DELAY=-1";
        DatabaseSchemaInitializer.initialize(() -> DriverManager.getConnection(url, "sa", ""));
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(url);
        dataSource.setUser("sa");

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/h2")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();

        assertEquals(1, flyway.migrate().migrationsExecuted);
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.createStatement().executeQuery(
                     "SELECT \"success\" FROM \"flyway_schema_history\" " +
                             "WHERE \"version\" = '1'")) {
            assertTrue(rs.next());
            assertTrue(rs.getBoolean(1));
        }
    }

    @Test
    void postgresqlBaselineHasPortableSqlSyntax() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:postgresql-baseline;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/postgresql")
                .load();

        assertEquals(1, flyway.migrate().migrationsExecuted);
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.createStatement().executeQuery(
                     "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
                             "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME IN " +
                             "('STOCK_DATA', 'STOCK_UNIVERSE', 'INSTITUTIONAL_DATA', 'SCAN_HISTORY', " +
                             "'MARKET_BREADTH', 'MONTHLY_REVENUE', 'FINMIND_SHAREHOLDING', " +
                             "'FINMIND_DAY_TRADING', 'APP_META', 'FINANCIAL_QUARTER_DATA')")) {
            rs.next();
            assertEquals(10, rs.getInt(1));
        }
    }
}
