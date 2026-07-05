package org.gtalent.dto;

/**
 * 布林通道計算結果
 */
public class BollingerResult {
    private double upperBand;
    private double middleBand;
    private double lowerBand;
    private double bandwidth;

    public BollingerResult() {}

    public BollingerResult(double upperBand, double middleBand, double lowerBand, double bandwidth) {
        this.upperBand = upperBand;
        this.middleBand = middleBand;
        this.lowerBand = lowerBand;
        this.bandwidth = bandwidth;
    }

    public double getUpperBand() { return upperBand; }
    public void setUpperBand(double upperBand) { this.upperBand = upperBand; }

    public double getMiddleBand() { return middleBand; }
    public void setMiddleBand(double middleBand) { this.middleBand = middleBand; }

    public double getLowerBand() { return lowerBand; }
    public void setLowerBand(double lowerBand) { this.lowerBand = lowerBand; }

    public double getBandwidth() { return bandwidth; }
    public void setBandwidth(double bandwidth) { this.bandwidth = bandwidth; }
}
