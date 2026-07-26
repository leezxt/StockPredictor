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
public class FinancialDataRepository {

    @FunctionalInterface
    interface ConnectionProvider {
        Connection getConnection() throws SQLException;
    }

    private final ConnectionProvider connectionProvider;

    @Autowired
    public FinancialDataRepository(DataSource dataSource) {
        this(dataSource::getConnection);
    }

    FinancialDataRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public void saveFinancialQuarterData(String symbol, FinancialQuarterData data) {
        if (symbol == null || symbol.isBlank() || data == null ||
                data.getQuarterDate() == null || data.getQuarterDate().isBlank()) {
            return;
        }
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(DatabaseUpsertSql.build(
                     conn,
                     "FINANCIAL_QUARTER_DATA",
                     new String[]{"symbol", "quarter_date", "gross_profit_margin",
                             "operating_profit_margin", "net_profit_margin",
                             "inventory_turnover_days", "contract_liabilities"},
                     new String[]{"symbol", "quarter_date"}))) {
            pstmt.setString(1, symbol.trim());
            pstmt.setDate(2, Date.valueOf(data.getQuarterDate()));
            pstmt.setDouble(3, data.getGrossProfitMargin());
            pstmt.setDouble(4, data.getOperatingProfitMargin());
            pstmt.setDouble(5, data.getNetProfitMargin());
            pstmt.setInt(6, data.getInventoryTurnoverDays());
            pstmt.setLong(7, data.getContractLiabilities());
            pstmt.executeUpdate();
        } catch (SQLException | IllegalArgumentException e) {
            System.err.println("寫入 FINANCIAL_QUARTER_DATA 失敗(" + symbol + "): " + e.getMessage());
        }
    }

    public List<FinancialQuarterData> getFinancialQuarterHistory(String symbol, int quarters) {
        if (symbol == null || symbol.isBlank() || quarters <= 0) {
            return List.of();
        }
        String sql = "SELECT * FROM (SELECT quarter_date, gross_profit_margin, " +
                "operating_profit_margin, net_profit_margin, inventory_turnover_days, " +
                "contract_liabilities FROM FINANCIAL_QUARTER_DATA WHERE symbol = ? " +
                "ORDER BY quarter_date DESC LIMIT ?) ORDER BY quarter_date ASC";
        List<FinancialQuarterData> history = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, symbol.trim());
            pstmt.setInt(2, quarters);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    history.add(new FinancialQuarterData(
                            rs.getDate("quarter_date").toString(),
                            rs.getDouble("gross_profit_margin"),
                            rs.getDouble("operating_profit_margin"),
                            rs.getDouble("net_profit_margin"),
                            rs.getInt("inventory_turnover_days"),
                            rs.getLong("contract_liabilities")));
                }
            }
        } catch (SQLException e) {
            System.err.println("查詢 FINANCIAL_QUARTER_DATA 失敗(" + symbol + "): " + e.getMessage());
        }
        return history;
    }
}
