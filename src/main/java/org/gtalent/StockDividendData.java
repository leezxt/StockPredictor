package org.gtalent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 股利政策與除權息數據模型
 * FinMind Dataset: TaiwanStockDividend
 *
 * 核心應用場景：
 * • 殖利率計算與高息股防守評估
 * • 除權息行情預判
 * • 長線資金催化劑識別
 *
 * @author StockPredictor Team
 * @version 1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockDividendData {

    @JsonProperty("stock_id")
    private String stockId;                   // 股票代號

    private String name;                      // 股票名稱

    // ── 股利相關 ───────────────────────────
    /** 配息年度 */
    @JsonProperty("year")
    private Integer year;

    /** 現金股利（元）*/
    @JsonProperty("cash_dividend")
    private Double cashDividend;

    /** 股票股利（元）*/
    @JsonProperty("stock_dividend")
    private Double stockDividend;

    /** 總股利（現金 + 股票，以當時股價換算）*/
    @JsonProperty("total_divididend")
    private Double totalDividend;

    // ── 除權息日期相關 ────────────────────
    /** 除息交易日 */
    @JsonProperty("ex_dividend_date")
    private String exDividendDate;

    /** 除權交易日 */
    @JsonProperty("ex_right_date")
    private String exRightDate;

    /** 現金股利發放日 */
    @JsonProperty("cash_dividend_payment_date")
    private String cashDividendPaymentDate;

    // ── 配息政策相關 ─────────────────────
    /** 配發年度是否曾配息 */
    @JsonProperty("has_dividend")
    private Boolean hasDividend;

    /** 配息月份 */
    @JsonProperty("dividend_month")
    private Integer dividendMonth;

    // ── 建構函數 ──────────────────────────
    public StockDividendData() {
    }

    // ── Getters ────────────────────────────
    public String getStockId() { return stockId; }

    public String getName() { return name; }

    public Integer getYear() { return year; }

    public Double getCashDividend() { return cashDividend; }

    public Double getStockDividend() { return stockDividend; }

    public Double getTotalDividend() { return totalDividend; }

    public String getExDividendDate() { return exDividendDate; }

    public String getExRightDate() { return exRightDate; }

    public String getCashDividendPaymentDate() { return cashDividendPaymentDate; }

    public Boolean getHasDividend() { return hasDividend; }

    public Integer getDividendMonth() { return dividendMonth; }

    // ── Setters ────────────────────────────
    public void setStockId(String stockId) { this.stockId = stockId; }

    public void setName(String name) { this.name = name; }

    public void setYear(Integer year) { this.year = year; }

    public void setCashDividend(Double cashDividend) { this.cashDividend = cashDividend; }

    public void setStockDividend(Double stockDividend) { this.stockDividend = stockDividend; }

    public void setTotalDividend(Double totalDividend) { this.totalDividend = totalDividend; }

    public void setExDividendDate(String exDividendDate) { this.exDividendDate = exDividendDate; }

    public void setExRightDate(String exRightDate) { this.exRightDate = exRightDate; }

    public void setCashDividendPaymentDate(String cashDividendPaymentDate) { this.cashDividendPaymentDate = cashDividendPaymentDate; }

    public void setHasDividend(Boolean hasDividend) { this.hasDividend = hasDividend; }

    public void setDividendMonth(Integer dividendMonth) { this.dividendMonth = dividendMonth; }

    // ── 業務相關方法 ───────────────────────

    /**
     * 計算殖利率
     *
     * @param currentPrice 當前股價
     * @return 殖利率百分比
     */
    public Double calculateYieldRate(Double currentPrice) {
        if (currentPrice == null || currentPrice == 0 || cashDividend == null) {
            return 0.0;
        }
        return (cashDividend / currentPrice) * 100.0;
    }

    /**
     * 判斷是否為高殖利率股
     *
     * @param currentPrice 當前股價
     * @param highYieldThreshold 高息股閾值（%），通常 4-6%
     * @return true 如果殖利率超過閾值，false 否則
     */
    public Boolean isHighYieldStock(Double currentPrice, Double highYieldThreshold) {
        Double yieldRate = calculateYieldRate(currentPrice);
        return yieldRate >= highYieldThreshold;
    }

    /**
     * 獲取最高現金股利
     * 用於長期統計
     *
     * @return 現金股利金額
     */
    public Double getCashDividendAmount() {
        return cashDividend != null ? cashDividend : 0.0;
    }

    /**
     * 判斷是否為穩定配息股
     * 通常連年配息的優質企業
     *
     * @return true 如果有配息記錄，false 否則
     */
    public Boolean isStableDividendStock() {
        return hasDividend != null && hasDividend;
    }

    /**
     * 計算未來除權息倒數天數
     * 假設知道 exDividendDate
     *
     * @param currentDate 當前日期（格式：yyyy-MM-dd）
     * @return 距離除權息日剩餘天數，負數表示已過期
     */
    public Long daysUntilExDividend(String currentDate) {
        if (exDividendDate == null || currentDate == null) {
            return null;
        }

        try {
            java.time.LocalDate current = java.time.LocalDate.parse(currentDate);
            java.time.LocalDate exDate = java.time.LocalDate.parse(exDividendDate);
            return java.time.temporal.ChronoUnit.DAYS.between(current, exDate);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 判斷是否在除權息前夕（作帳期）
     * 通常除權息前 1 個月內
     *
     * @param currentDate 當前日期
     * @return true 如果在作帳期內，false 否則
     */
    public Boolean isInDividendActingPeriod(String currentDate) {
        Long daysUntil = daysUntilExDividend(currentDate);
        if (daysUntil == null) {
            return false;
        }
        // 除權息前 1 個月內（30 天內）
        return daysUntil >= 0 && daysUntil <= 30;
    }

    /**
     * 計算股利總額
     * 現金股利 + 股票股利（換算為當時股價）
     *
     * @return 總股利金額
     */
    public Double calculateTotalDividendAmount() {
        double cash = cashDividend != null ? cashDividend : 0.0;
        double stock = stockDividend != null ? stockDividend : 0.0;
        return cash + stock;
    }

    /**
     * 獲取股利評分
     * 評分基準：現金股利越高分越高
     *
     * @return 評分 (0-100)
     */
    public Integer calculateDividendScore() {
        if (cashDividend == null) {
            return 0;
        }

        // 簡化版評分：現金股利越高，分數越高
        // 假設 5 元以上為滿分 100
        int score = (int) (Math.min(cashDividend, 5.0) / 5.0 * 100);
        return Math.min(score, 100);
    }
}

