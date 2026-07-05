package org.gtalent;

public class MarketBreadthResult {
    private double breadth;
    private int eligibleCount;
    private int bullishCount;

    public MarketBreadthResult() {}

    public MarketBreadthResult(double breadth, int eligibleCount, int bullishCount) {
        this.breadth = breadth;
        this.eligibleCount = eligibleCount;
        this.bullishCount = bullishCount;
    }

    public double getBreadth() { return breadth; }
    public void setBreadth(double breadth) { this.breadth = breadth; }
    public int getEligibleCount() { return eligibleCount; }
    public void setEligibleCount(int eligibleCount) { this.eligibleCount = eligibleCount; }
    public int getBullishCount() { return bullishCount; }
    public void setBullishCount(int bullishCount) { this.bullishCount = bullishCount; }
}

