package org.gtalent.dto;

/**
 * 成交量異常偵測結果
 */
public class VolumeAnomalyResult {
    private String signalType;      // 訊號類型 (量增價漲, 量縮價不跌, 正常等)
    private double volumeRatio;     // 成交量與均量之比
    private double priceChangePct;  // 漲跌幅
    private String description;     // 詳細描述

    public VolumeAnomalyResult() {}

    public VolumeAnomalyResult(String signalType, double volumeRatio, double priceChangePct, String description) {
        this.signalType = signalType;
        this.volumeRatio = volumeRatio;
        this.priceChangePct = priceChangePct;
        this.description = description;
    }

    // Getters and Setters
    public String getSignalType() { return signalType; }
    public void setSignalType(String signalType) { this.signalType = signalType; }

    public double getVolumeRatio() { return volumeRatio; }
    public void setVolumeRatio(double volumeRatio) { this.volumeRatio = volumeRatio; }

    public double getPriceChangePct() { return priceChangePct; }
    public void setPriceChangePct(double priceChangePct) { this.priceChangePct = priceChangePct; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
