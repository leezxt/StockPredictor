package org.gtalent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 股權分散表數據模型
 * FinMind Dataset: TaiwanStockShareholding
 *
 * 核心應用場景：
 * • 千張大戶持股集中度分析
 * • 散戶被套牢程度判斷
 * • 籌碼極度集中識別
 *
 * @author StockPredictor Team
 * @version 1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockHoldingData {

    private String date;                      // 統計日期

    @JsonProperty("stock_id")
    private String stockId;                   // 股票代號

    private String name;                      // 股票名稱

    // ── 大戶控盤相關 ──────────────────────
    @JsonProperty("holding_stock_number")
    private Long holdingStockNumber;          // 持股張數

    @JsonProperty("holding_stock_holder")
    private Long holdingStockHolder;          // 持股人數

    @JsonProperty("holding_stock_ratio")
    private Double holdingStockRatio;         // 持股比例 (%)

    // ── 大戶層級分布 ──────────────────────
    /** 1000張以上大戶人數 */
    @JsonProperty("foreign_investor_1000_shares_over_number")
    private Long largeHoldersOverThousandCount;

    /** 1000張以上大戶持股比例 */
    @JsonProperty("foreign_investor_1000_shares_over_holding_ratio")
    private Double largeHoldersOverThousandRatio;

    // ── 散戶層級分布 ──────────────────────
    /** 10張以下散戶人數 */
    @JsonProperty("investor_10_shares_or_less_number")
    private Long smallHoldersUnderTenCount;

    /** 10張以下散戶持股比例 */
    @JsonProperty("investor_10_shares_or_less_holding_ratio")
    private Double smallHoldersUnderTenRatio;

    // ── 其他層級 ──────────────────────────
    /** 11-100張持股人數 */
    @JsonProperty("investor_11_100_shares_number")
    private Long middleHolders11To100Count;

    /** 11-100張持股比例 */
    @JsonProperty("investor_11_100_shares_holding_ratio")
    private Double middleHolders11To100Ratio;

    /** 101-1000張持股人數 */
    @JsonProperty("investor_101_1000_shares_number")
    private Long largeHolders101To1000Count;

    /** 101-1000張持股比例 */
    @JsonProperty("investor_101_1000_shares_holding_ratio")
    private Double largeHolders101To1000Ratio;

    // ── 建構函數 ──────────────────────────
    public StockHoldingData() {
    }

    // ── Getters ────────────────────────────
    public String getDate() { return date; }

    public String getStockId() { return stockId; }

    public String getName() { return name; }

    public Long getHoldingStockNumber() { return holdingStockNumber; }

    public Long getHoldingStockHolder() { return holdingStockHolder; }

    public Double getHoldingStockRatio() { return holdingStockRatio; }

    public Long getLargeHoldersOverThousandCount() { return largeHoldersOverThousandCount; }

    public Double getLargeHoldersOverThousandRatio() { return largeHoldersOverThousandRatio; }

    public Long getSmallHoldersUnderTenCount() { return smallHoldersUnderTenCount; }

    public Double getSmallHoldersUnderTenRatio() { return smallHoldersUnderTenRatio; }

    public Long getMiddleHolders11To100Count() { return middleHolders11To100Count; }

    public Double getMiddleHolders11To100Ratio() { return middleHolders11To100Ratio; }

    public Long getLargeHolders101To1000Count() { return largeHolders101To1000Count; }

    public Double getLargeHolders101To1000Ratio() { return largeHolders101To1000Ratio; }

    // ── Setters ────────────────────────────
    public void setDate(String date) { this.date = date; }

    public void setStockId(String stockId) { this.stockId = stockId; }

    public void setName(String name) { this.name = name; }

    public void setHoldingStockNumber(Long holdingStockNumber) { this.holdingStockNumber = holdingStockNumber; }

    public void setHoldingStockHolder(Long holdingStockHolder) { this.holdingStockHolder = holdingStockHolder; }

    public void setHoldingStockRatio(Double holdingStockRatio) { this.holdingStockRatio = holdingStockRatio; }

    public void setLargeHoldersOverThousandCount(Long largeHoldersOverThousandCount) { this.largeHoldersOverThousandCount = largeHoldersOverThousandCount; }

    public void setLargeHoldersOverThousandRatio(Double largeHoldersOverThousandRatio) { this.largeHoldersOverThousandRatio = largeHoldersOverThousandRatio; }

    public void setSmallHoldersUnderTenCount(Long smallHoldersUnderTenCount) { this.smallHoldersUnderTenCount = smallHoldersUnderTenCount; }

    public void setSmallHoldersUnderTenRatio(Double smallHoldersUnderTenRatio) { this.smallHoldersUnderTenRatio = smallHoldersUnderTenRatio; }

    public void setMiddleHolders11To100Count(Long middleHolders11To100Count) { this.middleHolders11To100Count = middleHolders11To100Count; }

    public void setMiddleHolders11To100Ratio(Double middleHolders11To100Ratio) { this.middleHolders11To100Ratio = middleHolders11To100Ratio; }

    public void setLargeHolders101To1000Count(Long largeHolders101To1000Count) { this.largeHolders101To1000Count = largeHolders101To1000Count; }

    public void setLargeHolders101To1000Ratio(Double largeHolders101To1000Ratio) { this.largeHolders101To1000Ratio = largeHolders101To1000Ratio; }

    // ── 業務相關方法 ───────────────────────

    /**
     * 計算大戶控盤度
     *
     * @return 千張大戶持股比例（%）
     */
    public Double calculateLargeHolderConcentration() {
        return largeHoldersOverThousandRatio != null ? largeHoldersOverThousandRatio : 0.0;
    }

    /**
     * 計算散戶被套度
     *
     * @return 10張以下散戶持股比例（%）
     */
    public Double calculateRetailTrappedRatio() {
        return smallHoldersUnderTenRatio != null ? smallHoldersUnderTenRatio : 0.0;
    }

    /**
     * 判斷籌碼是否極度集中
     *
     * @return true 如果千張大戶持股 > 50%，false 否則
     */
    public Boolean isChipHighlyConcentrated() {
        if (largeHoldersOverThousandRatio == null) {
            return false;
        }
        return largeHoldersOverThousandRatio > 50.0;
    }

    /**
     * 計算籌碼分散度評分
     * 簡單評分：大戶比例越高，分數越低；散戶比例越高，分數越高
     *
     * @return 分散度評分 (0-100)
     */
    public Integer calculateDisperseScore() {
        double largeRatio = largeHoldersOverThousandRatio != null ? largeHoldersOverThousandRatio : 0.0;
        double smallRatio = smallHoldersUnderTenRatio != null ? smallHoldersUnderTenRatio : 0.0;

        // 公式：散戶比例越高越好（權重60%），大戶比例越低越好（權重40%）
        double score = (smallRatio * 0.6) + ((100 - largeRatio) * 0.4);
        return Math.min((int) score, 100);
    }
}

