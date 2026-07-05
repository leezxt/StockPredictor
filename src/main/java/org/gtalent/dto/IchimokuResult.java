package org.gtalent.dto;

/**
 * 一目均衡表計算結果
 */
public class IchimokuResult {
    private double tenkanSen;    // 轉折線
    private double kijunSen;     // 基準線
    private double senkouSpanA;  // 先行帶A
    private double senkouSpanB;  // 先行帶B
    private double chikouSpan;   // 遲行帶

    public IchimokuResult() {}

    public IchimokuResult(double tenkanSen, double kijunSen, double senkouSpanA, double senkouSpanB, double chikouSpan) {
        this.tenkanSen = tenkanSen;
        this.kijunSen = kijunSen;
        this.senkouSpanA = senkouSpanA;
        this.senkouSpanB = senkouSpanB;
        this.chikouSpan = chikouSpan;
    }

    public double getTenkanSen() { return tenkanSen; }
    public void setTenkanSen(double tenkanSen) { this.tenkanSen = tenkanSen; }

    public double getKijunSen() { return kijunSen; }
    public void setKijunSen(double kijunSen) { this.kijunSen = kijunSen; }

    public double getSenkouSpanA() { return senkouSpanA; }
    public void setSenkouSpanA(double senkouSpanA) { this.senkouSpanA = senkouSpanA; }

    public double getSenkouSpanB() { return senkouSpanB; }
    public void setSenkouSpanB(double senkouSpanB) { this.senkouSpanB = senkouSpanB; }

    public double getChikouSpan() { return chikouSpan; }
    public void setChikouSpan(double chikouSpan) { this.chikouSpan = chikouSpan; }
}
