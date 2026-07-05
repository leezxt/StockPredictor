package org.gtalent.dto;

/**
 * 技術指標背離偵測結果
 */
public class DivergenceResult {
    private String indicatorType;   // 指標類型 (RSI, MACD)
    private String divergenceType;  // 背離類型 (底背離, 頂背離, 無)
    private double strength;        // 訊號強度 (0.0 - 1.0)
    private boolean confirmed;      // 是否確認 (根據指標轉向判定)
    private String description;     // 詳細描述

    public DivergenceResult() {}

    public DivergenceResult(String indicatorType, String divergenceType, double strength, boolean confirmed, String description) {
        this.indicatorType = indicatorType;
        this.divergenceType = divergenceType;
        this.strength = strength;
        this.confirmed = confirmed;
        this.description = description;
    }

    // Getters and Setters
    public String getIndicatorType() { return indicatorType; }
    public void setIndicatorType(String indicatorType) { this.indicatorType = indicatorType; }

    public String getDivergenceType() { return divergenceType; }
    public void setDivergenceType(String divergenceType) { this.divergenceType = divergenceType; }

    public double getStrength() { return strength; }
    public void setStrength(double strength) { this.strength = strength; }

    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
