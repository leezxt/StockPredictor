package org.gtalent;

import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;
import yahoofinance.histquotes.HistoricalQuote;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class StockDataRepository {

    @FunctionalInterface
    interface ConnectionProvider {
        Connection getConnection() throws SQLException;
    }

    private final ConnectionProvider connectionProvider;

    @Autowired
    public StockDataRepository(DataSource dataSource) {
        this(dataSource::getConnection);
    }

    StockDataRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public int saveHistoricalQuotes(String symbol, List<HistoricalQuote> history) {
        if (history == null || history.isEmpty()) {
            return 0;
        }
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(DatabaseUpsertSql.build(
                     conn,
                     "STOCK_DATA",
                     new String[]{"symbol", "trade_date", "close_price", "volume"},
                     new String[]{"symbol", "trade_date"}))) {
            int count = 0;
            for (HistoricalQuote quote : history) {
                if (quote == null || quote.getDate() == null) {
                    continue;
                }
                pstmt.setString(1, symbol);
                pstmt.setDate(2, new Date(quote.getDate().getTimeInMillis()));
                pstmt.setBigDecimal(3, quote.getClose());
                if (quote.getVolume() == null) {
                    pstmt.setNull(4, Types.BIGINT);
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

    public boolean saveSimpleData(String symbol, String date, String price) {
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(DatabaseUpsertSql.build(
                     conn,
                     "STOCK_DATA",
                     new String[]{"symbol", "trade_date", "close_price", "volume"},
                     new String[]{"symbol", "trade_date"}))) {
            pstmt.setString(1, symbol);
            pstmt.setDate(2, Date.valueOf(date));
            pstmt.setBigDecimal(3, new BigDecimal(price));
            pstmt.setNull(4, Types.BIGINT);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException | IllegalArgumentException e) {
            System.err.println("簡易儲存失敗(" + date + "): " + e.getMessage());
            return false;
        }
    }

    public double calculateMA(String symbol, int days) {
        if (symbol == null || symbol.isBlank() || days <= 0) {
            return 0.0;
        }
        String sql = "SELECT AVG(close_price) FROM (" +
                "SELECT close_price FROM STOCK_DATA WHERE symbol = ? " +
                "ORDER BY trade_date DESC LIMIT ?)";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, symbol);
            pstmt.setInt(2, days);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        } catch (SQLException e) {
            System.err.println("計算均線失敗: " + e.getMessage());
            return 0.0;
        }
    }

    public double getLatestPrice(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return 0.0;
        }
        String sql = "SELECT close_price FROM STOCK_DATA WHERE symbol = ? ORDER BY trade_date DESC LIMIT 1";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, symbol);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getDouble("close_price") : 0.0;
            }
        } catch (SQLException e) {
            System.err.println("查詢最新價格失敗: " + e.getMessage());
            return 0.0;
        }
    }

    public Map<String, Double> calculateBollinger(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        List<Double> prices = new ArrayList<>();
        String sql = "SELECT close_price FROM STOCK_DATA WHERE symbol = ? ORDER BY trade_date DESC LIMIT 20";
        try (Connection conn = connectionProvider.getConnection();
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
        double mid = prices.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double deviation = Math.sqrt(prices.stream()
                .mapToDouble(price -> Math.pow(price - mid, 2))
                .sum() / 20);
        Map<String, Double> bands = new HashMap<>();
        bands.put("mid", mid);
        bands.put("upper", mid + deviation * 2);
        bands.put("lower", mid - deviation * 2);
        return bands;
    }

    public void saveBulkOhlcvRow(String symbol, String date, String open, String high,
                                 String low, String close, String volume) {
        if (symbol == null || symbol.isBlank() || date == null || close == null || close.isBlank()) {
            return;
        }
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(stockOhlcvUpsertSql(conn))) {
            pstmt.setString(1, symbol);
            pstmt.setDate(2, Date.valueOf(date));
            pstmt.setBigDecimal(3, parsePriceSafe(open, close));
            pstmt.setBigDecimal(4, parsePriceSafe(high, close));
            pstmt.setBigDecimal(5, parsePriceSafe(low, close));
            pstmt.setBigDecimal(6, parsePriceSafe(close, null));
            if (volume == null || volume.isBlank() || !volume.matches("[0-9]+")) {
                pstmt.setNull(7, Types.BIGINT);
            } else {
                pstmt.setLong(7, Long.parseLong(volume));
            }
            pstmt.executeUpdate();
        } catch (Exception e) {
            System.err.printf("saveBulkOhlcvRow 失敗（%s %s）: %s%n", symbol, date, e.getMessage());
        }
    }

    public long getLatestVolume(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return 0L;
        }
        String sql = "SELECT volume FROM STOCK_DATA WHERE symbol = ? ORDER BY trade_date DESC LIMIT 1";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, symbol);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getLong("volume") : 0L;
            }
        } catch (SQLException e) {
            System.err.println("查詢最新成交量失敗: " + e.getMessage());
            return 0L;
        }
    }

    public long calculateVolumeMA(String symbol, int days) {
        if (symbol == null || symbol.isBlank() || days <= 0) {
            return 0L;
        }
        String sql = "SELECT AVG(COALESCE(volume, 0)) FROM (" +
                "SELECT volume FROM STOCK_DATA WHERE symbol = ? ORDER BY trade_date DESC LIMIT ?)";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, symbol);
            pstmt.setInt(2, days);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? Math.round(rs.getDouble(1)) : 0L;
            }
        } catch (SQLException e) {
            System.err.println("計算成交量均線失敗: " + e.getMessage());
            return 0L;
        }
    }

    public void saveMonthlyHistory(String symbol, List<String[]> dataList) {
        if (symbol == null || symbol.isBlank() || dataList == null || dataList.isEmpty()) {
            return;
        }

        try (Connection conn = connectionProvider.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(stockOhlcvUpsertSql(conn))) {
                for (String[] row : dataList) {
                    addRowToBatch(pstmt, symbol, row);
                }
                pstmt.executeBatch();
                conn.commit();
            } catch (Exception e) {
                rollback(conn, e);
                throw new IllegalStateException("批次資料存入失敗", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("批次資料存入失敗", e);
        }
    }

    public List<StockDataPoint> getRecentHistory(String symbol, int days) {
        return getHistory(symbol, days, true);
    }

    public List<StockDataPoint> getFullHistory(String symbol, int days) {
        return getHistory(symbol, days, false);
    }

    private List<StockDataPoint> getHistory(String symbol, int days, boolean fillMissingOhlc) {
        if (symbol == null || symbol.isBlank() || days <= 0) {
            return List.of();
        }

        String open = fillMissingOhlc ? "COALESCE(open_price, close_price)" : "open_price";
        String high = fillMissingOhlc ? "COALESCE(high_price, close_price)" : "high_price";
        String low = fillMissingOhlc ? "COALESCE(low_price, close_price)" : "low_price";
        String sql = "SELECT * FROM (SELECT trade_date, " + open + " AS open_price, " +
                high + " AS high_price, " + low + " AS low_price, close_price, volume " +
                "FROM STOCK_DATA WHERE symbol = ? ORDER BY trade_date DESC LIMIT ?) " +
                "ORDER BY trade_date ASC";
        List<StockDataPoint> history = new ArrayList<>();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, symbol);
            pstmt.setInt(2, Math.max(days, days * 2));
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    LocalDate tradeDate = rs.getDate("trade_date").toLocalDate();
                    if (isLikelyTradingDate(tradeDate)) {
                        history.add(new StockDataPoint(
                                tradeDate.toString(),
                                rs.getDouble("open_price"),
                                rs.getDouble("high_price"),
                                rs.getDouble("low_price"),
                                rs.getDouble("close_price"),
                                rs.getLong("volume")));
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查詢歷史數據失敗", e);
        }

        if (history.size() <= days) {
            return history;
        }
        return new ArrayList<>(history.subList(history.size() - days, history.size()));
    }

    private void addRowToBatch(PreparedStatement pstmt, String symbol, String[] row) throws SQLException {
        if (row == null || row.length < 2) {
            return;
        }
        LocalDate tradeDate = convertToWesternDate(row[0]);
        if (tradeDate == null) {
            return;
        }

        String rawClose = row.length >= 7 ? row[6] : row[1];
        if (isMissing(rawClose)) {
            return;
        }
        String close = normalizeNumber(rawClose);
        String open = row.length >= 7 && !isMissing(row[3]) ? normalizeNumber(row[3]) : close;
        String high = row.length >= 7 && !isMissing(row[4]) ? normalizeNumber(row[4]) : close;
        String low = row.length >= 7 && !isMissing(row[5]) ? normalizeNumber(row[5]) : close;

        pstmt.setString(1, symbol);
        pstmt.setDate(2, Date.valueOf(tradeDate));
        pstmt.setBigDecimal(3, new BigDecimal(open));
        pstmt.setBigDecimal(4, new BigDecimal(high));
        pstmt.setBigDecimal(5, new BigDecimal(low));
        pstmt.setBigDecimal(6, new BigDecimal(close));
        if (row.length < 7 || isMissing(row[1])) {
            pstmt.setNull(7, Types.BIGINT);
        } else {
            pstmt.setLong(7, Long.parseLong(normalizeNumber(row[1])));
        }
        pstmt.addBatch();
    }

    private static LocalDate convertToWesternDate(String twDate) {
        if (twDate == null || twDate.isBlank()) {
            return null;
        }
        String[] parts = twDate.split("/");
        if (parts.length != 3) {
            return null;
        }
        try {
            return LocalDate.of(
                    Integer.parseInt(parts[0]) + 1911,
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean isMissing(String value) {
        return value == null || value.isBlank() || "--".equals(value);
    }

    private static String normalizeNumber(String value) {
        return value.replace(",", "");
    }

    private static BigDecimal parsePriceSafe(String value, String fallback) {
        if (value != null && !value.isBlank()) {
            try {
                return new BigDecimal(value.replace(",", ""));
            } catch (RuntimeException ignored) {
                // 改用 fallback。
            }
        }
        if (fallback != null && !fallback.isBlank()) {
            try {
                return new BigDecimal(fallback.replace(",", ""));
            } catch (RuntimeException ignored) {
                // 回傳零值。
            }
        }
        return BigDecimal.ZERO;
    }

    private static String stockOhlcvUpsertSql(Connection connection) throws SQLException {
        return DatabaseUpsertSql.build(
                connection,
                "STOCK_DATA",
                new String[]{"symbol", "trade_date", "open_price", "high_price",
                        "low_price", "close_price", "volume"},
                new String[]{"symbol", "trade_date"});
    }

    private static boolean isLikelyTradingDate(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    private static void rollback(Connection conn, Exception original) {
        try {
            conn.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }
}
