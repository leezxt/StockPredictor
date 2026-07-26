package org.gtalent;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Copies the application tables from H2 to PostgreSQL without modifying the source database.
 */
public final class H2ToPostgresqlMigrator {
    private static final List<TableSpec> TABLES = List.of(
            table("STOCK_DATA", "trade_date", "symbol",
                    keys("symbol", "trade_date"),
                    columns("symbol", "trade_date", "open_price", "high_price", "low_price",
                            "close_price", "volume", "price", "foreign_buy", "trust_buy", "dealer_buy")),
            table("STOCK_UNIVERSE", null, "symbol", keys("symbol"),
                    columns("symbol", "name", "market", "asset_type", "is_etf", "active", "last_synced_at")),
            table("INSTITUTIONAL_DATA", "trade_date", "symbol", keys("symbol", "trade_date"),
                    columns("symbol", "trade_date", "foreign_buy", "trust_buy", "dealer_buy")),
            table("SCAN_HISTORY", "scan_date", "symbol", keys("id"),
                    columns("id", "symbol", "scan_date", "score", "price_at_scan", "rsi_at_scan")),
            table("MARKET_BREADTH", "trade_date", null, keys("trade_date"),
                    columns("trade_date", "breadth", "eligible_count", "bullish_count", "created_at")),
            table("MONTHLY_REVENUE", null, "symbol", keys("symbol", "year_month"),
                    columns("symbol", "year_month", "revenue", "mom", "yoy", "created_at")),
            table("FINMIND_SHAREHOLDING", "trade_date", "symbol",
                    keys("symbol", "trade_date", "holding_factor"),
                    columns("symbol", "trade_date", "holding_factor", "shareholder_count",
                            "shares", "percentage", "created_at")),
            table("FINMIND_DAY_TRADING", "trade_date", "symbol", keys("symbol", "trade_date"),
                    columns("symbol", "trade_date", "buy_amount", "sell_amount",
                            "day_trading_volume", "day_trading_rate", "created_at")),
            table("APP_META", null, null, keys("meta_key"),
                    columns("meta_key", "meta_value", "updated_at")),
            table("FINANCIAL_QUARTER_DATA", "quarter_date", "symbol",
                    keys("symbol", "quarter_date"),
                    columns("symbol", "quarter_date", "gross_profit_margin", "operating_profit_margin",
                            "net_profit_margin", "inventory_turnover_days",
                            "contract_liabilities", "created_at"))
    );

    private final ConnectionProvider sourceConnections;
    private final ConnectionProvider targetConnections;
    private final int batchSize;

