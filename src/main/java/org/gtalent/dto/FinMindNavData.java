package org.gtalent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * FinMind ETF 淨值數據 DTO
 *
 * FinMind API ETF淨值端點回應的數據模型。
 * 用於接收 ETF 歷史淨值、計算折價溢價、追蹤 ETF 風險。
 *
 * API 示例：
 * GET /TaiwanETFNavigation?data_id=0050&start_date=2026-05-01&end_date=2026-05-28
 *
 * 回應格式：
 * {
 *   "data": [
 *     {
 *       "date": "2026-05-28",
 *       "stock_id": "0050",
 *       "NAV": 50.25
 *     }
 *   ]
 * }
 */
public class FinMindNavData {
    @JsonProperty("date")
    private String date;           // 日期 (YYYY-MM-DD)

    @JsonProperty("stock_id")
    private String stockId;        // ETF 代碼 (如 0050、0056、00919)

    @JsonProperty("NAV")
    private double nav;            // 淨資產價值 (Net Asset Value)

    public FinMindNavData() {
    }

    public FinMindNavData(String date, String stockId, double nav) {
        this.date = date;
        this.stockId = stockId;
        this.nav = nav;
    }

    // ──── Getters ────
    public String getDate() {
        return date;
    }

    public String getStockId() {
        return stockId;
    }

    public double getNav() {
        return nav;
    }

    // ──── Setters ────
    public void setDate(String date) {
        this.date = date;
    }

    public void setStockId(String stockId) {
        this.stockId = stockId;
    }

    public void setNav(double nav) {
        this.nav = nav;
    }

    // ──── 輔助方法 ────

    /**
     * 計算折價溢價比例
     *
     * @param currentPrice 當前市價
     * @return 折價溢價比例（-0.05 表示折五，+0.03 表示溢三）
     */
    public double calculatePremiumDiscount(double currentPrice) {
        if (nav <= 0) return 0.0;
        return (currentPrice - nav) / nav;
    }

    /**
     * 格式化淨值顯示
     *
     * @return 格式化字符串 "2026-05-28 | 0050 | NAV: 50.25"
     */
    public String format() {
        return String.format("%s | %s | NAV: %.2f", date, stockId, nav);
    }

    @Override
    public String toString() {
        return "FinMindNavData{" +
                "date='" + date + '\'' +
                ", stockId='" + stockId + '\'' +
                ", nav=" + nav +
                '}';
    }
}

