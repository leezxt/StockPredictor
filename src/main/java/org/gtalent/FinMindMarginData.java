package org.gtalent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * FinMind 信用交易數據 DTO
 *
 * 用於接收 FinMind API 的融資融券信息。
 * Dataset: TaiwanStockMarginPurchaseShortSale
 *
 * <p><b>核心欄位</b>:
 * <ul>
 *   <li>融資相關: MarginPurchaseBuy / MarginPurchaseSell / MarginPurchaseLimit</li>
 *   <li>融券相關: ShortSaleBuy / ShortSaleSell / ShortSaleLimit</li>
 * </ul>
 *
 * <p><b>衍生計算</b>:
 * <ul>
 *   <li>資券比 = MarginPurchaseLimit / ShortSaleLimit (若分母為 0 則為 NaN)</li>
 *   <li>融資買賣超 = MarginPurchaseBuy - MarginPurchaseSell</li>
 *   <li>融券買賣超 = ShortSaleBuy - ShortSaleSell</li>
 * </ul>
 *
 * <p><b>API 回傳範例 (JSON)</b>:
 * <pre>
 * {
 *   "date": "2026-05-19",
 *   "stock_id": "2330",
 *   "MarginPurchaseBuy": 5000,
 *   "MarginPurchaseSell": 3000,
 *   "MarginPurchaseLimit": 150000,
 *   "ShortSaleBuy": 2000,
 *   "ShortSaleSell": 1500,
 *   "ShortSaleLimit": 80000
 * }
 * </pre>
 *
 * @author StockPredictor Team
 * @version 1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FinMindMarginData {

    /** 日期 (格式: YYYY-MM-DD) */
    private String date;

    /** 股票代號 */
    @JsonProperty("stock_id")
    private String stock_id;

    /** 今日融資買進 (張) */
    @JsonProperty("MarginPurchaseBuy")
    private long MarginPurchaseBuy;

    /** 今日融資賣出 (張) */
    @JsonProperty("MarginPurchaseSell")
    private long MarginPurchaseSell;

    /** 融資當日餘額 (張) - 報告日期之融資餘額 */
    @JsonProperty("MarginPurchaseLimit")
    private long MarginPurchaseLimit;

    /** 今日融券買進 (張) */
    @JsonProperty("ShortSaleBuy")
    private long ShortSaleBuy;

    /** 今日融券賣出 (張) */
    @JsonProperty("ShortSaleSell")
    private long ShortSaleSell;

    /** 融券當日餘額 (張) - 報告日期之融券餘額 */
    @JsonProperty("ShortSaleLimit")
    private long ShortSaleLimit;

    // ════════════════════════════════════════════════════════════
    //  建構子
    // ════════════════════════════════════════════════════════════

    public FinMindMarginData() {
    }

    public FinMindMarginData(String date, String stock_id,
                            long marginPurchaseBuy, long marginPurchaseSell, long marginPurchaseLimit,
                            long shortSaleBuy, long shortSaleSell, long shortSaleLimit) {
        this.date = date;
        this.stock_id = stock_id;
        this.MarginPurchaseBuy = marginPurchaseBuy;
        this.MarginPurchaseSell = marginPurchaseSell;
        this.MarginPurchaseLimit = marginPurchaseLimit;
        this.ShortSaleBuy = shortSaleBuy;
        this.ShortSaleSell = shortSaleSell;
        this.ShortSaleLimit = shortSaleLimit;
    }

    // ════════════════════════════════════════════════════════════
    //  Getters and Setters
    // ════════════════════════════════════════════════════════════

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getStock_id() {
        return stock_id;
    }

    public void setStock_id(String stock_id) {
        this.stock_id = stock_id;
    }

    public long getMarginPurchaseBuy() {
        return MarginPurchaseBuy;
    }

    public void setMarginPurchaseBuy(long marginPurchaseBuy) {
        MarginPurchaseBuy = marginPurchaseBuy;
    }

    public long getMarginPurchaseSell() {
        return MarginPurchaseSell;
    }

    public void setMarginPurchaseSell(long marginPurchaseSell) {
        MarginPurchaseSell = marginPurchaseSell;
    }

    public long getMarginPurchaseLimit() {
        return MarginPurchaseLimit;
    }

    public void setMarginPurchaseLimit(long marginPurchaseLimit) {
        MarginPurchaseLimit = marginPurchaseLimit;
    }

    public long getShortSaleBuy() {
        return ShortSaleBuy;
    }

    public void setShortSaleBuy(long shortSaleBuy) {
        ShortSaleBuy = shortSaleBuy;
    }

    public long getShortSaleSell() {
        return ShortSaleSell;
    }

    public void setShortSaleSell(long shortSaleSell) {
        ShortSaleSell = shortSaleSell;
    }

    public long getShortSaleLimit() {
        return ShortSaleLimit;
    }

    public void setShortSaleLimit(long shortSaleLimit) {
        ShortSaleLimit = shortSaleLimit;
    }

    // ════════════════════════════════════════════════════════════
    //  衍生計算方法
    // ════════════════════════════════════════════════════════════

    /**
     * 計算融資買賣超餘額。
     *
     * @return 融資買進 - 融資賣出 (張數)
     */
    public long getMarginPurchaseNetChange() {
        return MarginPurchaseBuy - MarginPurchaseSell;
    }

    /**
     * 計算融券買賣超餘額。
     *
     * @return 融券買進 - 融券賣出 (張數)
     */
    public long getShortSaleNetChange() {
        return ShortSaleBuy - ShortSaleSell;
    }

    /**
     * 計算資券比 (Margin/Short Sale Ratio)。
     *
     * <p><b>計算公式</b>: MarginPurchaseLimit / ShortSaleLimit
     *
     * <p><b>含意</b>:
     * <ul>
     *   <li>資券比 > 1.0 → 融資籌碼較多（主要買方）</li>
     *   <li>資券比 = 1.0 → 融資融券均等</li>
     *   <li>資券比 < 1.0 → 融券籌碼較多（主要賣方）</li>
     *   <li>資券比 = NaN → 融券餘額為 0（無法計算）</li>
     * </ul>
     *
     * @return 資券比 (若融券餘額為 0 則返回 Double.NaN)
     */
    public double getMarginShortRatio() {
        if (ShortSaleLimit <= 0) {
            return Double.NaN;
        }
        return (double) MarginPurchaseLimit / ShortSaleLimit;
    }

    /**
     * 計算融資餘額變化（與前一日進行比較時使用）。
     *
     * @param previousMarginLimit 前一日融資餘額
     * @return 融資餘額變化 (當日餘額 - 前日餘額)
     */
    public long getMarginDayChange(long previousMarginLimit) {
        return this.MarginPurchaseLimit - previousMarginLimit;
    }

    /**
     * 計算融券餘額變化（與前一日進行比較時使用）。
     *
     * @param previousShortLimit 前一日融券餘額
     * @return 融券餘額變化 (當日餘額 - 前日餘額)
     */
    public long getShortDayChange(long previousShortLimit) {
        return this.ShortSaleLimit - previousShortLimit;
    }

    // ════════════════════════════════════════════════════════════
    //  工具方法
    // ════════════════════════════════════════════════════════════

    @Override
    public String toString() {
        return "FinMindMarginData{" +
                "date='" + date + '\'' +
                ", stock_id='" + stock_id + '\'' +
                ", MarginPurchaseBuy=" + MarginPurchaseBuy +
                ", MarginPurchaseSell=" + MarginPurchaseSell +
                ", MarginPurchaseLimit=" + MarginPurchaseLimit +
                ", ShortSaleBuy=" + ShortSaleBuy +
                ", ShortSaleSell=" + ShortSaleSell +
                ", ShortSaleLimit=" + ShortSaleLimit +
                ", MarginShortRatio=" + String.format("%.2f", getMarginShortRatio()) +
                '}';
    }

    /**
     * 取得簡潔的摘要字符串。
     *
     * @return 格式: "2330 (融資: 150000張 | 融券: 80000張 | 資券比: 1.88)"
     */
    public String toSummaryString() {
        double ratio = getMarginShortRatio();
        String ratioStr = Double.isNaN(ratio) ? "N/A" : String.format("%.2f", ratio);
        return String.format("%s (融資: %,d 張 | 融券: %,d 張 | 資券比: %s)",
                stock_id, MarginPurchaseLimit, ShortSaleLimit, ratioStr);
    }
}

