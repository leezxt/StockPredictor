package org.gtalent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 當沖交易統計數據模型
 * FinMind Dataset: TaiwanStockDayTrading
 *
 * 核心應用場景：
 * • 當沖過熱率計算與風險識別
 * • 技術指標失真預測
 * • 當沖客戰場識別
 *
 * @author StockPredictor Team
 * @version 1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockDayTradingData {

    private String date;                      // 統計日期

    @JsonProperty("stock_id")
    private String stockId;                   // 股票代號

    private String name;                      // 股票名稱

    // ── 當沖交易相關 ───────────────────────
    /** 當沖成交張數 */
    @JsonProperty("day_trading_trading_volume")
    private Long dayTradingVolume;

    /** 當沖買進金額 */
    @JsonProperty("day_trading_buy_amount")
    private Long dayTradingBuyAmount;

    /** 當沖賣出金額 */
    @JsonProperty("day_trading_sell_amount")
    private Long dayTradingSellAmount;

    // ── 建構函數 ──────────────────────────
    public StockDayTradingData() {
    }

    // ── Getters ────────────────────────────
    public String getDate() { return date; }

    public String getStockId() { return stockId; }

    public String getName() { return name; }

    public Long getDayTradingVolume() { return dayTradingVolume; }

    public Long getDayTradingBuyAmount() { return dayTradingBuyAmount; }

    public Long getDayTradingSellAmount() { return dayTradingSellAmount; }

    // ── Setters ────────────────────────────
    public void setDate(String date) { this.date = date; }

    public void setStockId(String stockId) { this.stockId = stockId; }

    public void setName(String name) { this.name = name; }

    public void setDayTradingVolume(Long dayTradingVolume) { this.dayTradingVolume = dayTradingVolume; }

    public void setDayTradingBuyAmount(Long dayTradingBuyAmount) { this.dayTradingBuyAmount = dayTradingBuyAmount; }

    public void setDayTradingSellAmount(Long dayTradingSellAmount) { this.dayTradingSellAmount = dayTradingSellAmount; }

    // ── 業務相關方法 ───────────────────────

    /**
     * 計算當沖淨成交額
     *
     * @return 淨成交額
     */
    public Long calculateDayTradingNetAmount() {
        long buy = dayTradingBuyAmount != null ? dayTradingBuyAmount : 0;
        long sell = dayTradingSellAmount != null ? dayTradingSellAmount : 0;
        return buy - sell;
    }

    /**
     * 計算當沖均衡度
     * 買進金額 / 賣出金額，接近 1.0 表示越均衡
     *
     * @return 比例值
     */
    public Double calculateDayTradingRatio() {
        long buy = dayTradingBuyAmount != null ? dayTradingBuyAmount : 0;
        long sell = dayTradingSellAmount != null ? dayTradingSellAmount : 0;

        if (sell == 0) {
            return 0.0;
        }

        return (double) buy / sell;
    }

    /**
     * 判斷是否為當沖過熱
     * 當沖成交張數超過總成交量的特定比例
     *
     * @param totalVolume 當日總成交張數
     * @param hotThreshold 過熱閾值（%），通常 60-70%
     * @return true 如果當沖比例超過閾值，false 否則
     */
    public Boolean isDayTradingOverheated(Long totalVolume, Double hotThreshold) {
        if (dayTradingVolume == null || totalVolume == null || hotThreshold == null) {
            return false;
        }

        if (totalVolume == 0) {
            return false;
        }

        double ratio = (dayTradingVolume * 100.0) / totalVolume;
        return ratio > hotThreshold;
    }

    /**
     * 計算當沖比例
     *
     * @param totalVolume 當日總成交張數
     * @return 當沖比例百分比 (0-100)
     */
    public Double calculateDayTradingRatio(Long totalVolume) {
        if (dayTradingVolume == null || totalVolume == null || totalVolume == 0) {
            return 0.0;
        }

        return (dayTradingVolume * 100.0) / totalVolume;
    }

    /**
     * 計算當沖熱度指數
     * 用於判斷市場參與度
     *
     * @return 熱度指數 (0-100)
     */
    public Integer calculateDayTradingHeatIndex() {
        if (dayTradingVolume == null) {
            return 0;
        }

        // 簡化版：當沖成交量越高，熱度越高
        // 假設 > 100,000 張為滿分 100
        long volume = dayTradingVolume;
        return Math.min((int) (volume / 1000), 100);
    }

    /**
     * 獲取當沖市場狀態描述
     *
     * @param totalVolume 當日總成交張數
     * @return 狀態描述字符串
     */
    public String getDayTradingMarketStatus(Long totalVolume) {
        Double ratio = calculateDayTradingRatio(totalVolume);

        if (ratio < 20) {
            return "🟢 低熱度 - 機構主導市場";
        } else if (ratio < 40) {
            return "🟡 中低熱度 - 混合交易";
        } else if (ratio < 60) {
            return "🟠 中熱度 - 當沖活躍";
        } else if (ratio < 75) {
            return "🔴 高熱度 - 當沖主導";
        } else {
            return "🚀 極熱度 - 短線客炒作戰場";
        }
    }

    /**
     * 判斷是否應調整技術指標權重
     * 當沖過多時，KD、RSI 等技術指標容易失真
     *
     * @param totalVolume 當日總成交張數
     * @return 技術指標信任度 (0-100)，越低表示失真風險越高
     */
    public Integer evaluteTechnicalIndicatorValidity(Long totalVolume) {
        Double ratio = calculateDayTradingRatio(totalVolume);

        if (ratio < 30) {
            return 100;  // 完全信任
        } else if (ratio < 50) {
            return 70;   // 中等信任
        } else if (ratio < 70) {
            return 40;   // 低信任
        } else {
            return 10;   // 極低信任，強烈建議使用 MA 均線替代
        }
    }

    /**
     * 獲取當沖風控建議
     *
     * @param totalVolume 當日總成交張數
     * @return 建議描述字符串
     */
    public String getDayTradingRiskControl(Long totalVolume) {
        Double ratio = calculateDayTradingRatio(totalVolume);

        if (ratio < 40) {
            return "✅ 市場穩定，可信賴 KD/RSI 等技術指標";
        } else if (ratio < 60) {
            return "⚠️  當沖活躍，技術指標可信度中等，建議結合 MA 均線";
        } else if (ratio < 75) {
            return "🔶 當沖主導，技術指標失真風險高，改看 MA 支撐/阻力";
        } else {
            return "🚫 短線客炒作戰場，不宜過度頻繁追價，建議觀望或使用長線策略";
        }
    }
}

