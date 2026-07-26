package org.gtalent;

import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Repository
public class InstitutionalDataRepository {

    @FunctionalInterface
    interface ConnectionProvider {
        Connection getConnection() throws SQLException;
    }

    private final ConnectionProvider connectionProvider;

    @Autowired
    public InstitutionalDataRepository(DataSource dataSource) {
        this(dataSource::getConnection);
    }

    InstitutionalDataRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public int saveLargeHolderShareholding(String symbol, List<FinMindShareholdingData> rows) {
        if (invalidRows(symbol, rows)) {
            return 0;
        }
        int count = 0;
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(DatabaseUpsertSql.build(
                     conn,
                     "FINMIND_SHAREHOLDING",
                     new String[]{"symbol", "trade_date", "holding_factor",
                             "shareholder_count", "shares", "percentage"},
                     new String[]{"symbol", "trade_date", "holding_factor"}))) {
            for (FinMindShareholdingData row : rows) {
                if (row == null || blank(row.getDate()) || row.getHoldingFactor() != 15) {
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
        } catch (SQLException | IllegalArgumentException e) {
            System.err.println("寫入 FINMIND_SHAREHOLDING 失敗: " + e.getMessage());
            return 0;
        }
    }

    public int saveDayTradingData(String symbol, List<FinMindDayTradingData> rows) {
        if (invalidRows(symbol, rows)) {
            return 0;
        }
        int count = 0;
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(DatabaseUpsertSql.build(
                     conn,
                     "FINMIND_DAY_TRADING",
                     new String[]{"symbol", "trade_date", "buy_amount", "sell_amount",
                             "day_trading_volume", "day_trading_rate"},
                     new String[]{"symbol", "trade_date"}))) {
            for (FinMindDayTradingData row : rows) {
                if (row == null || blank(row.getDate())) {
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
        } catch (SQLException | IllegalArgumentException e) {
            System.err.println("寫入 FINMIND_DAY_TRADING 失敗: " + e.getMessage());
            return 0;
        }
    }

    public List<FinMindDayTradingData> getDayTradingHistory(String symbol, int days) {
        if (blank(symbol) || days <= 0) {
            return List.of();
        }
        String sql = "SELECT * FROM (SELECT trade_date, buy_amount, sell_amount, " +
                "day_trading_volume, day_trading_rate FROM FINMIND_DAY_TRADING WHERE symbol = ? " +
                "ORDER BY trade_date DESC LIMIT ?) ORDER BY trade_date ASC";
        List<FinMindDayTradingData> result = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, symbol.trim());
            pstmt.setInt(2, days);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    FinMindDayTradingData item = new FinMindDayTradingData();
                    item.setDate(rs.getDate("trade_date").toString());
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

    public List<FinMindShareholdingData> getLargeHolderShareholdingHistory(String symbol, int weeks) {
        if (blank(symbol) || weeks <= 0) {
            return List.of();
        }
        String sql = "SELECT * FROM (SELECT trade_date, holding_factor, shareholder_count, shares, percentage " +
                "FROM FINMIND_SHAREHOLDING WHERE symbol = ? AND holding_factor = 15 " +
                "ORDER BY trade_date DESC LIMIT ?) ORDER BY trade_date ASC";
        List<FinMindShareholdingData> result = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, symbol.trim());
            pstmt.setInt(2, weeks);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    FinMindShareholdingData item = new FinMindShareholdingData();
                    item.setDate(rs.getDate("trade_date").toString());
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

    public int saveInstitutionalTrades(String symbol, List<InstitutionalTrade> trades) {
        if (invalidRows(symbol, trades)) {
            return 0;
        }
        int count = 0;
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(DatabaseUpsertSql.build(
                     conn,
                     "INSTITUTIONAL_DATA",
                     new String[]{"symbol", "trade_date", "foreign_buy", "trust_buy", "dealer_buy"},
                     new String[]{"symbol", "trade_date"}))) {
            for (InstitutionalTrade trade : trades) {
                if (trade == null || blank(trade.getDate())) {
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
            if (count > 0) {
                pstmt.executeBatch();
            }
            return count;
        } catch (SQLException | IllegalArgumentException e) {
            System.err.println("寫入法人資料失敗: " + e.getMessage());
            return 0;
        }
    }

    public List<InstitutionalTrade> getRecentInstitutionalTrades(String symbol, int days) {
        if (blank(symbol) || days <= 0) {
            return List.of();
        }
        String sql = "SELECT i.trade_date, i.foreign_buy, i.trust_buy, i.dealer_buy, " +
                "COALESCE(s.volume, 0) AS daily_volume FROM (" +
                "SELECT trade_date, foreign_buy, trust_buy, dealer_buy FROM INSTITUTIONAL_DATA " +
                "WHERE symbol = ? ORDER BY trade_date DESC LIMIT ?) i " +
                "LEFT JOIN STOCK_DATA s ON s.symbol = ? AND s.trade_date = i.trade_date " +
                "ORDER BY i.trade_date ASC";
        List<InstitutionalTrade> result = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
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
                            rs.getLong("daily_volume")));
                }
            }
        } catch (SQLException e) {
            System.err.println("查詢法人資料失敗: " + e.getMessage());
        }
        return result;
    }

    public double getAverageInstitutionalNetBuy(String symbol, int days) {
        if (blank(symbol) || days <= 0) {
            return 0.0;
        }
        String sql = "SELECT AVG(net) FROM (SELECT foreign_buy + trust_buy + dealer_buy AS net " +
                "FROM INSTITUTIONAL_DATA WHERE symbol = ? ORDER BY trade_date DESC LIMIT ?) AS t";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, symbol);
            pstmt.setInt(2, days);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        } catch (SQLException e) {
            System.err.println("計算法人平均淨買超失敗: " + e.getMessage());
            return 0.0;
        }
    }

    public int countConsecutiveTrustBuyDays(String symbol, int maxDays) {
        if (blank(symbol) || maxDays <= 0) {
            return 0;
        }
        String sql = "SELECT trust_buy FROM INSTITUTIONAL_DATA " +
                "WHERE symbol = ? ORDER BY trade_date DESC LIMIT ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, symbol);
            pstmt.setInt(2, maxDays);
            int count = 0;
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next() && rs.getLong("trust_buy") > 0) {
                    count++;
                }
            }
            return count;
        } catch (SQLException e) {
            System.err.println("計算投信連買天數失敗: " + e.getMessage());
            return 0;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean invalidRows(String symbol, List<?> rows) {
        return blank(symbol) || rows == null || rows.isEmpty();
    }
}
