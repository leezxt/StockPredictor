package org.gtalent;

/**
 * 綜合交叉測試結果。
 * 由 {@link MarketCrossScannerService} 產出，描述一檔標的的策略診斷結論。
 */
public class StockDiagnosticResult {

    private String symbol;
    private boolean isEtf;
    private boolean skip;
    private String reason;
    private boolean perfectMatch;
    private int finalScore;
    private String strategyTag;

    /** ETF 折溢價（百分比，正值溢價、負值折價）；個股或無淨值資料時為 0 */
    private double premium;

    /** ETF 淨值資料是否可用 */
    private boolean navAvailable;

    /** ETF 淨值資料來源（例如 FinMind / NOT_AVAILABLE） */
    private String navSource;

    /** 個股 52 週相對位置（0.0 為年低、1.0 為年高）；ETF 路徑為 0 */
    private double currentPosition;

    public StockDiagnosticResult() {
        this.navSource = "NOT_AVAILABLE";
    }

    public StockDiagnosticResult(String symbol) {
        this.symbol = symbol;
        this.navSource = "NOT_AVAILABLE";
    }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public boolean isEtf() { return isEtf; }
    public void setIsEtf(boolean etf) { this.isEtf = etf; }

    public boolean isSkip() { return skip; }
    public void setSkip(boolean skip) { this.skip = skip; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public boolean isPerfectMatch() { return perfectMatch; }
    public void setPerfectMatch(boolean perfectMatch) { this.perfectMatch = perfectMatch; }

    public int getFinalScore() { return finalScore; }
    public void setFinalScore(int finalScore) { this.finalScore = finalScore; }

    public String getStrategyTag() { return strategyTag; }
    public void setStrategyTag(String strategyTag) { this.strategyTag = strategyTag; }

    public double getPremium() { return premium; }
    public void setPremium(double premium) { this.premium = premium; }

    public boolean isNavAvailable() { return navAvailable; }
    public void setNavAvailable(boolean navAvailable) { this.navAvailable = navAvailable; }

    public String getNavSource() { return navSource; }
    public void setNavSource(String navSource) {
        this.navSource = navSource == null || navSource.isBlank() ? "NOT_AVAILABLE" : navSource;
    }

    public double getCurrentPosition() { return currentPosition; }
    public void setCurrentPosition(double currentPosition) { this.currentPosition = currentPosition; }
}
