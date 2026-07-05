package org.gtalent.dto;

/**
 * 法人合力分析結果
 */
public class InstitutionalSyncResult {
    private double combinedScore;   // 綜合得分
    private String foreignTrend;    // 外資趨勢 (買超/賣超/盤整)
    private String trustTrend;      // 投信趨勢 (買超/賣超/盤整)
    private boolean syncBuying;     // 是否同步買超
    private long totalNetBuy;       // 合計買超張數

    public InstitutionalSyncResult() {}

    public InstitutionalSyncResult(double combinedScore, String foreignTrend, String trustTrend, boolean syncBuying, long totalNetBuy) {
        this.combinedScore = combinedScore;
        this.foreignTrend = foreignTrend;
        this.trustTrend = trustTrend;
        this.syncBuying = syncBuying;
        this.totalNetBuy = totalNetBuy;
    }

    // Getters and Setters
    public double getCombinedScore() { return combinedScore; }
    public void setCombinedScore(double combinedScore) { this.combinedScore = combinedScore; }

    public String getForeignTrend() { return foreignTrend; }
    public void setForeignTrend(String foreignTrend) { this.foreignTrend = foreignTrend; }

    public String getTrustTrend() { return trustTrend; }
    public void setTrustTrend(String trustTrend) { this.trustTrend = trustTrend; }

    public boolean isSyncBuying() { return syncBuying; }
    public void setSyncBuying(boolean syncBuying) { this.syncBuying = syncBuying; }

    public long getTotalNetBuy() { return totalNetBuy; }
    public void setTotalNetBuy(long totalNetBuy) { this.totalNetBuy = totalNetBuy; }
}
