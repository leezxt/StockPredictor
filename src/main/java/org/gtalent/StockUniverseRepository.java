package org.gtalent;

import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Repository
public class StockUniverseRepository {

    @FunctionalInterface
    interface ConnectionProvider {
        Connection getConnection() throws SQLException;
    }

    private final ConnectionProvider connectionProvider;

    @Autowired
    public StockUniverseRepository(DataSource dataSource) {
        this(dataSource::getConnection);
    }

    StockUniverseRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public List<String> getAllSymbols() {
        return getAllSymbols(null, null);
    }

    public List<String> getAllSymbolsWithData(int minRecords, String assetType, String market) {
        List<String> symbols = new ArrayList<>();
        String normalizedAssetType = normalizeFilter(assetType);
        String normalizedMarket = normalizeFilter(market);
        boolean hasFilter = !normalizedAssetType.isBlank() || !normalizedMarket.isBlank();
        String dataCte = "(SELECT symbol, COUNT(*) AS cnt FROM STOCK_DATA " +
                "GROUP BY symbol HAVING COUNT(*) >= ?) AS d";

        String sql;
        if (hasFilter) {
            sql = "SELECT u.symbol FROM STOCK_UNIVERSE u INNER JOIN " + dataCte +
                    " ON d.symbol = u.symbol WHERE u.active = TRUE" +
                    (normalizedAssetType.isBlank() ? "" : " AND UPPER(u.asset_type) = ?") +
                    (normalizedMarket.isBlank() ? "" : " AND UPPER(u.market) = ?") +
                    " ORDER BY u.symbol";
        } else {
            sql = "SELECT symbol FROM (" +
                    " SELECT u.symbol FROM STOCK_UNIVERSE u INNER JOIN " + dataCte +
                    " ON d.symbol = u.symbol WHERE u.active = TRUE" +
                    " UNION SELECT s.symbol FROM " +
                    "(SELECT symbol FROM STOCK_DATA GROUP BY symbol HAVING COUNT(*) >= ?) AS s" +
                    " LEFT JOIN STOCK_UNIVERSE u ON u.symbol = s.symbol" +
                    " WHERE u.symbol IS NULL OR u.active = TRUE) ORDER BY symbol";
        }

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            int index = 1;
            if (hasFilter) {
                pstmt.setInt(index++, minRecords);
                if (!normalizedAssetType.isBlank()) {
                    pstmt.setString(index++, normalizedAssetType);
                }
                if (!normalizedMarket.isBlank()) {
                    pstmt.setString(index, normalizedMarket);
                }
            } else {
                pstmt.setInt(index++, minRecords);
                pstmt.setInt(index, minRecords);
            }
            addSymbols(pstmt, symbols);
        } catch (SQLException e) {
            System.err.println("查詢有資料股票代碼清單失敗: " + e.getMessage());
        }
        return symbols;
    }

    public List<String> getAllSymbols(String assetType, String market) {
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
            sql = "SELECT symbol FROM (" +
                    " SELECT symbol FROM STOCK_UNIVERSE WHERE active = TRUE" +
                    " UNION SELECT DISTINCT s.symbol FROM STOCK_DATA s" +
                    " LEFT JOIN STOCK_UNIVERSE u ON u.symbol = s.symbol" +
                    " WHERE s.symbol IS NOT NULL AND s.symbol <> ''" +
                    " AND (u.symbol IS NULL OR u.active = TRUE)) ORDER BY symbol";
        }

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            int index = 1;
            if (hasFilter) {
                if (!normalizedAssetType.isBlank()) {
                    pstmt.setString(index++, normalizedAssetType);
                }
                if (!normalizedMarket.isBlank()) {
                    pstmt.setString(index, normalizedMarket);
                }
            }
            addSymbols(pstmt, symbols);
        } catch (SQLException e) {
            System.err.println("查詢股票代碼清單失敗: " + e.getMessage());
        }
        return symbols;
    }

    public int saveStockUniverse(List<StockUniverseEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return 0;
        }
        int count = 0;
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(DatabaseUpsertSql.build(
                     conn,
                     "STOCK_UNIVERSE",
                     new String[]{"symbol", "name", "market", "asset_type",
                             "is_etf", "active", "last_synced_at"},
                     new String[]{"symbol"}))) {
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
                pstmt.setTimestamp(7, Timestamp.from(Instant.now()));
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

    public int getStockUniverseCount() {
        String sql = "SELECT COUNT(*) FROM STOCK_UNIVERSE WHERE active = TRUE";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            System.err.println("查詢 STOCK_UNIVERSE 筆數失敗: " + e.getMessage());
            return 0;
        }
    }

    public StockUniverseEntry getStockUniverseEntry(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        String sql = "SELECT symbol, name, market, asset_type, is_etf, active " +
                "FROM STOCK_UNIVERSE WHERE symbol = ?";
        try (Connection conn = connectionProvider.getConnection();
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
                            rs.getBoolean("active"));
                }
            }
        } catch (SQLException e) {
            System.err.println("查詢 STOCK_UNIVERSE 單筆資料失敗: " + e.getMessage());
        }
        return null;
    }

    private static void addSymbols(PreparedStatement pstmt, List<String> symbols) throws SQLException {
        try (ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                symbols.add(rs.getString("symbol"));
            }
        }
    }

    private static String normalizeFilter(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}
