package org.gtalent;

public class ScannedResult {
    private String symbol;
    private int score;
    private double rsi;
    private String diagnosis;
    private double currentPrice;
    private double realVolumeRatio;
    private boolean volumeChurnRisk;
    private String assetType;
    private String market;
    private String strategyMode;
    private String strategyDescription;

    public ScannedResult(String symbol, int score, double rsi, String diagnosis, double currentPrice,
                         double realVolumeRatio, boolean volumeChurnRisk,
                         String assetType, String market,
                         String strategyMode, String strategyDescription) {
        this.symbol = symbol;
        this.score = score;
        this.rsi = Math.round(rsi * 100.0) / 100.0;
        this.diagnosis = diagnosis;
        this.currentPrice = Math.round(currentPrice * 100.0) / 100.0;
        this.realVolumeRatio = Math.round(Math.max(0.0, Math.min(1.0, realVolumeRatio)) * 1000.0) / 1000.0;
        this.volumeChurnRisk = volumeChurnRisk;
        this.assetType = assetType == null ? "UNKNOWN" : assetType.trim();
        this.market = market == null ? "" : market.trim();
        this.strategyMode = strategyMode == null ? "標準股票策略" : strategyMode.trim();
        this.strategyDescription = strategyDescription == null ? "以趨勢、動能、量價與法人籌碼均衡評估。" : strategyDescription.trim();
    }

    public String getSymbol() {
        return symbol;
    }

    public int getScore() {
        return score;
    }

    public double getRsi() {
        return rsi;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public double getRealVolumeRatio() {
        return realVolumeRatio;
    }

    public boolean isVolumeChurnRisk() {
        return volumeChurnRisk;
    }

    public String getAssetType() {
        return assetType;
    }

    public String getMarket() {
        return market;
    }

    public String getStrategyMode() {
        return strategyMode;
    }

    public String getStrategyDescription() {
        return strategyDescription;
    }
}
