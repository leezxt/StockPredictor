package org.gtalent.dto;

import org.gtalent.RadarTacticalAnalyzer;

/**
 * 後端算分引擎與前端雷達圖渲染的標準通訊協定。
 * 所有分數欄位設計為 0~100；防禦機制會將分數打壓至 0（一票否決）或乘以降噪係數。
 *
 * 六大軸向量化公式：
 * A. 基本面：三率三升（毛利/利益/淨利 QoQ+YoY）、合約負債攀升、存貨週轉天數創低
 * B. 技術面：KD 中軸黃金交叉（+40%）、KD 低檔底背離（+40%）；當沖率 >55% 時乘以 0.4
 * C. 波動爆發：BBW 擠壓至歷史極低點（+60%）、股價突破上軌（+40%）
 * D. 風控基期：52週相對位置計算；位置 >40% 或股價 >400 元時直接歸 0（一票否決）
 * E. 籌碼結構：融資連續遞減（+40%）、資券比 >30% 且融券增（+40%）、千張大戶上升（+20%）
 * F. 消息輿情：新聞情緒極性 + 非對稱回應（NRI）；利多出盡時熔斷至 -100（前端顯示 0，觸發警告）
 */
public class RadarConfigDto {
    private String symbol;
    private String name;
    private boolean isEtf;

    // 六角雷達圖核心組態數據（所有分數 0~100，防禦機制會觸發 0 或降噪）
    private int fundamentals;   // A. 基本面
    private int technicals;     // B. 技術面
    private int volatility;     // C. 波動爆發
    private int riskMargin;     // D. 風控基期
    private int microstructure; // E. 籌碼結構
    private int sentiment;      // F. 消息輿情

    // 量化元資料（供前端明細展示與防禦機制狀態檢查）
    private QuantitativeDetails quantDetails;

    // 戰術分析結果（根據六軸分佈形狀判定操盤策略）
    private RadarTacticalAnalyzer.TacticalAnalysisResult tacticalAnalysis;

    // ETF 專屬評估欄位
    private double netAssetValue;           // ETF 淨值 (NAV)
    private double premium;                 // 折溢價比例 (%)；負值=折價，正值=溢價
    private String netAssetValueSource;     // 淨值數據來源（"FinMind / 臺灣證券交易所" 或 "NOT_AVAILABLE"）

    /**
     * 量化公式的中間計算結果與防禦機制狀態
     */
    public static class QuantitativeDetails {
        // D. 風控基期相關
        public double priceLocation;         // (現價 - 52週最低) / (52週最高 - 52週最低)，範圍 0~1
        public double currentPrice;          // 當前股價（用於 >400 元過濾）
        public boolean riskMarginVeto;       // 一票否決：位置 >40% 或股價 >400，此軸直接歸 0

        // B. 技術面相關
        public double dayTradingRate;        // 當沖率（0~1），>55% 時此軸乘以 0.4
        public double technicalNoiseFactor;  // 降噪係數：當沖率 >55% 時為 0.4，否則 1.0

        // C. 波動爆發相關
        public double bbwLevel;              // BBW 壓縮度 (0~1)，接近 0 表極端擠壓
        public boolean bbwCompressionAlert;  // BBW 是否達歷史極低點（準備變盤警示）

        // E. 籌碼結構相關
        public double marginDecreaseDays;    // 融資連續遞減天數
        public double marginShortRatio;      // 資券比（%）
        public boolean marginShortSqueeze;   // 軋空組態：資券比 >30% 且融券增
        public double largeHolderRatio;      // 千張大戶持股比（%）

        // F. 消息輿情相關
        public double newssentiment;         // 新聞情緒極性 (-1~1)
        public double nri;                   // 非對稱回應指數 (-1~1)；負值表利多出盡
        public boolean sentimentMeltdown;    // 熔斷標記：利多出盡，此軸直接歸 0，觸發警告

        // A. 基本面相關
        public boolean tripleRiseActive;     // 三率三升是否同時成立
        public double contractLiabilityGrowth; // 合約負債成長率
        public double inventoryTuroverDays;  // 存貨週轉天數

        public QuantitativeDetails() {
        }

        // Getters and Setters
        public double getPriceLocation() { return priceLocation; }
        public void setPriceLocation(double priceLocation) { this.priceLocation = priceLocation; }

        public double getCurrentPrice() { return currentPrice; }
        public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

        public boolean isRiskMarginVeto() { return riskMarginVeto; }
        public void setRiskMarginVeto(boolean riskMarginVeto) { this.riskMarginVeto = riskMarginVeto; }

        public double getDayTradingRate() { return dayTradingRate; }
        public void setDayTradingRate(double dayTradingRate) { this.dayTradingRate = dayTradingRate; }

        public double getTechnicalNoiseFactor() { return technicalNoiseFactor; }
        public void setTechnicalNoiseFactor(double technicalNoiseFactor) { this.technicalNoiseFactor = technicalNoiseFactor; }

