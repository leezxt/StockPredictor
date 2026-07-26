package org.gtalent;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseSchemaInitializer {

    @FunctionalInterface
    interface ConnectionProvider {
        Connection getConnection() throws SQLException;
    }

    private DatabaseSchemaInitializer() {
    }

    static void initialize(ConnectionProvider connectionProvider) {
        try (Connection conn = connectionProvider.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS STOCK_DATA (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, symbol VARCHAR(20), trade_date DATE, " +
                    "open_price DECIMAL(10, 2), high_price DECIMAL(10, 2), low_price DECIMAL(10, 2), " +
                    "close_price DECIMAL(10, 2), volume BIGINT, price DECIMAL(10, 2))");
            stmt.execute("CREATE INDEX IF NOT EXISTS IDX_SYMBOL_DATE ON STOCK_DATA(symbol, trade_date)");

            stmt.execute("CREATE TABLE IF NOT EXISTS STOCK_UNIVERSE (" +
                    "symbol VARCHAR(20) PRIMARY KEY, name VARCHAR(100) DEFAULT '', market VARCHAR(20) DEFAULT '', " +
                    "asset_type VARCHAR(30) DEFAULT 'UNKNOWN', is_etf BOOLEAN DEFAULT FALSE, " +
                    "active BOOLEAN DEFAULT TRUE, last_synced_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            executeCompatibilityStatement(stmt,
                    "ALTER TABLE STOCK_UNIVERSE ADD COLUMN IF NOT EXISTS asset_type VARCHAR(30) DEFAULT 'UNKNOWN'");
            executeCompatibilityStatement(stmt,
                    "ALTER TABLE STOCK_UNIVERSE ADD COLUMN IF NOT EXISTS is_etf BOOLEAN DEFAULT FALSE");

            stmt.execute("CREATE TABLE IF NOT EXISTS INSTITUTIONAL_DATA (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, symbol VARCHAR(20) NOT NULL, trade_date DATE NOT NULL, " +
                    "foreign_buy BIGINT DEFAULT 0, trust_buy BIGINT DEFAULT 0, dealer_buy BIGINT DEFAULT 0)");
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS IDX_INST_SYMBOL_DATE " +
                    "ON INSTITUTIONAL_DATA(symbol, trade_date)");

            executeCompatibilityStatement(stmt,
                    "ALTER TABLE STOCK_DATA ADD COLUMN IF NOT EXISTS foreign_buy BIGINT");
            executeCompatibilityStatement(stmt,
                    "ALTER TABLE STOCK_DATA ADD COLUMN IF NOT EXISTS trust_buy BIGINT");
            executeCompatibilityStatement(stmt,
                    "ALTER TABLE STOCK_DATA ADD COLUMN IF NOT EXISTS dealer_buy BIGINT");

            stmt.execute("CREATE TABLE IF NOT EXISTS SCAN_HISTORY (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, symbol VARCHAR(20) NOT NULL, trade_date DATE NOT NULL, " +
                    "score INT NOT NULL, price_at_scan DOUBLE NOT NULL, rsi_at_scan DOUBLE)");
            stmt.execute("CREATE TABLE IF NOT EXISTS MARKET_BREADTH (" +
                    "trade_date DATE PRIMARY KEY, breadth DECIMAL(5, 2) NOT NULL, eligible_count INT NOT NULL, " +
                    "bullish_count INT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            stmt.execute("CREATE TABLE IF NOT EXISTS MONTHLY_REVENUE (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, symbol VARCHAR(20) NOT NULL, year_month VARCHAR(7) NOT NULL, " +
                    "revenue DOUBLE NOT NULL, mom DOUBLE DEFAULT 0, yoy DOUBLE DEFAULT 0, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS IDX_MONTHLY_REVENUE_SYMBOL_MONTH " +
                    "ON MONTHLY_REVENUE(symbol, year_month)");
            stmt.execute("CREATE TABLE IF NOT EXISTS FINMIND_SHAREHOLDING (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, symbol VARCHAR(20) NOT NULL, trade_date DATE NOT NULL, " +
                    "holding_factor INT NOT NULL, shareholder_count INT NOT NULL, shares BIGINT NOT NULL, " +
                    "percentage DOUBLE NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS IDX_SHAREHOLDING_SYMBOL_DATE_FACTOR " +
                    "ON FINMIND_SHAREHOLDING(symbol, trade_date, holding_factor)");
            stmt.execute("CREATE TABLE IF NOT EXISTS FINMIND_DAY_TRADING (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, symbol VARCHAR(20) NOT NULL, trade_date DATE NOT NULL, " +
                    "buy_amount BIGINT DEFAULT 0, sell_amount BIGINT DEFAULT 0, day_trading_volume BIGINT DEFAULT 0, " +
                    "day_trading_rate DOUBLE DEFAULT 0, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS IDX_DAY_TRADING_SYMBOL_DATE " +
                    "ON FINMIND_DAY_TRADING(symbol, trade_date)");
            stmt.execute("CREATE TABLE IF NOT EXISTS APP_META (" +
                    "meta_key VARCHAR(100) PRIMARY KEY, meta_value VARCHAR(500) NOT NULL, " +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            stmt.execute("CREATE TABLE IF NOT EXISTS FINANCIAL_QUARTER_DATA (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, symbol VARCHAR(20) NOT NULL, " +
                    "quarter_date DATE NOT NULL, gross_profit_margin DOUBLE DEFAULT 0, " +
                    "operating_profit_margin DOUBLE DEFAULT 0, net_profit_margin DOUBLE DEFAULT 0, " +
                    "inventory_turnover_days INT DEFAULT 0, contract_liabilities BIGINT DEFAULT 0, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS IDX_FINANCIAL_QUARTER_SYMBOL_DATE " +
                    "ON FINANCIAL_QUARTER_DATA(symbol, quarter_date)");
        } catch (SQLException e) {
            throw new IllegalStateException("資料庫 schema 初始化失敗", e);
        }
    }

    private static void executeCompatibilityStatement(Statement stmt, String sql) {
        try {
            stmt.execute(sql);
        } catch (SQLException ignored) {
            // 保留舊版 H2 schema 的相容行為。
        }
    }
}
