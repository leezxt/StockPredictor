package org.gtalent;

/**
 * KD 指標數據模型
 * 
 * 用於儲存單一時間點的 KD 指標值和收盤價
 * 供 KdAdvancedService 進行高級動能分析
 * 
 * @author StockPredictor Team
 * @version 2.1
 */
public class KdData {
    /**
     * 日期（格式：YYYY-MM-DD）
     */
    private String date;
    
    /**
     * 收盤價（用於判斷背離）
     */
    private double closePrice;
    
    /**
     * K 值（快線，範圍 0-100）
     */
    private double kValue;
    
    /**
     * D 值（慢線，範圍 0-100）
     */
    private double dValue;

    /**
     * 無參數構造器
     */
    public KdData() {
    }

    /**
     * 完整構造器
     * 
     * @param date 日期
     * @param closePrice 收盤價
     * @param kValue K 值
     * @param dValue D 值
     */
    public KdData(String date, double closePrice, double kValue, double dValue) {
        this.date = date;
        this.closePrice = closePrice;
        this.kValue = kValue;
        this.dValue = dValue;
    }

    // ════════════════════════════════════════════════════════════════
    // Getters and Setters
    // ════════════════════════════════════════════════════════════════

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public double getClosePrice() {
        return closePrice;
    }

    public void setClosePrice(double closePrice) {
        this.closePrice = closePrice;
    }

    public double getKValue() {
        return kValue;
    }

    public void setKValue(double kValue) {
        this.kValue = kValue;
    }

    public double getDValue() {
        return dValue;
    }

    public void setDValue(double dValue) {
        this.dValue = dValue;
    }

    // ════════════════════════════════════════════════════════════════
    // 工具方法
    // ════════════════════════════════════════════════════════════════

    /**
     * 判斷 K 值是否在高檔（>= 80）
     */
    public boolean isHighZone() {
        return kValue >= 80;
    }

    /**
     * 判斷 K 值是否在低檔（<= 20）
     */
    public boolean isLowZone() {
        return kValue <= 20;
    }

    /**
     * 判斷 K 值是否在中軸區（45-55）
     */
    public boolean isMidZone() {
        return kValue >= 45 && kValue <= 55;
    }

    /**
     * 判斷 K 線是否在 D 線之上（多頭排列）
     */
    public boolean isKAboveD() {
        return kValue > dValue;
    }

    /**
     * 獲取 KD 差值（K - D）
     */
    public double getKdDiff() {
        return kValue - dValue;
    }

    /**
     * 驗證數據是否有效
     */
    public boolean isValid() {
        return date != null && !date.isBlank()
                && closePrice > 0
                && kValue >= 0 && kValue <= 100
                && dValue >= 0 && dValue <= 100;
    }

    @Override
    public String toString() {
        return String.format("KdData{date='%s', close=%.2f, K=%.2f, D=%.2f}",
                date, closePrice, kValue, dValue);
    }
}


