package org.gtalent;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class PostgresqlRepositoryIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("stockpredictor")
                    .withUsername("stockpredictor")
                    .withPassword("stockpredictor-test");

    @BeforeAll
    static void migrateSchema() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration/postgresql")
                .load()
                .migrate();
    }

    @BeforeEach
    void clearData() throws Exception {
        try (Connection connection = connect();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    TRUNCATE TABLE
                        APP_META,
                        FINANCIAL_QUARTER_DATA,
                        FINMIND_SHAREHOLDING,
                        FINMIND_DAY_TRADING,
                        INSTITUTIONAL_DATA,
                        MARKET_BREADTH,
                        MONTHLY_REVENUE,
                        STOCK_DATA,
                        STOCK_UNIVERSE
                    RESTART IDENTITY CASCADE
                    """);
        }
    }

    @Test
    void appMetaUpsertRunsOnPostgresql() {
        AppMetaRepository repository = new AppMetaRepository(
                PostgresqlRepositoryIntegrationTest::connect);

        repository.setMetaFlag("postgres-ready", "true");
        assertTrue(repository.isMetaFlagSet("postgres-ready"));

        repository.setMetaFlag("postgres-ready", "false");
        assertFalse(repository.isMetaFlagSet("postgres-ready"));
    }

    @Test
    void financialDataUpsertRunsOnPostgresql() {
        FinancialDataRepository repository = new FinancialDataRepository(
                PostgresqlRepositoryIntegrationTest::connect);

        repository.saveFinancialQuarterData("2330",
                new FinancialQuarterData("2026-06-30", 50, 40, 30, 45, 100));
        repository.saveFinancialQuarterData("2330",
                new FinancialQuarterData("2026-06-30", 60, 50, 40, 35, 200));

        FinancialQuarterData result = repository.getFinancialQuarterHistory("2330", 1).get(0);
        assertEquals(60.0, result.getGrossProfitMargin());
        assertEquals(200L, result.getContractLiabilities());
    }

    @Test
    void institutionalDataUpsertsRunOnPostgresql() {
        InstitutionalDataRepository repository = new InstitutionalDataRepository(
                PostgresqlRepositoryIntegrationTest::connect);

        InstitutionalTrade first = new InstitutionalTrade("2026-07-22", 100, 20, 10);
        InstitutionalTrade replacement = new InstitutionalTrade("2026-07-22", 300, 40, 20);
        assertEquals(1, repository.saveInstitutionalTrades("2330", List.of(first)));
        assertEquals(1, repository.saveInstitutionalTrades("2330", List.of(replacement)));

        InstitutionalTrade result = repository.getRecentInstitutionalTrades("2330", 1).get(0);
        assertEquals(300L, result.getForeignBuy());
        assertEquals(40L, result.getTrustBuy());
    }

    @Test
    void marketDataUpsertsRunOnPostgresql() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-23T00:00:00Z"), ZoneOffset.UTC);
        MarketDataRepository repository = new MarketDataRepository(
                PostgresqlRepositoryIntegrationTest::connect, clock);

        repository.saveDailyMarketBreadth(new MarketBreadthResult(50, 100, 50));
        repository.saveDailyMarketBreadth(new MarketBreadthResult(60, 120, 72));
        repository.saveRevenueData("2330", new RevenueData("2026-07", 100, 1, 2));
        repository.saveRevenueData("2330", new RevenueData("2026-07", 150, 3, 4));

        assertEquals(60.0, repository.getMarketBreadthHistory(1).get(0).getBreadth());
        assertEquals(150.0, repository.getRevenueHistory("2330", 1).get(0).getRevenue());
    }

    @Test
    void stockDataUpsertsRunOnPostgresql() {
        StockDataRepository repository = new StockDataRepository(
                PostgresqlRepositoryIntegrationTest::connect);

        assertTrue(repository.saveSimpleData("2330", "2026-07-22", "100"));
        repository.saveBulkOhlcvRow(
                "2330", "2026-07-22", "95", "110", "90", "105", "1000");

        StockDataPoint result = repository.getFullHistory("2330", 1).get(0);
        assertEquals(95.0, result.o);
        assertEquals(105.0, result.c);
        assertEquals(1_000L, result.volume);
    }

    @Test
    void stockUniverseUpsertRunsOnPostgresql() {
        StockUniverseRepository repository = new StockUniverseRepository(
                PostgresqlRepositoryIntegrationTest::connect);

        repository.saveStockUniverse(List.of(
                new StockUniverseEntry("2330", "台積電", "TWSE", "STOCK", false, true)));
        repository.saveStockUniverse(List.of(
                new StockUniverseEntry("2330", "台積電", "TWSE", "STOCK", false, false)));

        assertFalse(repository.getStockUniverseEntry("2330").isActive());
        assertEquals(0, repository.getStockUniverseCount());
    }

    @Test
    void migratesH2DataWithDryRunValidationAndRepeatableUpserts() throws Exception {
        String sourceUrl = "jdbc:h2:mem:migration-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(sourceUrl, "sa", "")
                .locations("classpath:db/migration/h2")
                .load()
                .migrate();
        try (Connection source = DriverManager.getConnection(sourceUrl, "sa", "");
             Statement statement = source.createStatement()) {
            statement.execute("""
                    INSERT INTO STOCK_DATA(
                        symbol, trade_date, open_price, high_price, low_price,
                        close_price, volume, price, foreign_buy, trust_buy, dealer_buy
                    ) VALUES (
                        '2330', DATE '2026-07-22', 100, 110, 95,
                        105, 1000, 105, 10, 20, 30
                    )
                    """);
            statement.execute("""
                    INSERT INTO SCAN_HISTORY(
                        id, symbol, scan_date, score, price_at_scan, rsi_at_scan
                    ) VALUES (42, '2330', DATE '2026-07-22', 88, 105, 55)
                    """);
            statement.execute("""
                    INSERT INTO APP_META(meta_key, meta_value)
                    VALUES ('migration-test', 'true')
                    """);
        }

        H2ToPostgresqlMigrator migrator = new H2ToPostgresqlMigrator(
                () -> DriverManager.getConnection(sourceUrl, "sa", ""),
                PostgresqlRepositoryIntegrationTest::connect,
                2);

        H2ToPostgresqlMigrator.MigrationReport dryRun = migrator.migrate(false);
        assertFalse(dryRun.executed());
        assertEquals(0, dryRun.targetAfter().get("STOCK_DATA").rows());

        H2ToPostgresqlMigrator.MigrationReport firstRun = migrator.migrate(true);
        assertEquals(1, firstRun.targetAfter().get("STOCK_DATA").rows());
        assertEquals("2026-07-22", firstRun.targetAfter().get("STOCK_DATA").minDate());
        assertEquals(42L, scalarLong("SELECT id FROM SCAN_HISTORY"));

        H2ToPostgresqlMigrator.MigrationReport secondRun = migrator.migrate(true);
        assertEquals(firstRun.targetAfter(), secondRun.targetAfter());
        assertEquals(1, scalarLong("SELECT COUNT(*) FROM STOCK_DATA"));
    }

    private static Connection connect() throws java.sql.SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static long scalarLong(String sql) throws Exception {
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             java.sql.ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }
}