    public H2ToPostgresqlMigrator(
            ConnectionProvider sourceConnections,
            ConnectionProvider targetConnections,
            int batchSize) {
        this.sourceConnections = Objects.requireNonNull(sourceConnections);
        this.targetConnections = Objects.requireNonNull(targetConnections);
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be greater than zero");
        }
        this.batchSize = batchSize;
    }

    public MigrationReport migrate(boolean execute) throws SQLException {
        try (Connection source = sourceConnections.getConnection();
             Connection target = targetConnections.getConnection()) {
            requireDatabase(source, "H2");
            requireDatabase(target, "PostgreSQL");
            verifyTables(source, target);
            verifySourceKeys(source);

            Map<String, TableStats> sourceStats = collectStats(source);
            Map<String, TableStats> beforeStats = collectStats(target);
            if (!execute) {
                return new MigrationReport(false, sourceStats, beforeStats, beforeStats, Map.of());
            }

            target.setAutoCommit(false);
            Map<String, Long> written = new LinkedHashMap<>();
            Map<String, TableStats> afterStats;
            try {
                for (TableSpec table : TABLES) {
                    written.put(table.name(), copyTable(source, target, table));
                }
                resetScanHistorySequence(target);
                afterStats = collectStats(target);
                verifyPostMigration(sourceStats, afterStats);
                verifySamples(source, target);
                target.commit();
            } catch (Exception exception) {
                target.rollback();
                if (exception instanceof SQLException sqlException) {
                    throw sqlException;
                }
                throw new SQLException("Migration failed", exception);
            } finally {
                target.setAutoCommit(true);
            }

            return new MigrationReport(true, sourceStats, beforeStats, afterStats, written);
        }
    }

    private long copyTable(Connection source, Connection target, TableSpec table) throws SQLException {
        String selectSql = "SELECT " + String.join(", ", table.columns()) +
                " FROM " + table.name();
        String insertSql = buildPostgresqlUpsert(table);
        long written = 0;

        try (Statement select = source.createStatement();
             ResultSet rows = select.executeQuery(selectSql);
             PreparedStatement insert = target.prepareStatement(insertSql)) {
            ResultSetMetaData metadata = rows.getMetaData();
            while (rows.next()) {
                for (int index = 1; index <= metadata.getColumnCount(); index++) {
                    insert.setObject(index, rows.getObject(index));
                }
                insert.addBatch();
                written++;
                if (written % batchSize == 0) {
                    insert.executeBatch();
                }
            }
            if (written % batchSize != 0) {
                insert.executeBatch();
            }
        }
        return written;
    }

    private static String buildPostgresqlUpsert(TableSpec table) {
        StringJoiner placeholders = new StringJoiner(", ");
        table.columns().forEach(column -> placeholders.add("?"));

        List<String> updateColumns = table.columns().stream()
                .filter(column -> !table.keys().contains(column))
                .toList();
        String conflictAction;
        if (updateColumns.isEmpty()) {
            conflictAction = "DO NOTHING";
        } else {
            conflictAction = "DO UPDATE SET " + updateColumns.stream()
                    .map(column -> column + " = EXCLUDED." + column)
                    .reduce((left, right) -> left + ", " + right)
                    .orElseThrow();
        }
        return "INSERT INTO " + table.name() + " (" + String.join(", ", table.columns()) +
                ") VALUES (" + placeholders + ") ON CONFLICT (" +
                String.join(", ", table.keys()) + ") " + conflictAction;
    }

    private static void verifySourceKeys(Connection source) throws SQLException {
        for (TableSpec table : TABLES) {
            String keyList = String.join(", ", table.keys());
            String nullCondition = table.keys().stream()
                    .map(key -> key + " IS NULL")
                    .reduce((left, right) -> left + " OR " + right)
                    .orElseThrow();
            if (scalarLong(source, "SELECT COUNT(*) FROM " + table.name() +
                    " WHERE " + nullCondition) > 0) {
                throw new SQLException("Source contains null migration key in " +
                        table.name() + " (" + keyList + ")");
            }
            String sql = "SELECT " + keyList + ", COUNT(*) FROM " + table.name() +
                    " GROUP BY " + keyList + " HAVING COUNT(*) > 1";
            try (Statement statement = source.createStatement();
                 ResultSet duplicates = statement.executeQuery(sql)) {
                if (duplicates.next()) {
                    throw new SQLException("Source contains duplicate migration key in " +
                            table.name() + " (" + keyList + ")");
                }
            }
        }
    }

    private static Map<String, TableStats> collectStats(Connection connection) throws SQLException {
        Map<String, TableStats> stats = new LinkedHashMap<>();
        for (TableSpec table : TABLES) {
            long rows = scalarLong(connection, "SELECT COUNT(*) FROM " + table.name());
            long symbols = table.symbolColumn() == null ? 0 :
                    scalarLong(connection, "SELECT COUNT(DISTINCT " + table.symbolColumn() +
                            ") FROM " + table.name());
            String minDate = null;
            String maxDate = null;
            if (table.dateColumn() != null) {
                String sql = "SELECT MIN(" + table.dateColumn() + "), MAX(" +
                        table.dateColumn() + ") FROM " + table.name();
                try (Statement statement = connection.createStatement();
                     ResultSet result = statement.executeQuery(sql)) {
                    result.next();
                    minDate = Objects.toString(result.getObject(1), null);
                    maxDate = Objects.toString(result.getObject(2), null);
                }
            }
            stats.put(table.name(), new TableStats(rows, symbols, minDate, maxDate));
        }
        return stats;
    }

    private static long scalarLong(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private static void verifyPostMigration(
            Map<String, TableStats> source,
            Map<String, TableStats> target) throws SQLException {
        List<String> mismatches = new ArrayList<>();
        source.forEach((table, expected) -> {
            TableStats actual = target.get(table);
            if (!expected.equals(actual)) {
                mismatches.add(table + ": source=" + expected + ", target=" + actual);
            }
        });
        if (!mismatches.isEmpty()) {
            throw new SQLException("Post-migration validation failed: " +
                    String.join("; ", mismatches));
        }
    }

    private static void verifySamples(Connection source, Connection target) throws SQLException {
        for (TableSpec table : TABLES) {
            String orderBy = String.join(", ", table.keys());
            String select = "SELECT " + String.join(", ", table.columns()) +
                    " FROM " + table.name() + " ORDER BY " + orderBy + " LIMIT 1";
            try (Statement sourceStatement = source.createStatement();
                 Statement targetStatement = target.createStatement();
                 ResultSet sourceRow = sourceStatement.executeQuery(select);
                 ResultSet targetRow = targetStatement.executeQuery(select)) {
                boolean sourcePresent = sourceRow.next();
                boolean targetPresent = targetRow.next();
                if (sourcePresent != targetPresent) {
                    throw new SQLException("Sample presence mismatch in " + table.name());
                }
                if (!sourcePresent) {
                    continue;
                }
                for (int index = 1; index <= table.columns().size(); index++) {
                    String sourceValue = canonical(sourceRow.getObject(index));
                    String targetValue = canonical(targetRow.getObject(index));
                    if (!Objects.equals(sourceValue, targetValue)) {
                        throw new SQLException("Sample mismatch in " + table.name() +
                                "." + table.columns().get(index - 1) +
                                ": source=" + sourceValue + ", target=" + targetValue);
                    }
                }
            }
        }
    }

    private static String canonical(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        return Objects.toString(value, null);
    }

    private static void verifyTables(Connection source, Connection target) throws SQLException {
        for (TableSpec table : TABLES) {
            requireTable(source, table.name());
            requireTable(target, table.name());
        }
    }

    private static void requireTable(Connection connection, String table) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet result = metadata.getTables(null, null, table, new String[]{"TABLE"})) {
            if (!result.next()) {
                try (ResultSet lower = metadata.getTables(
                        null, null, table.toLowerCase(), new String[]{"TABLE"})) {
                    if (!lower.next()) {
                        throw new SQLException("Required table is missing: " + table);
                    }
                }
            }
        }
    }

    private static void requireDatabase(Connection connection, String expected) throws SQLException {
        String actual = connection.getMetaData().getDatabaseProductName();
        if (!actual.toLowerCase().contains(expected.toLowerCase())) {
            throw new SQLException("Expected " + expected + " but connected to " + actual);
        }
    }

    private static void resetScanHistorySequence(Connection target) throws SQLException {
        try (Statement statement = target.createStatement()) {
            statement.execute("""
                    SELECT setval(
                        pg_get_serial_sequence('scan_history', 'id'),
                        COALESCE((SELECT MAX(id) FROM scan_history), 1),
                        (SELECT COUNT(*) > 0 FROM scan_history)
                    )
                    """);
        }
    }

    private static TableSpec table(
            String name, String dateColumn, String symbolColumn,
            List<String> keys, List<String> columns) {
        return new TableSpec(name, dateColumn, symbolColumn, keys, columns);
    }

    private static List<String> keys(String... values) {
        return List.of(values);
    }

    private static List<String> columns(String... values) {
        return List.of(values);
    }

    public record TableStats(long rows, long symbols, String minDate, String maxDate) {
    }

    public record MigrationReport(
            boolean executed,
            Map<String, TableStats> source,
            Map<String, TableStats> targetBefore,
            Map<String, TableStats> targetAfter,
            Map<String, Long> writtenRows) {
    }

    private record TableSpec(
            String name,
            String dateColumn,
            String symbolColumn,
            List<String> keys,
            List<String> columns) {
    }

    @FunctionalInterface
    public interface ConnectionProvider {
        Connection getConnection() throws SQLException;
    }

    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        H2ToPostgresqlMigrator migrator = new H2ToPostgresqlMigrator(
                () -> DriverManager.getConnection(
                        options.sourceUrl(), options.sourceUser(), options.sourcePassword()),
                () -> DriverManager.getConnection(
                        options.targetUrl(), options.targetUser(), options.targetPassword()),
                options.batchSize());
        MigrationReport report = migrator.migrate(options.execute());
        System.out.println(options.execute() ? "Migration completed." : "Dry-run completed; no rows were written.");
        report.source().forEach((table, sourceStats) ->
                System.out.println(table + " source=" + sourceStats +
                        " target=" + report.targetAfter().get(table) +
                        " written=" + report.writtenRows().getOrDefault(table, 0L)));
    }

    private record Options(
            String sourceUrl,
            String sourceUser,
            String sourcePassword,
            String targetUrl,
            String targetUser,
            String targetPassword,
            int batchSize,
            boolean execute) {

        private static Options parse(String[] args) {
            Map<String, String> values = new LinkedHashMap<>();
            boolean execute = false;
            for (String argument : args) {
                if ("--execute".equals(argument)) {
                    execute = true;
                } else if (argument.startsWith("--") && argument.contains("=")) {
                    int separator = argument.indexOf('=');
                    values.put(argument.substring(2, separator), argument.substring(separator + 1));
                } else {
                    throw new IllegalArgumentException("Unknown argument: " + argument);
                }
            }
            return new Options(
                    required(values, "source-url", "H2_SOURCE_URL"),
                    value(values, "source-user", "H2_SOURCE_USER", "sa"),
                    value(values, "source-password", "H2_SOURCE_PASSWORD", ""),
                    required(values, "target-url", "POSTGRES_JDBC_URL"),
                    required(values, "target-user", "POSTGRES_USER"),
                    required(values, "target-password", "POSTGRES_PASSWORD"),
                    Integer.parseInt(value(values, "batch-size", "MIGRATION_BATCH_SIZE", "500")),
                    execute);
        }

        private static String required(
                Map<String, String> values, String key, String environmentKey) {
            String value = value(values, key, environmentKey, null);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(
                        "Missing --" + key + " or environment variable " + environmentKey);
            }
            return value;
        }

        private static String value(
                Map<String, String> values,
                String key,
                String environmentKey,
                String defaultValue) {
            String argumentValue = values.get(key);
            if (argumentValue != null) {
                return argumentValue;
            }
            String environmentValue = System.getenv(environmentKey);
            return environmentValue == null ? defaultValue : environmentValue;
        }
    }
}
