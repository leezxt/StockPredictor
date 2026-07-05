package org.gtalent;

public class MarketBreadthSnapshot {
    private String tradeDate;
    private double breadth;
    private int eligibleCount;
    private int bullishCount;

    public MarketBreadthSnapshot() {
    }

    public MarketBreadthSnapshot(String tradeDate, double breadth, int eligibleCount, int bullishCount) {
        this.tradeDate = tradeDate;
        this.breadth = breadth;
        this.eligibleCount = eligibleCount;
        this.bullishCount = bullishCount;
    }

    public String getTradeDate() {
        return tradeDate;
    }

    public void setTradeDate(String tradeDate) {
        this.tradeDate = tradeDate;
    }

    public double getBreadth() {
        return breadth;
    }

    public void setBreadth(double breadth) {
        this.breadth = breadth;
    }

    public int getEligibleCount() {
        return eligibleCount;
    }

    public void setEligibleCount(int eligibleCount) {
        this.eligibleCount = eligibleCount;
    }

    public int getBullishCount() {
        return bullishCount;
    }

    public void setBullishCount(int bullishCount) {
        this.bullishCount = bullishCount;
    }
}

