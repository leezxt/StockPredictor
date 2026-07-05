package org.gtalent;

/**
 * 精細化 RSI 數據模型
 * 包含日線 RSI、週線 RSI、背離判定等進階指標
 */
public class RefinedRsiData {
    private double rsi14;                  // 日線 RSI(14)
    private double rsiMa9;                 // RSI 的 9 日均線
    private double weeklyRsi14;            // 週線 RSI(14)
    private boolean bullishDivergence;     // 底背離（股價新低但 RSI 不同步新低）
    private boolean bearishDivergence;     // 頂背離（股價新高但 RSI 不同步新高）
    private boolean holdingFiftySupport;   // 50 軸鐵壁支撐（多頭主升段特徵）

    public RefinedRsiData() {
    }

    public RefinedRsiData(double rsi14, double rsiMa9, double weeklyRsi14,
                          boolean bullishDivergence, boolean bearishDivergence,
                          boolean holdingFiftySupport) {
        this.rsi14 = rsi14;
        this.rsiMa9 = rsiMa9;
        this.weeklyRsi14 = weeklyRsi14;
        this.bullishDivergence = bullishDivergence;
        this.bearishDivergence = bearishDivergence;
        this.holdingFiftySupport = holdingFiftySupport;
    }

    // Getters
    public double getRsi14() { return rsi14; }
    public double getRsiMa9() { return rsiMa9; }
    public double getWeeklyRsi14() { return weeklyRsi14; }
    public boolean isBullishDivergence() { return bullishDivergence; }
    public boolean isBearishDivergence() { return bearishDivergence; }
    public boolean isHldingFiftySupport() { return holdingFiftySupport; }

    // Setters
    public void setRsi14(double rsi14) { this.rsi14 = rsi14; }
    public void setRsiMa9(double rsiMa9) { this.rsiMa9 = rsiMa9; }
    public void setWeeklyRsi14(double weeklyRsi14) { this.weeklyRsi14 = weeklyRsi14; }
    public void setBullishDivergence(boolean bullishDivergence) { this.bullishDivergence = bullishDivergence; }
    public void setBearishDivergence(boolean bearishDivergence) { this.bearishDivergence = bearishDivergence; }
    public void setHldingFiftySupport(boolean holdingFiftySupport) { this.holdingFiftySupport = holdingFiftySupport; }
}

