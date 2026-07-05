package org.gtalent;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import yahoofinance.histquotes.HistoricalQuote;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseManager {
    // 資料庫連線位置（預設改為使用者可寫入的應用程式資料夾）
    private static final String JDBC_URL = AppRuntime.resolveJdbcUrl();
    private static final String USER = "sa";
    private static final String PASSWORD = "";
    private static final HikariDataSource dataSource;

    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(JDBC_URL);
        config.setUsername(USER);
        config.setPassword(PASSWORD);

        // 連線池優化設定
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000);
        config.setConnectionTimeout(20000);

        dataSource = new HikariDataSource(config);
    }

    // 以後所有方法都透過這個 getConnection() 拿連線
    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private DatabaseManager() {
        // Utility class
    }

    public static void initDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS STOCK_DATA (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "symbol VARCHAR(20), " +
                    "trade_date DATE, " +
                    "open_price DECIMAL(10, 2), " +
                    "high_price DECIMAL(10, 2), " +
                    "low_price DECIMAL(10, 2), " +
                    "close_price DECIMAL(10, 2), " +
                    "volume BIGINT, " +
                    "price DECIMAL(10, 2)" +
                    ")";
            stmt.execute(sql);

            // 建立 STOCK_DATA 的索引
            stmt.execute("CREATE INDEX IF NOT EXISTS IDX_SYMBOL_DATE ON STOCK_DATA(symbol, trade_date)");

            // 建立全市場股票池（供掃描/廣度/批次任務使用）
            stmt.execute("CREATE TABLE IF NOT EXISTS STOCK_UNIVERSE (" +
                    "symbol VARCHAR(20) PRIMARY KEY, " +
                    "name VARCHAR(100) DEFAULT '', " +
                    "market VARCHAR(20) DEFAULT '', " +
                    "asset_type VARCHAR(30) DEFAULT 'UNKNOWN', " +
                    "is_etf BOOLEAN DEFAULT FALSE, " +
                    "active BOOLEAN DEFAULT TRUE, " +
                    "last_synced_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            try {
                stmt.execute("ALTER TABLE STOCK_UNIVERSE ADD COLUMN IF NOT EXISTS asset_type VARCHAR(30) DEFAULT 'UNKNOWN'");
                stmt.execute("ALTER TABLE STOCK_UNIVERSE ADD COLUMN IF NOT EXISTS is_etf BOOLEAN DEFAULT FALSE");
            } catch (SQLException ignore) {
                // compatibility for existing DBs
            }

            // 建立法人獨立表
            stmt.execute("CREATE TABLE IF NOT EXISTS INSTITUTIONAL_DATA (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "symbol VARCHAR(20) NOT NULL, " +
                    "trade_date DATE NOT NULL, " +
                    "foreign_buy BIGINT DEFAULT 0, " +
                    "trust_buy BIGINT DEFAULT 0, " +
                    "dealer_buy BIGINT DEFAULT 0" +
                    ")");

            // 建立法人表索引
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS IDX_INST_SYMBOL_DATE ON INSTITUTIONAL_DATA(symbol, trade_date)");

            // 處理舊版本相容性，加入新的法人欄位到 STOCK_DATA
            try {
                stmt.execute("ALTER TABLE STOCK_DATA ADD COLUMN IF NOT EXISTS foreign_buy BIGINT");
                stmt.execute("ALTER TABLE STOCK_DATA ADD COLUMN IF NOT EXISTS trust_buy BIGINT");
                stmt.execute("ALTER TABLE STOCK_DATA ADD COLUMN IF NOT EXISTS dealer_buy BIGINT");
            } catch (SQLException ignore) {
                // 如果欄位已存在，可能會拋出例外，忽略它
            }

            // 建立 SCAN_HISTORY 表
            stmt.execute("CREATE TABLE IF NOT EXISTS SCAN_HISTORY (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "symbol VARCHAR(20) NOT NULL, " +
                    "scan_date DATE NOT NULL, " +
                    "score INT NOT NULL, " +
                    "price_at_scan DOUBLE NOT NULL, " +
                    "rsi_at_scan DOUBLE" +
                    ")");

            // 建立每日大盤環境資料表
            stmt.execute("CREATE TABLE IF NOT EXISTS MARKET_BREADTH (" +
                    "trade_date DATE PRIMARY KEY, " +
                    "breadth DECIMAL(5, 2) NOT NULL, " +
                    "eligible_count INT NOT NULL, " +
                    "bullish_count INT NOT NULL, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            // 建立月營收資料表
            stmt.execute("CREATE TABLE IF NOT EXISTS MONTHLY_REVENUE (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "symbol VARCHAR(20) NOT NULL, " +
                    "year_month VARCHAR(7) NOT NULL, " +
                    "revenue DOUBLE NOT NULL, " +
                    "mom DOUBLE DEFAULT 0, " +
                    "yoy DOUBLE DEFAULT 0, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS IDX_MONTHLY_REVENUE_SYMBOL_MONTH ON MONTHLY_REVENUE(symbol, year_month)");

            // 建立 FinMind 股權分散（鎖定 HoldingFactor=15）資料表
            stmt.execute("CREATE TABLE IF NOT EXISTS FINMIND_SHAREHOLDING (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "symbol VARCHAR(20) NOT NULL, " +
                    "trade_date DATE NOT NULL, " +
                    "holding_factor INT NOT NULL, " +
                    "shareholder_count INT NOT NULL, " +
                    "shares BIGINT NOT NULL, " +
                    "percentage DOUBLE NOT NULL, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS IDX_SHAREHOLDING_SYMBOL_DATE_FACTOR ON FINMIND_SHAREHOLDING(symbol, trade_date, holding_factor)");

            // 建立 FinMind 當沖統計資料表
            stmt.execute("CREATE TABLE IF NOT EXISTS FINMIND_DAY_TRADING (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "symbol VARCHAR(20) NOT NULL, " +
                    "trade_date DATE NOT NULL, " +
                    "buy_amount BIGINT DEFAULT 0, " +
                    "sell_amount BIGINT DEFAULT 0, " +
                    "day_trading_volume BIGINT DEFAULT 0, " +
                    "day_trading_rate DOUBLE DEFAULT 0, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS IDX_DAY_TRADING_SYMBOL_DATE ON FINMIND_DAY_TRADING(symbol, trade_date)");

            // 應用程式元資料表（用於記錄首次備份完成等旗標）
            stmt.execute("CREATE TABLE IF NOT EXISTS APP_META (" +
                    "meta_key VARCHAR(100) PRIMARY KEY, " +
                    "meta_value VARCHAR(500) NOT NULL, " +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static boolean isMetaFlagSet(String key) {
        String sql = "SELECT meta_value FROM APP_META WHERE meta_key = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, key);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && "true".equals(rs.getString("meta_value"));
            }
        } catch (SQLException e) {
            System.err.println("查詢 APP_META 失敗: " + e.getMessage());
            return false;
        }
    }

    public static void setMetaFlag(String key, String value) {
        String sql = "MERGE INTO APP_META(meta_key, meta_value, updated_at) KEY(meta_key) VALUES (?, ?, CURRENT_TIMESTAMP)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, key);
            pstmt.setString(2, value);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("寫入 APP_META 失敗: " + e.getMessage());
        }
    }

    public static void saveDailyMarketBreadth(MarketBreadthResult result) {
        if (result == null) {
            return;
        }

        String sql = "MERGE INTO MARKET_BREADTH(trade_date, breadth, eligible_count, bullish_count) " +
                "KEY(trade_date) VALUES (?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDate(1, Date.valueOf(LocalDate.now()));
            pstmt.setDouble(2, result.getBreadth());
            pstmt.setInt(3, result.getEligibleCount());
            pstmt.setInt(4, result.getBullishCount());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("寫入 MARKET_BREADTH 失敗: " + e.getMessage());
        }
    }

    public static List<MarketBreadthSnapshot> getMarketBreadthHistory(int days) {
        if (days <= 0) {
            return List.of();
        }

        List<MarketBreadthSnapshot> history = new ArrayList<>();
        String sql = "SELECT * FROM (" +
                " SELECT trade_date, breadth, eligible_count, bullish_count FROM MARKET_BREADTH" +
                " ORDER BY trade_date DESC LIMIT ?" +
                ") ORDER BY trade_date ASC";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, days);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Date tradeDate = rs.getDate("trade_date");
                    history.add(new MarketBreadthSnapshot(
                            tradeDate != null ? tradeDate.toString() : null,
                            rs.getDouble("breadth"),
                            rs.getInt("eligible_count"),
                            rs.getInt("bullish_count")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("查詢 MARKET_BREADTH 歷史失敗: " + e.getMessage());
        }
        return history;
    }

    public static void saveRevenueData(String symbol, RevenueData revenueData) {
        if (symbol == null || symbol.isBlank() || revenueData == null || revenueData.getYearMonth() == null || revenueData.getYearMonth().isBlank()) {
            return;
        }

        String sql = "MERGE INTO MONTHLY_REVENUE(symbol, year_month, revenue, mom, yoy) KEY(symbol, year_month) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, symbol.trim());
            pstmt.setString(2, revenueData.getYearMonth());
            pstmt.setDouble(3, revenueData.getRevenue());
            pstmt.setDouble(4, revenueData.getMom());
            pstmt.setDouble(5, revenueData.getYoy());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("寫入 MONTHLY_REVENUE 失敗: " + e.getMessage());
        }
    }

    public static List<RevenueData> getRevenueHistory(String symbol, int months) {
        if (symbol == null || symbol.isBlank() || months <= 0) {
            return List.of();
        }

        List<RevenueData> history = new ArrayList<>();
        String sql = "SELECT * FROM (" +
                " SELECT year_month, revenue, mom, yoy FROM MONTHLY_REVENUE" +
                " WHERE symbol = ? ORDER BY year_month DESC LIMIT ?" +
                ") ORDER BY year_month ASC";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, symbol.trim());
            pstmt.setInt(2, months);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    history.add(new RevenueData(
                            rs.getString("year_month"),
                            rs.getDouble("revenue"),
                            rs.getDouble("mom"),
                            rs.getDouble("yoy")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("查詢 MONTHLY_REVENUE 歷史失敗: " + e.getMessage());
        }
        return history;
    }

    public static int saveLargeHolderShareholding(String symbol, List<FinMindShareholdingData> rows) {
        if (symbol == null || symbol.isBlank() || rows == null || rows.isEmpty()) {
            return 0;
        }

        String sql = "MERGE INTO FINMIND_SHAREHOLDING(symbol, trade_date, holding_factor, shareholder_count, shares, percentage) " +
                "KEY(symbol, trade_date, holding_factor) VALUES (?, ?, ?, ?, ?, ?)";

        int count = 0;
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (FinMindShareholdingData row : rows) {
                if (row == null || row.getDate() == null || row.getDate().isBlank()) {
                    continue;
                }
                if (row.getHoldingFactor() != 15) {
                    continue;
                }

                pstmt.setString(1, symbol.trim());
                pstmt.setDate(2, Date.valueOf(row.getDate()));
                pstmt.setInt(3, row.getHoldingFactor());
                pstmt.setInt(4, row.getShareholderCount());
                pstmt.setLong(5, row.getShares());
                pstmt.setDouble(6, row.getPercentage());
                pstmt.addBatch();
                count++;
            }

            if (count > 0) {
                pstmt.executeBatch();
            }
            return count;
        } catch (SQLException e) {
            System.err.println("寫入 FINMIND_SHAREHOLDING 失敗: " + e.getMessage());
            return 0;
        }
    }

    public static int saveDayTradingData(String symbol, List<FinMindDayTradingData> rows) {
        if (symbol == null || symbol.isBlank() || rows == null || rows.isEmpty()) {
            return 0;
        }

        String sql = "MERGE INTO FINMIND_DAY_TRADING(symbol, trade_date, buy_amount, sell_amount, day_trading_volume, day_trading_rate) " +
                "KEY(symbol, trade_date) VALUES (?, ?, ?, ?, ?, ?)";

        int count = 0;
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (FinMindDayTradingData row : rows) {
                if (row == null || row.getDate() == null || row.getDate().isBlank()) {
                    continue;
                }

                pstmt.setString(1, symbol.trim());
                pstmt.setDate(2, Date.valueOf(row.getDate()));
                pstmt.setLong(3, row.getBuyAmount());
                pstmt.setLong(4, row.getSellAmount());
                pstmt.setLong(5, row.getDayTradingVolume());
                pstmt.setDouble(6, row.getDayTradingRate());
                pstmt.addBatch();
                count++;
            }

            if (count > 0) {
                pstmt.executeBatch();
            }
            return count;
        } catch (SQLException e) {
            System.err.println("寫入 FINMIND_DAY_TRADING 失敗: " + e.getMessage());
            return 0;
        }
    }

    public static List<FinMindDayTradingData> getDayTradingHistory(String symbol, int days) {
        if (symbol == null || symbol.isBlank() || days <= 0) {
            return List.of();
        }

        List<FinMindDayTradingData> result = new ArrayList<>();
        String sql = "SELECT * FROM (" +
                " SELECT trade_date, buy_amount, sell_amount, day_trading_volume, day_trading_rate" +
                " FROM FINMIND_DAY_TRADING WHERE symbol = ?" +
                " ORDER BY trade_date DESC LIMIT ?" +
                ") ORDER BY trade_date ASC";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, symbol.trim());
            pstmt.setInt(2, days);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    FinMindDayTradingData item = new FinMindDayTradingData();
                    Date tradeDate = rs.getDate("trade_date");
                    item.setDate(tradeDate != null ? tradeDate.toString() : null);
                    item.setStockId(symbol.trim());
                    item.setBuyAmount(rs.getLong("buy_amount"));
                    item.setSellAmount(rs.getLong("sell_amount"));
                    item.setDayTradingVolume(rs.getLong("day_trading_volume"));
                    item.setDayTradingRate(rs.getDouble("day_trading_rate"));
                    result.add(item);
                }
            }
        } catch (SQLException e) {
            System.err.println("查詢 FINMIND_DAY_TRADING 失敗: " + e.getMessage());
        }

        return result;
    }

    public static List<FinMindShareholdingData> getLargeHolderShareholdingHistory(String symbol, int weeks) {
        if (symbol == null || symbol.isBlank() || weeks <= 0) {
            return List.of();
        }

        List<FinMindShareholdingData> result = new ArrayList<>();
        String sql = "SELECT * FROM (" +
                " SELECT trade_date, holding_factor, shareholder_count, shares, percentage" +
                " FROM FINMIND_SHAREHOLDING WHERE symbol = ? AND holding_factor = 15" +
                " ORDER BY trade_date DESC LIMIT ?" +
                ") ORDER BY trade_date ASC";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, symbol.trim());
            pstmt.setInt(2, weeks);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    FinMindShareholdingData item = new FinMindShareholdingData();
                    Date tradeDate = rs.getDate("trade_date");
                    item.setDate(tradeDate != null ? tradeDate.toString() : null);
                    item.setStockId(symbol.trim());
                    item.setHoldingFactor(rs.getInt("holding_factor"));
                    item.setShareholderCount(rs.getInt("shareholder_count"));
                    item.setShares(rs.getLong("shares"));
                    item.setPercentage(rs.getDouble("percentage"));
                    result.add(item);
                }
            }
        } catch (SQLException e) {
            System.err.println("查詢 FINMIND_SHAREHOLDING 失敗: " + e.getMessage());
        }
        return result;
    }

    public static int saveHistoricalQuotes(String symbol, List<HistoricalQuote> history) {
        if (history == null || history.isEmpty()) {
            return 0;
        }

        //noinspection SqlResolve
        String sql = "MERGE INTO STOCK_DATA(symbol, trade_date, close_price, volume) KEY(symbol, trade_date) VALUES (?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            int count = 0;
            for (HistoricalQuote quote : history) {
                if (quote == null || quote.getDate() == null) {
                    continue;
                }

                Date sqlDate = new Date(quote.getDate().getTimeInMillis());
                pstmt.setString(1, symbol);
                pstmt.setDate(2, sqlDate);
                pstmt.setBigDecimal(3, quote.getClose());
                if (quote.getVolume() == null) {
                    pstmt.setNull(4, java.sql.Types.BIGINT);
                } else {
                    pstmt.setLong(4, quote.getVolume());
                }
                pstmt.addBatch();
                count++;
            }

            if (count == 0) {
                return 0;
            }

            pstmt.executeBatch();
            return count;
        } catch (SQLException e) {
            System.err.println("資料存入失敗: " + e.getMessage());
            return 0;
        }
    }

    public static boolean saveSimpleData(String symbol, String date, String price) {
        //noinspection SqlResolve
        String sql = "MERGE INTO STOCK_DATA(symbol, trade_date, close_price, volume) " +
                "KEY(symbol, trade_date) VALUES (?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, symbol);
            pstmt.setDate(2, Date.valueOf(date));
            pstmt.setBigDecimal(3, new java.math.BigDecimal(price));
            pstmt.setNull(4, java.sql.Types.BIGINT);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException | IllegalArgumentException e) {
            System.err.println("簡易儲存失敗(" + date + "): " + e.getMessage());
            return false;
        }
    }

    public static double calculateMA(String symbol, int days) {
        if (symbol == null || symbol.isBlank() || days <= 0) {
            return 0.0;
        }

        // SQL 語法：選取最近 N 天的收盤價，並計算平均值
        // 使用子查詢先過濾出最近 N 天，再對這 N 天算平均
        //noinspection SqlResolve
        String sql = "SELECT AVG(close_price) FROM (" +
                "  SELECT close_price FROM STOCK_DATA " +
                "  WHERE symbol = ? " +
                "  ORDER BY trade_date DESC " +
                "  LIMIT ?" +
                ")";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, symbol);
            pstmt.setInt(2, days);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble(1); // 回傳平均值
                }
            }
        } catch (SQLException e) {
            System.err.println("計算均線失敗: " + e.getMessage());
        }
        return 0.0;
    }

    public static double getLatestPrice(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return 0.0;
        }

        //noinspection SqlResolve
        String sql = "SELECT close_price FROM STOCK_DATA WHERE symbol = ? ORDER BY trade_date DESC LIMIT 1";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, symbol);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("close_price");
                }
            }
        } catch (SQLException e) {
            System.err.println("查詢最新價格失敗: " + e.getMessage());
        }
        return 0.0;
    }

    public static Map<String, Double> calculateBollinger(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }

        List<Double> prices = new ArrayList<>();
        //noinspection SqlResolve
        String sql = "SELECT close_price FROM STOCK_DATA WHERE symbol = ? ORDER BY trade_date DESC LIMIT 20";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, symbol);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    prices.add(rs.getDouble(1));
                }
            }
        } catch (SQLException e) {
            System.err.println("計算布林通道失敗: " + e.getMessage());
            return null;
        }

        if (prices.size() < 20) {
            return null;
        }

        double sum = 0;
        for (double p : prices) {
            sum += p;
        }
        double ma20 = sum / 20;

        double standardDeviationSum = 0;
        for (double p : prices) {
            standardDeviationSum += Math.pow(p - ma20, 2);
        }
        double sd = Math.sqrt(standardDeviationSum / 20);

        Map<String, Double> bands = new HashMap<>();
        bands.put("mid", ma20);
        bands.put("upper", ma20 + (sd * 2));
        bands.put("lower", ma20 - (sd * 2));
        return bands;
    }

    public static void saveAllToDatabase(String symbol, List<String[]> dataList) {
        if (symbol == null || symbol.isBlank() || dataList == null || dataList.isEmpty()) {
            return;
        }

        // Use MERGE to upsert by (symbol, trade_date) and avoid duplicate-key write failures.
        //noinspection SqlResolve
        String sql = "MERGE INTO STOCK_DATA (symbol, trade_date, open_price, high_price, low_price, close_price, volume) " +
                "KEY(symbol, trade_date) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            conn.setAutoCommit(false); // 關閉自動提交，改為手動批次提交

            for (String[] row : dataList) {
                if (row == null || row.length == 0) {
                    continue;
                }

                // 相容兩種資料格式：
                // 1) 完整 TWSE row: [date, volume, amount, open(3), high(4), low(5), close(6)]
                // 2) 簡化 row: [date, close]
                String twDate = row[0];
                String westernDate = convertToWesternDate(twDate);
                if (westernDate == null) {
                    continue;
                }

                String rawOpen = null;
                String rawHigh = null;
                String rawLow = null;
                String rawClose;
                String rawVol = null;
                if (row.length >= 7) {
                    rawVol = row[1];
                    rawOpen = row[3];
                    rawHigh = row[4];
                    rawLow = row[5];
                    rawClose = row[6];
                } else if (row.length >= 2) {
                    rawClose = row[1];
                } else {
                    continue;
                }

                if (rawClose == null || rawClose.isBlank() || "--".equals(rawClose)) {
                    continue;
                }

                String close = rawClose.replace(",", "");
                String open = (rawOpen == null || rawOpen.isBlank() || "--".equals(rawOpen)) ? close : rawOpen.replace(",", "");
                String high = (rawHigh == null || rawHigh.isBlank() || "--".equals(rawHigh)) ? close : rawHigh.replace(",", "");
                String low = (rawLow == null || rawLow.isBlank() || "--".equals(rawLow)) ? close : rawLow.replace(",", "");

                pstmt.setString(1, symbol);
                pstmt.setDate(2, Date.valueOf(westernDate));
                pstmt.setBigDecimal(3, new BigDecimal(open));
                pstmt.setBigDecimal(4, new BigDecimal(high));
                pstmt.setBigDecimal(5, new BigDecimal(low));
                pstmt.setBigDecimal(6, new BigDecimal(close));

                if (rawVol == null || rawVol.isBlank() || "--".equals(rawVol)) {
                    pstmt.setNull(7, java.sql.Types.BIGINT);
                } else {
                    String vol = rawVol.replace(",", "");
                    pstmt.setLong(7, Long.parseLong(vol));
                }
                pstmt.addBatch(); // 加入批次
            }

            pstmt.executeBatch(); // 一次執行
            conn.commit(); // 提交事務
        } catch (Exception e) {
            System.err.println("批次資料存入失敗: " + e.getMessage());
        }
    }

    private static String convertToWesternDate(String twDate) {
        if (twDate == null || twDate.isBlank()) {
            return null;
        }

        String[] parts = twDate.split("/");
        if (parts.length != 3) {
            return null;
        }

        try {
            int westernYear = Integer.parseInt(parts[0]) + 1911;
            return westernYear + "-" + parts[1] + "-" + parts[2];
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static List<StockDataPoint> getRecentHistory(String symbol, int days) {
        if (symbol == null || symbol.isBlank() || days <= 0) {
            return List.of();
        }

        List<StockDataPoint> list = new ArrayList<>();
        //noinspection SqlResolve
        String sql = "SELECT * FROM (" +
                "  SELECT trade_date, " +
                "         COALESCE(open_price, close_price) AS open_price, " +
                "         COALESCE(high_price, close_price) AS high_price, " +
                "         COALESCE(low_price, close_price) AS low_price, " +
                "         close_price, volume FROM STOCK_DATA " +
                "  WHERE symbol = ? " +
                "  ORDER BY trade_date DESC LIMIT ?" +
                ") ORDER BY trade_date ASC";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, symbol);
            pstmt.setInt(2, Math.max(days, days * 2));

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    LocalDate tradeDate = rs.getDate("trade_date").toLocalDate();
                    if (!isLikelyTradingDate(tradeDate)) {
                        continue;
                    }
                    list.add(new StockDataPoint(
                            tradeDate.toString(),
                            rs.getDouble("open_price"),
                            rs.getDouble("high_price"),
                            rs.getDouble("low_price"),
                            rs.getDouble("close_price"),
                            rs.getLong("volume")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("查詢歷史數據失敗: " + e.getMessage());
        }
        if (list.size() <= days) {
            return list;
        }
        return new ArrayList<>(list.subList(list.size() - days, list.size()));
    }

    public static List<StockDataPoint> getFullHistory(String symbol, int days) {
        if (symbol == null || symbol.isBlank() || days <= 0) {
            return List.of();
        }

        List<StockDataPoint> list = new ArrayList<>();
        //noinspection SqlResolve
        String sql = "SELECT * FROM (" +
                "  SELECT trade_date, open_price, high_price, low_price, close_price, volume FROM STOCK_DATA " +
                "  WHERE symbol = ? " +
                "  ORDER BY trade_date DESC LIMIT ?" +
                ") ORDER BY trade_date ASC";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, symbol);
            pstmt.setInt(2, Math.max(days, days * 2));

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    LocalDate tradeDate = rs.getDate("trade_date").toLocalDate();
                    if (!isLikelyTradingDate(tradeDate)) {
                        continue;
                    }
                    list.add(new StockDataPoint(
                            tradeDate.toString(),
                            rs.getDouble("open_price"),
                            rs.getDouble("high_price"),
                            rs.getDouble("low_price"),
                            rs.getDouble("close_price"),
                            rs.getLong("volume")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("查詢完整歷史數據失敗: " + e.getMessage());
        }
        if (list.size() <= days) {
            return list;
        }
        return new ArrayList<>(list.subList(list.size() - days, list.size()));
    }

    private static boolean isLikelyTradingDate(LocalDate date) {
        if (date == null) {
            return false;
        }
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    public static List<StockDataPoint> getHistoryForBacktest(String symbol, int days) {
        return getFullHistory(symbol, days);
    }

    public static List<String> getAllSymbols() {
        return getAllSymbols(null, null);
    }

    /**
     * 只回傳在 STOCK_DATA 中擁有至少 minRecords 筆收盤紀錄的股票代碼。
     * 掃描引擎應優先使用此方法，避免對無資料的股票池成員白費計算。
     *
     * @param minRecords   最少需要的 STOCK_DATA 紀錄筆數（建議 25，用於 MA20 + 5天前）
     * @param assetType    商品類型篩選（null / 空字串 = 不篩選）
     * @param market       市場篩選（null / 空字串 = 不篩選）
     */
    public static List<String> getAllSymbolsWithData(int minRecords, String assetType, String market) {
        List<String> symbols = new ArrayList<>();
        String normalizedAssetType = normalizeFilter(assetType);
        String normalizedMarket = normalizeFilter(market);
        boolean hasFilter = !normalizedAssetType.isBlank() || !normalizedMarket.isBlank();

        // CTE: symbols from STOCK_DATA that meet the minimum record count
        String dataCte = "(SELECT symbol, COUNT(*) AS cnt FROM STOCK_DATA GROUP BY symbol HAVING cnt >= ?) AS d";

        String sql;
        if (hasFilter) {
            // Filtered: must be in STOCK_UNIVERSE with matching type/market AND have enough data
            sql = "SELECT u.symbol FROM STOCK_UNIVERSE u" +
                  " INNER JOIN " + dataCte + " ON d.symbol = u.symbol" +
                  " WHERE u.active = TRUE" +
                  (normalizedAssetType.isBlank() ? "" : " AND UPPER(u.asset_type) = ?") +
                  (normalizedMarket.isBlank()    ? "" : " AND UPPER(u.market) = ?")     +
                  " ORDER BY u.symbol";
        } else {
            // No filter: union of STOCK_UNIVERSE (active) + bare STOCK_DATA entries
            sql = "SELECT symbol FROM (" +
                  "  SELECT u.symbol FROM STOCK_UNIVERSE u" +
                  "   INNER JOIN " + dataCte + " ON d.symbol = u.symbol" +
                  "   WHERE u.active = TRUE" +
                  "  UNION" +
                  "  SELECT s.symbol FROM (SELECT symbol FROM STOCK_DATA GROUP BY symbol HAVING COUNT(*) >= ?) AS s" +
                  "   LEFT JOIN STOCK_UNIVERSE u ON u.symbol = s.symbol" +
                  "   WHERE u.symbol IS NULL OR u.active = TRUE" +
                  ") ORDER BY symbol";
        }

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            int idx = 1;
            if (hasFilter) {
                pstmt.setInt(idx++, minRecords);
                if (!normalizedAssetType.isBlank()) pstmt.setString(idx++, normalizedAssetType);
                if (!normalizedMarket.isBlank())    pstmt.setString(idx, normalizedMarket);
            } else {
                pstmt.setInt(idx++, minRecords); // first CTE
                pstmt.setInt(idx, minRecords);   // second subquery
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    symbols.add(rs.getString("symbol"));
                }
            }
        } catch (SQLException e) {
            System.err.println("查詢有資料股票代碼清單失敗: " + e.getMessage());
        }
        return symbols;
    }

    public static List<String> getAllSymbols(String assetType, String market) {
        List<String> symbols = new ArrayList<>();
        String normalizedAssetType = normalizeFilter(assetType);
        String normalizedMarket = normalizeFilter(market);

        boolean hasFilter = !normalizedAssetType.isBlank() || !normalizedMarket.isBlank();
        String sql;
        if (hasFilter) {
            sql = "SELECT symbol FROM STOCK_UNIVERSE WHERE active = TRUE" +
                    (normalizedAssetType.isBlank() ? "" : " AND UPPER(asset_type) = ?") +
                    (normalizedMarket.isBlank() ? "" : " AND UPPER(market) = ?") +
                    " ORDER BY symbol";
        } else {
            //noinspection SqlResolve
            sql = "SELECT symbol FROM (" +
                    " SELECT symbol FROM STOCK_UNIVERSE WHERE active = TRUE" +
                    " UNION" +
                    " SELECT DISTINCT s.symbol FROM STOCK_DATA s" +
                    " LEFT JOIN STOCK_UNIVERSE u ON u.symbol = s.symbol" +
                    " WHERE s.symbol IS NOT NULL AND s.symbol <> ''" +
                    "   AND (u.symbol IS NULL OR u.active = TRUE)" +
                    ") ORDER BY symbol";
        }

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            int paramIndex = 1;
            if (hasFilter) {
                if (!normalizedAssetType.isBlank()) {
                    pstmt.setString(paramIndex++, normalizedAssetType);
                }
                if (!normalizedMarket.isBlank()) {
                    pstmt.setString(paramIndex, normalizedMarket);
                }
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    symbols.add(rs.getString("symbol"));
                }
            }
        } catch (SQLException e) {
            System.err.println("查詢股票代碼清單失敗: " + e.getMessage());
        }
        return symbols;
    }

    public static int saveStockUniverse(List<StockUniverseEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return 0;
        }

        String sql = "MERGE INTO STOCK_UNIVERSE(symbol, name, market, asset_type, is_etf, active, last_synced_at) " +
                "KEY(symbol) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";

        int count = 0;
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (StockUniverseEntry entry : entries) {
                if (entry == null || entry.getSymbol().isBlank()) {
                    continue;
                }
                pstmt.setString(1, entry.getSymbol());
                pstmt.setString(2, entry.getName());
                pstmt.setString(3, entry.getMarket());
                pstmt.setString(4, entry.getAssetType());
                pstmt.setBoolean(5, entry.isEtf());
                pstmt.setBoolean(6, entry.isActive());
                pstmt.addBatch();
                count++;
            }

            if (count > 0) {
                pstmt.executeBatch();
            }
            return count;
        } catch (SQLException e) {
            System.err.println("寫入 STOCK_UNIVERSE 失敗: " + e.getMessage());
            return 0;
        }
    }

    public static int getStockUniverseCount() {
        String sql = "SELECT COUNT(*) FROM STOCK_UNIVERSE WHERE active = TRUE";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("查詢 STOCK_UNIVERSE 筆數失敗: " + e.getMessage());
        }
        return 0;
    }

    public static StockUniverseEntry getStockUniverseEntry(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }

        String sql = "SELECT symbol, name, market, asset_type, is_etf, active FROM STOCK_UNIVERSE WHERE symbol = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, symbol.trim());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new StockUniverseEntry(
                            rs.getString("symbol"),
                            rs.getString("name"),
                            rs.getString("market"),
                            rs.getString("asset_type"),
                            rs.getBoolean("is_etf"),
                            rs.getBoolean("active")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("查詢 STOCK_UNIVERSE 單筆資料失敗: " + e.getMessage());
        }
        return null;
    }

    private static String normalizeFilter(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    /**
     * 直接以西元日期 (yyyy-MM-dd) 和已清理數字的 OHLCV 字串，一次寫入一筆。
     * 供批次 API 呼叫使用（STOCK_DAY_ALL / TPEX daily）。
     */
    public static void saveBulkOhlcvRow(String symbol, String date,
                                        String open, String high, String low,
                                        String close, String volume) {
        if (symbol == null || symbol.isBlank() || date == null || close == null || close.isBlank()) {
            return;
        }
        //noinspection SqlResolve
        String sql = "MERGE INTO STOCK_DATA (symbol, trade_date, open_price, high_price, low_price, close_price, volume) " +
                "KEY(symbol, trade_date) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, symbol);
            pstmt.setDate(2, Date.valueOf(date));
            pstmt.setBigDecimal(3, parsePriceSafe(open, close));
            pstmt.setBigDecimal(4, parsePriceSafe(high, close));
            pstmt.setBigDecimal(5, parsePriceSafe(low, close));
            pstmt.setBigDecimal(6, parsePriceSafe(close, null));
            if (volume == null || volume.isBlank() || !volume.matches("[0-9]+")) {
                pstmt.setNull(7, java.sql.Types.BIGINT);
            } else {
                pstmt.setLong(7, Long.parseLong(volume));
            }
            pstmt.executeUpdate();
        } catch (Exception e) {
            System.err.printf("saveBulkOhlcvRow 失敗（%s %s）: %s%n", symbol, date, e.getMessage());
        }
    }

    private static BigDecimal parsePriceSafe(String value, String fallback) {
        if (value != null && !value.isBlank()) {
            try { return new BigDecimal(value.replace(",", "")); } catch (Exception ignored) {}
        }
        if (fallback != null && !fallback.isBlank()) {
            try { return new BigDecimal(fallback.replace(",", "")); } catch (Exception ignored) {}
        }
        return BigDecimal.ZERO;
    }

    public static long getLatestVolume(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return 0L;
        }

        //noinspection SqlResolve
        String sql = "SELECT volume FROM STOCK_DATA WHERE symbol = ? ORDER BY trade_date DESC LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, symbol);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("volume");
                }
            }
        } catch (SQLException e) {
            System.err.println("查詢最新成交量失敗: " + e.getMessage());
        }
        return 0L;
    }

    public static long calculateVolumeMA(String symbol, int days) {
        if (symbol == null || symbol.isBlank() || days <= 0) {
            return 0L;
        }

        //noinspection SqlResolve
        String sql = "SELECT AVG(COALESCE(volume, 0)) FROM (" +
                " SELECT volume FROM STOCK_DATA WHERE symbol = ? ORDER BY trade_date DESC LIMIT ?" +
                ")";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, symbol);
            pstmt.setInt(2, days);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Math.round(rs.getDouble(1));
                }
            }
        } catch (SQLException e) {
            System.err.println("計算成交量均線失敗: " + e.getMessage());
        }
        return 0L;
    }

    public static int saveInstitutionalTrades(String symbol, List<InstitutionalTrade> trades) {
        if (symbol == null || symbol.isBlank() || trades == null || trades.isEmpty()) {
            return 0;
        }

        String sql = "MERGE INTO INSTITUTIONAL_DATA(symbol, trade_date, foreign_buy, trust_buy, dealer_buy) " +
                "KEY(symbol, trade_date) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            int count = 0;
            for (InstitutionalTrade trade : trades) {
                if (trade == null || trade.getDate() == null || trade.getDate().isBlank()) {
                    continue;
                }
                pstmt.setString(1, symbol);
                pstmt.setDate(2, Date.valueOf(trade.getDate()));
                pstmt.setLong(3, trade.getForeignBuy());
                pstmt.setLong(4, trade.getTrustBuy());
                pstmt.setLong(5, trade.getDealerBuy());
                pstmt.addBatch();
                count++;
            }

            if (count == 0) {
                return 0;
            }
            pstmt.executeBatch();
            return count;
        } catch (SQLException e) {
            System.err.println("寫入法人資料失敗: " + e.getMessage());
            return 0;
        }
    }

    public static List<InstitutionalTrade> getRecentInstitutionalTrades(String symbol, int days) {
        if (symbol == null || symbol.isBlank() || days <= 0) {
            return List.of();
        }

        List<InstitutionalTrade> result = new ArrayList<>();
        String sql = "SELECT i.trade_date, i.foreign_buy, i.trust_buy, i.dealer_buy, COALESCE(s.volume, 0) AS daily_volume FROM (" +
                " SELECT trade_date, foreign_buy, trust_buy, dealer_buy FROM INSTITUTIONAL_DATA" +
                " WHERE symbol = ? ORDER BY trade_date DESC LIMIT ?" +
                ") i LEFT JOIN STOCK_DATA s ON s.symbol = ? AND s.trade_date = i.trade_date " +
                " ORDER BY i.trade_date ASC";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, symbol);
            pstmt.setInt(2, days);
            pstmt.setString(3, symbol);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.add(new InstitutionalTrade(
                            rs.getDate("trade_date").toString(),
                            rs.getLong("foreign_buy"),
                            rs.getLong("trust_buy"),
                            rs.getLong("dealer_buy"),
                            rs.getLong("daily_volume")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("查詢法人資料失敗: " + e.getMessage());
        }
        return result;
    }

    public static double getAverageInstitutionalNetBuy(String symbol, int days) {
        if (symbol == null || symbol.isBlank() || days <= 0) {
            return 0.0;
        }

        String sql = "SELECT AVG(net) FROM (" +
                " SELECT foreign_buy + trust_buy + dealer_buy AS net FROM INSTITUTIONAL_DATA" +
                " WHERE symbol = ? ORDER BY trade_date DESC LIMIT ?" +
                ") AS t";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, symbol);
            pstmt.setInt(2, days);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("計算法人平均淨買超失敗: " + e.getMessage());
        }
        return 0.0;
    }

    public static int countConsecutiveTrustBuyDays(String symbol, int maxDays) {
        if (symbol == null || symbol.isBlank() || maxDays <= 0) {
            return 0;
        }

        String sql = "SELECT trust_buy FROM INSTITUTIONAL_DATA WHERE symbol = ? ORDER BY trade_date DESC LIMIT ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, symbol);
            pstmt.setInt(2, maxDays);
            int count = 0;
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    long trustBuy = rs.getLong("trust_buy");
                    if (trustBuy > 0) {
                        count++;
                    } else {
                        break;
                    }
                }
            }
            return count;
        } catch (SQLException e) {
            System.err.println("計算投信連買天數失敗: " + e.getMessage());
            return 0;
        }
    }

    // ════════════════════════════════════════════════════════════
    //  季度財報快取（FINANCIAL_QUARTER_DATA）
    //
    //  對應 FinMind TaiwanStockFinancialStatements 三率直接欄位：
    //    GrossProfitMargin / OperatingProfitMargin / NetProfitMargin
    //  以及 TaiwanStockBalanceSheet 推算欄位：
    //    inventory_turnover_days / contract_liabilities
    // ════════════════════════════════════════════════════════════

    /**
     * 寫入（或更新）一筆季度財報快取。
     * 使用 MERGE … KEY(symbol, quarter_date) 保證冪等性。
     *
     * @param symbol 股票代號
     * @param data   已填充完畢的 {@link FinancialQuarterData}
     */
    public static void saveFinancialQuarterData(String symbol, FinancialQuarterData data) {
        if (symbol == null || symbol.isBlank() || data == null || data.getQuarterDate() == null) {
            return;
        }
        String sql = "MERGE INTO FINANCIAL_QUARTER_DATA" +
                "(symbol, quarter_date, gross_profit_margin, operating_profit_margin," +
                " net_profit_margin, inventory_turnover_days, contract_liabilities)" +
                " KEY(symbol, quarter_date)" +
                " VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, symbol.trim());
            ps.setDate(2, Date.valueOf(data.getQuarterDate()));
            ps.setDouble(3, data.getGrossProfitMargin());
            ps.setDouble(4, data.getOperatingProfitMargin());
            ps.setDouble(5, data.getNetProfitMargin());
            ps.setInt(6, data.getInventoryTurnoverDays());
            ps.setLong(7, data.getContractLiabilities());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("寫入 FINANCIAL_QUARTER_DATA 失敗(" + symbol + "): " + e.getMessage());
        }
    }

    /**
     * 讀取最近 N 季的財報快取，結果按季度日期升序排列。
     *
     * @param symbol   股票代號
     * @param quarters 最多筆數
     * @return 季度財報列表（最多 quarters 筆，可能更少）
     */
    public static List<FinancialQuarterData> getFinancialQuarterHistory(String symbol, int quarters) {
        if (symbol == null || symbol.isBlank() || quarters <= 0) {
            return List.of();
        }
        // 先取最新 N 筆（DESC），再反轉為升序（ASC）
        String sql = "SELECT * FROM (" +
                " SELECT quarter_date, gross_profit_margin, operating_profit_margin," +
                "        net_profit_margin, inventory_turnover_days, contract_liabilities" +
                " FROM FINANCIAL_QUARTER_DATA" +
                " WHERE symbol = ? ORDER BY quarter_date DESC LIMIT ?" +
                ") ORDER BY quarter_date ASC";
        List<FinancialQuarterData> list = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, symbol.trim());
            ps.setInt(2, quarters);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new FinancialQuarterData(
                            rs.getDate("quarter_date").toString(),
                            rs.getDouble("gross_profit_margin"),
                            rs.getDouble("operating_profit_margin"),
                            rs.getDouble("net_profit_margin"),
                            rs.getInt("inventory_turnover_days"),
                            rs.getLong("contract_liabilities")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("查詢 FINANCIAL_QUARTER_DATA 失敗(" + symbol + "): " + e.getMessage());
        }
        return list;
    }
}
