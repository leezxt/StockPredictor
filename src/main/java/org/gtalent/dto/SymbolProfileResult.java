package org.gtalent.dto;

import org.gtalent.StockDataPoint;
import org.gtalent.InstitutionalTrade;
import org.gtalent.FinMindShareholdingData;
import org.gtalent.RevenueData;
import java.util.List;
import java.util.Map;

/**
 * 股票/ETF 綜合詳細資料 Profile
 */
public class SymbolProfileResult {
    // 1. 基礎資訊
    private String symbol;
    private String name;
    private String assetType; // STOCK, ETF, BOND_ETF, ETN
    private boolean isEtf;
    private double currentPrice;
    private double priceChange;
    private double priceChangePct;

    // 2. AI 預測結果 (Phase 4)
    private PredictionResult prediction;

    // 3. 高級技術指標 (Phase 1)
    private BollingerResult bollinger;
    private IchimokuResult ichimoku;
    private double vwap;
    private double rsi;
    private Map<String, Double> kd; // current K, D
    private Map<String, Double> macd; // current DIF, DEA, Hist

    // 4. 籌碼面分析 (Phase 2 & Existing)
    private InstitutionalSyncResult institutionalSync;
    private List<InstitutionalTrade> recentInstitutionalTrades;
    private int continuousBuyDays;
    private double bigHolderScore;

    // 5. 量能與診斷訊號 (Phase 2 & 3)
    private VolumeAnomalyResult volumeAnomaly;
    private DivergenceResult rsiDivergence;
    private DivergenceResult macdDivergence;

    // 6. 基本面 (個股專屬)
    private List<RevenueData> revenueHistory;
    private Map<String, Object> financialHighlights; // 三率等

    // 7. ETF 專屬
    private double netAssetValue;
    private double premiumDiscountPct;
    private boolean navAvailable;
    private String navSource;

    // 8. 系統結論
    private String overallConclusion;

    public SymbolProfileResult() {}

    // Getters and Setters (省略部分以節省空間，實際實作會補全)
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAssetType() { return assetType; }
    public void setAssetType(String assetType) { this.assetType = assetType; }

    public boolean isEtf() { return isEtf; }
    public void setEtf(boolean etf) { isEtf = etf; }

    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

    public PredictionResult getPrediction() { return prediction; }
    public void setPrediction(PredictionResult prediction) { this.prediction = prediction; }

    public BollingerResult getBollinger() { return bollinger; }
    public void setBollinger(BollingerResult bollinger) { this.bollinger = bollinger; }

    public IchimokuResult getIchimoku() { return ichimoku; }
    public void setIchimoku(IchimokuResult ichimoku) { this.ichimoku = ichimoku; }

    public double getVwap() { return vwap; }
    public void setVwap(double vwap) { this.vwap = vwap; }

    public InstitutionalSyncResult getInstitutionalSync() { return institutionalSync; }
    public void setInstitutionalSync(InstitutionalSyncResult institutionalSync) { this.institutionalSync = institutionalSync; }

    public VolumeAnomalyResult getVolumeAnomaly() { return volumeAnomaly; }
    public void setVolumeAnomaly(VolumeAnomalyResult volumeAnomaly) { this.volumeAnomaly = volumeAnomaly; }

    public double getNetAssetValue() { return netAssetValue; }
    public void setNetAssetValue(double netAssetValue) { this.netAssetValue = netAssetValue; }

    public double getPremiumDiscountPct() { return premiumDiscountPct; }
    public void setPremiumDiscountPct(double premiumDiscountPct) { this.premiumDiscountPct = premiumDiscountPct; }

    public boolean isNavAvailable() { return navAvailable; }
    public void setNavAvailable(boolean navAvailable) { this.navAvailable = navAvailable; }

    public String getNavSource() { return navSource; }
    public void setNavSource(String navSource) { this.navSource = navSource; }

    public double getRsi() { return rsi; }
    public void setRsi(double rsi) { this.rsi = rsi; }

    public Map<String, Double> getKd() { return kd; }
    public void setKd(Map<String, Double> kd) { this.kd = kd; }

    public Map<String, Double> getMacd() { return macd; }
    public void setMacd(Map<String, Double> macd) { this.macd = macd; }

    public DivergenceResult getRsiDivergence() { return rsiDivergence; }
    public void setRsiDivergence(DivergenceResult rsiDivergence) { this.rsiDivergence = rsiDivergence; }

    public DivergenceResult getMacdDivergence() { return macdDivergence; }
    public void setMacdDivergence(DivergenceResult macdDivergence) { this.macdDivergence = macdDivergence; }

    public String getOverallConclusion() { return overallConclusion; }
    public void setOverallConclusion(String overallConclusion) { this.overallConclusion = overallConclusion; }
}
