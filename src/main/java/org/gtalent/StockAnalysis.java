package org.gtalent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StockAnalysis {
    public String symbol;
    public double ma5;
    public double ma20;
    public String advice;

    public StockAnalysis(String symbol, double ma5, double ma20) {
        this.symbol = symbol;
        this.ma5 = ma5;
        this.ma20 = ma20;
        this.advice = (ma5 > ma20) ? "黃金交叉：看多" : "趨勢偏空";
    }

    public static double calculateRSI(String symbol, int period) {
        // 1. 從資料庫取得最近 (period + 1) 天的收盤價，由舊到新排序
        List<Double> prices = new ArrayList<>();
        String sql = "SELECT close_price FROM STOCK_DATA WHERE symbol = ? " +
                "ORDER BY trade_date DESC LIMIT ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, symbol);
            pstmt.setInt(2, period + 1);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    prices.add(rs.getDouble(1));
                }
            }
            Collections.reverse(prices); // 讓時間順序變為：舊 -> 新
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (prices.size() <= period) {
            return 50.0; // 資料不足時回傳中值
        }

        double avgGain = 0;
        double avgLoss = 0;

        // 2. 計算這段期間的總漲幅與總跌幅
        for (int i = 1; i < prices.size(); i++) {
            double difference = prices.get(i) - prices.get(i - 1);
            if (difference > 0) {
                avgGain += difference;
            } else {
                avgLoss += Math.abs(difference);
            }
        }

        // 3. 計算平均漲跌 (簡單移動平均法)
        avgGain /= period;
        avgLoss /= period;

        // 4. 計算 RSI
        if (avgLoss == 0) {
            return 100.0; // 如果期間內都沒跌過，RSI 為 100
        }

        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }
}
