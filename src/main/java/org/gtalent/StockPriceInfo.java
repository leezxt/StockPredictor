package org.gtalent;

/**
 * 標的價格摘要，提供 {@link MarketCrossScannerService} 做基期判定與 ETF 折溢價判定。
 *
 * <p>{@code discountPremiumRatio = (市價 - 淨值) / 淨值}，僅 ETF 適用；
 * 在缺乏淨值資料來源時可保留為 0，service 會自動忽略折溢價判定路徑。</p>
 */
public class StockPriceInfo {

    private double currentPrice;
    private double yearHigh;
    private double yearLow;
    private double discountPremiumRatio;
    private boolean navAvailable;
    private String navSource;

    public StockPriceInfo() {
        this.navSource = "NOT_AVAILABLE";
    }

    public StockPriceInfo(double currentPrice, double yearHigh, double yearLow) {
        this(currentPrice, yearHigh, yearLow, 0.0, false, "NOT_AVAILABLE");
    }

    public StockPriceInfo(double currentPrice, double yearHigh, double yearLow, double discountPremiumRatio) {
        this(currentPrice, yearHigh, yearLow, discountPremiumRatio, false, "NOT_AVAILABLE");
    }

    public StockPriceInfo(double currentPrice,
                          double yearHigh,
                          double yearLow,
                          double discountPremiumRatio,
                          boolean navAvailable,
                          String navSource) {
        this.currentPrice = currentPrice;
        this.yearHigh = yearHigh;
        this.yearLow = yearLow;
        this.discountPremiumRatio = discountPremiumRatio;
        this.navAvailable = navAvailable;
        this.navSource = navSource == null || navSource.isBlank() ? "NOT_AVAILABLE" : navSource;
    }

    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

    public double getYearHigh() { return yearHigh; }
    public void setYearHigh(double yearHigh) { this.yearHigh = yearHigh; }

    public double getYearLow() { return yearLow; }
    public void setYearLow(double yearLow) { this.yearLow = yearLow; }

    public double getDiscountPremiumRatio() { return discountPremiumRatio; }
    public void setDiscountPremiumRatio(double discountPremiumRatio) { this.discountPremiumRatio = discountPremiumRatio; }

    public boolean isNavAvailable() { return navAvailable; }
    public void setNavAvailable(boolean navAvailable) { this.navAvailable = navAvailable; }

    public String getNavSource() { return navSource; }
    public void setNavSource(String navSource) {
        this.navSource = navSource == null || navSource.isBlank() ? "NOT_AVAILABLE" : navSource;
    }
}
