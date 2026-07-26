package org.gtalent;

import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Repository
public class MarketDataRepository {

    @FunctionalInterface
    interface ConnectionProvider {
        Connection getConnection() throws SQLException;
    }

    private final ConnectionProvider connectionProvider;
    private final Clock clock;

    @Autowired
    public MarketDataRepository(DataSource dataSource) {
        this(dataSource::getConnection, Clock.systemDefaultZone());
    }

    MarketDataRepository(ConnectionProvider connectionProvider, Clock clock) {
        this.connectionProvider = connectionProvider;
        this.clock = clock;
    }

    public void saveDailyMarketBreadth(MarketBreadthResult result) {
        if (result == null) {
            return;
        }
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(DatabaseUpsertSql.build(
                     conn,
                     "MARKET_BREADTH",
                     new String[]{"trade_date", "breadth", "eligible_count", "bullish_count"},
                     new String[]{"trade_date"}))) {
            pstmt.setDate(1, Date.valueOf(LocalDate.now(clock)));
            pstmt.setDouble(2, result.getBreadth());
            pstmt.setInt(3, result.getEligibleCount());
            pstmt.setInt(4, result.getBullishCount());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("寫入 MARKET_BREADTH 失敗: " + e.getMessage());
        }
    }

    public List<MarketBreadthSnapshot> getMarketBreadthHistory(int days) {
        if (days <= 0) {
            return List.of();
        }
        String sql = "SELECT * FROM (SELECT trade_date, breadth, eligible_count, bullish_count " +
                "FROM MARKET_BREADTH ORDER BY trade_date DESC LIMIT ?) ORDER BY trade_date ASC";
        List<MarketBreadthSnapshot> history = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, days);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    history.add(new MarketBreadthSnapshot(
                            rs.getDate("trade_date").toString(),
                            rs.getDouble("breadth"),
                            rs.getInt("eligible_count"),
                            rs.getInt("bullish_count")));
                }
            }
        } catch (SQLException e) {
            System.err.println("查詢 MARKET_BREADTH 歷史失敗: " + e.getMessage());
        }
        return history;
    }

    public void saveRevenueData(String symbol, RevenueData revenueData) {
        if (symbol == null || symbol.isBlank() || revenueData == null ||
                revenueData.getYearMonth() == null || revenueData.getYearMonth().isBlank()) {
            return;
        }
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(DatabaseUpsertSql.build(
                     conn,
                     "MONTHLY_REVENUE",
                     new String[]{"symbol", "year_month", "revenue", "mom", "yoy"},
                     new String[]{"symbol", "year_month"}))) {
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

    public List<RevenueData> getRevenueHistory(String symbol, int months) {
        if (symbol == null || symbol.isBlank() || months <= 0) {
            return List.of();
        }
        String sql = "SELECT * FROM (SELECT year_month, revenue, mom, yoy FROM MONTHLY_REVENUE " +
                "WHERE symbol = ? ORDER BY year_month DESC LIMIT ?) ORDER BY year_month ASC";
        List<RevenueData> history = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, symbol.trim());
            pstmt.setInt(2, months);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    history.add(new RevenueData(
                            rs.getString("year_month"),
                            rs.getDouble("revenue"),
                            rs.getDouble("mom"),
                            rs.getDouble("yoy")));
                }
            }
        } catch (SQLException e) {
            System.err.println("查詢 MONTHLY_REVENUE 歷史失敗: " + e.getMessage());
        }
        return history;
    }
}