        public double getBbwLevel() { return bbwLevel; }
        public void setBbwLevel(double bbwLevel) { this.bbwLevel = bbwLevel; }

        public boolean isBbwCompressionAlert() { return bbwCompressionAlert; }
        public void setBbwCompressionAlert(boolean bbwCompressionAlert) { this.bbwCompressionAlert = bbwCompressionAlert; }

        public double getMarginDecreaseDays() { return marginDecreaseDays; }
        public void setMarginDecreaseDays(double marginDecreaseDays) { this.marginDecreaseDays = marginDecreaseDays; }

        public double getMarginShortRatio() { return marginShortRatio; }
        public void setMarginShortRatio(double marginShortRatio) { this.marginShortRatio = marginShortRatio; }

        public boolean isMarginShortSqueeze() { return marginShortSqueeze; }
        public void setMarginShortSqueeze(boolean marginShortSqueeze) { this.marginShortSqueeze = marginShortSqueeze; }

        public double getLargeHolderRatio() { return largeHolderRatio; }
        public void setLargeHolderRatio(double largeHolderRatio) { this.largeHolderRatio = largeHolderRatio; }

        public double getNewsSentiment() { return newssentiment; }
        public void setNewsSentiment(double newssentiment) { this.newssentiment = newssentiment; }

        public double getNri() { return nri; }
        public void setNri(double nri) { this.nri = nri; }

        public boolean isSentimentMeltdown() { return sentimentMeltdown; }
        public void setSentimentMeltdown(boolean sentimentMeltdown) { this.sentimentMeltdown = sentimentMeltdown; }

        public boolean isTripleRiseActive() { return tripleRiseActive; }
        public void setTripleRiseActive(boolean tripleRiseActive) { this.tripleRiseActive = tripleRiseActive; }

        public double getContractLiabilityGrowth() { return contractLiabilityGrowth; }
        public void setContractLiabilityGrowth(double contractLiabilityGrowth) { this.contractLiabilityGrowth = contractLiabilityGrowth; }

        public double getInventoryTuroverDays() { return inventoryTuroverDays; }
        public void setInventoryTuroverDays(double inventoryTuroverDays) { this.inventoryTuroverDays = inventoryTuroverDays; }
    }

    public RadarConfigDto() {
        this.quantDetails = new QuantitativeDetails();
    }

    public RadarConfigDto(String symbol,
                          String name,
                          boolean isEtf,
                          int fundamentals,
                          int technicals,
                          int volatility,
                          int riskMargin,
                          int microstructure,
                          int sentiment) {
        this.symbol = symbol;
        this.name = name;
        this.isEtf = isEtf;
        this.fundamentals = fundamentals;
        this.technicals = technicals;
        this.volatility = volatility;
        this.riskMargin = riskMargin;
        this.microstructure = microstructure;
        this.sentiment = sentiment;
        this.quantDetails = new QuantitativeDetails();
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isEtf() {
        return isEtf;
    }

    public void setEtf(boolean etf) {
        isEtf = etf;
    }

    public int getFundamentals() {
        return fundamentals;
    }

    public void setFundamentals(int fundamentals) {
        this.fundamentals = fundamentals;
    }

    public int getTechnicals() {
        return technicals;
    }

    public void setTechnicals(int technicals) {
        this.technicals = technicals;
    }

    public int getVolatility() {
        return volatility;
    }

    public void setVolatility(int volatility) {
        this.volatility = volatility;
    }

    public int getRiskMargin() {
        return riskMargin;
    }

    public void setRiskMargin(int riskMargin) {
        this.riskMargin = riskMargin;
    }

    public int getMicrostructure() {
        return microstructure;
    }

    public void setMicrostructure(int microstructure) {
        this.microstructure = microstructure;
    }

    public int getSentiment() {
        return sentiment;
    }

    public void setSentiment(int sentiment) {
        this.sentiment = sentiment;
    }

    public QuantitativeDetails getQuantDetails() {
        return quantDetails;
    }

    public void setQuantDetails(QuantitativeDetails quantDetails) {
        this.quantDetails = quantDetails;
    }

    public RadarTacticalAnalyzer.TacticalAnalysisResult getTacticalAnalysis() {
        return tacticalAnalysis;
    }

    public void setTacticalAnalysis(RadarTacticalAnalyzer.TacticalAnalysisResult tacticalAnalysis) {
        this.tacticalAnalysis = tacticalAnalysis;
    }

    // ──── ETF 專屬欄位 Getter/Setter ────
    public double getNetAssetValue() {
        return netAssetValue;
    }

    public void setNetAssetValue(double netAssetValue) {
        this.netAssetValue = netAssetValue;
    }

    public double getPremium() {
        return premium;
    }

    public void setPremium(double premium) {
        this.premium = premium;
    }

    public String getNetAssetValueSource() {
        return netAssetValueSource;
    }

    public void setNetAssetValueSource(String netAssetValueSource) {
        this.netAssetValueSource = netAssetValueSource;
    }
}
