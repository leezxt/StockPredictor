package org.gtalent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 融資融券與借券賣出數據模型
 * FinMind Dataset: TaiwanStockMarginPurchaseShortSale
 *
 * 核心應用場景：
 * • 融資死結識別（股價下跌融資暴增 = 散戶套牢）
 * • 空頭壓力分析（借券賣出高位 = 軋空預警）
 * • 軋空火箭預警（技術面 + 借券賣出 = 爆發信號）
 *
 * @author StockPredictor Team
 * @version 1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarginPurchaseShortSaleData {

    private String date;                      // 統計日期

    @JsonProperty("stock_id")
    private String stockId;                   // 股票代號

    private String name;                      // 股票名稱

    // ── 融資相關 ───────────────────────────
    /** 融資買進張數（日）*/
    @JsonProperty("margin_purchase_today")
    private Long marginPurchaseToday;

    /** 融資賣出張數（日）*/
    @JsonProperty("margin_sale_today")
    private Long marginSaleToday;

    /** 融資餘額（累積未還）*/
    @JsonProperty("margin_purchase_balance")
    private Long marginPurchaseBalance;

    // ── 融券相關 ───────────────────────────
    /** 融券賣出張數（日）*/
    @JsonProperty("short_sale_today")
    private Long shortSaleToday;

    /** 融券買進張數（日）*/
    @JsonProperty("short_sale_buy_today")
    private Long shortSaleBuyToday;

    /** 融券餘額（累積未歸還）*/
    @JsonProperty("short_sale_balance")
    private Long shortSaleBalance;

    // ── 借券賣出相關 ─────────────────────
    /** 借券賣出張數（日）*/
    @JsonProperty("borrow_sell_today")
    private Long borrowSellToday;

    /** 借券買進張數（日）*/
    @JsonProperty("borrow_sell_buy_today")
    private Long borrowSellBuyToday;

    /** 借券賣出餘額（累積）*/
    @JsonProperty("borrow_sell_balance")
    private Long borrowSellBalance;

    // ── 建構函數 ──────────────────────────
    public MarginPurchaseShortSaleData() {
    }

    // ── Getters ────────────────────────────
    public String getDate() { return date; }

    public String getStockId() { return stockId; }

    public String getName() { return name; }

    public Long getMarginPurchaseToday() { return marginPurchaseToday; }

    public Long getMarginSaleToday() { return marginSaleToday; }

    public Long getMarginPurchaseBalance() { return marginPurchaseBalance; }

    public Long getShortSaleToday() { return shortSaleToday; }

    public Long getShortSaleBuyToday() { return shortSaleBuyToday; }

    public Long getShortSaleBalance() { return shortSaleBalance; }

    public Long getBorrowSellToday() { return borrowSellToday; }

    public Long getBorrowSellBuyToday() { return borrowSellBuyToday; }

    public Long getBorrowSellBalance() { return borrowSellBalance; }

    // ── Setters ────────────────────────────
    public void setDate(String date) { this.date = date; }

    public void setStockId(String stockId) { this.stockId = stockId; }

    public void setName(String name) { this.name = name; }

    public void setMarginPurchaseToday(Long marginPurchaseToday) { this.marginPurchaseToday = marginPurchaseToday; }

    public void setMarginSaleToday(Long marginSaleToday) { this.marginSaleToday = marginSaleToday; }

    public void setMarginPurchaseBalance(Long marginPurchaseBalance) { this.marginPurchaseBalance = marginPurchaseBalance; }

    public void setShortSaleToday(Long shortSaleToday) { this.shortSaleToday = shortSaleToday; }

    public void setShortSaleBuyToday(Long shortSaleBuyToday) { this.shortSaleBuyToday = shortSaleBuyToday; }

    public void setShortSaleBalance(Long shortSaleBalance) { this.shortSaleBalance = shortSaleBalance; }

    public void setBorrowSellToday(Long borrowSellToday) { this.borrowSellToday = borrowSellToday; }

    public void setBorrowSellBuyToday(Long borrowSellBuyToday) { this.borrowSellBuyToday = borrowSellBuyToday; }

    public void setBorrowSellBalance(Long borrowSellBalance) { this.borrowSellBalance = borrowSellBalance; }

    // ── 業務相關方法 ───────────────────────

    /**
     * 計算當日融資淨買進（融資買進 - 融資賣出）
     *
     * @return 淨買進張數
     */
    public Long calculateMarginNetBuy() {
        long buy = marginPurchaseToday != null ? marginPurchaseToday : 0;
        long sell = marginSaleToday != null ? marginSaleToday : 0;
        return buy - sell;
    }

    /**
     * 計算當日融券淨賣出（融券賣出 - 融券買進）
     *
     * @return 淨賣出張數
     */
    public Long calculateShortSaleNetSell() {
        long sell = shortSaleToday != null ? shortSaleToday : 0;
        long buy = shortSaleBuyToday != null ? shortSaleBuyToday : 0;
        return sell - buy;
    }

    /**
     * 計算借券淨賣出（借券賣出 - 借券買進）
     *
     * @return 淨賣出張數
     */
    public Long calculateBorrowSellNet() {
        long sell = borrowSellToday != null ? borrowSellToday : 0;
        long buy = borrowSellBuyToday != null ? borrowSellBuyToday : 0;
        return sell - buy;
    }

    /**
     * 判斷是否存在融資死結
     * 融資死結：融資餘額偏高，通常代表散戶套牢
     *
     * @param highBalanceThreshold 融資高位閾值（張數）
     * @return true 如果融資餘額超過閾值，false 否則
     */
    public Boolean hasMarginDeadlock(Long highBalanceThreshold) {
        if (marginPurchaseBalance == null || highBalanceThreshold == null) {
            return false;
        }
        return marginPurchaseBalance > highBalanceThreshold;
    }

    /**
     * 判斷是否存在軋空風險
     * 軋空風險：借券賣出餘額處於歷史高位
     *
     * @param squeezeThreshold 軋空風險閾值（張數）
     * @return true 如果借券賣出餘額超過閾值，false 否則
     */
    public Boolean isSqueezeRisk(Long squeezeThreshold) {
        if (borrowSellBalance == null || squeezeThreshold == null) {
            return false;
        }
        return borrowSellBalance > squeezeThreshold;
    }

    /**
     * 計算融資使用率
     * 使用率 = 融資餘額 / (融資買進 + 融資賣出) 的比例
     *
     * @return 使用率百分比，訊號為 null 則返回 0
     */
    public Double calculateMarginUtilizationRate() {
        long balance = marginPurchaseBalance != null ? marginPurchaseBalance : 0;
        long netDaily = Math.abs(calculateMarginNetBuy());

        if (netDaily == 0) {
            return 0.0;
        }

        return (balance * 100.0) / netDaily;
    }

    /**
     * 獲取融資壓力指數
     * 指數計算：融資餘額越高，壓力越大
     *
     * @return 壓力指數 (0-100)
     */
    public Integer calculateMarginPressureIndex() {
        if (marginPurchaseBalance == null) {
            return 0;
        }

        // 簡化版：餘額越高，壓力越大
        // 假設 > 100,000 張為滿分 100 分
        long balance = marginPurchaseBalance;
        return Math.min((int) (balance / 1000), 100);
    }
}

