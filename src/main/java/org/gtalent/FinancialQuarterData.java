package org.gtalent;

/**
 * 季度財務數據模型
 * 來源：FinMind TaiwanStockFinancialStatements（綜合損益表）與 TaiwanStockBalanceSheet（資產負債表）
 */
public class FinancialQuarterData {

    /** 季度日期，例如 "2024-03-31"（Q1 結束日） */
    private String quarterDate;

    /** 毛利率 (%)：(毛利 / 營業收入) × 100 */
    private double grossProfitMargin;

    /** 營業利益率 (%)：(營業利益 / 營業收入) × 100 */
    private double operatingProfitMargin;

    /** 淨利率 (%)：(稅後淨利 / 營業收入) × 100 */
    private double netProfitMargin;

    /** 存貨週轉天數（天）：(存貨 / 營業收入) × 91.25（季度天數） */
    private int inventoryTurnoverDays;

    /** 合約負債（元）：預收款項 / 合約負債，代表未來入帳的訂單保證 */
    private long contractLiabilities;

    public FinancialQuarterData() {
    }

    public FinancialQuarterData(String quarterDate,
                                double grossProfitMargin,
                                double operatingProfitMargin,
                                double netProfitMargin,
                                int inventoryTurnoverDays,
                                long contractLiabilities) {
        this.quarterDate = quarterDate;
        this.grossProfitMargin = grossProfitMargin;
        this.operatingProfitMargin = operatingProfitMargin;
        this.netProfitMargin = netProfitMargin;
        this.inventoryTurnoverDays = inventoryTurnoverDays;
        this.contractLiabilities = contractLiabilities;
    }

    public String getQuarterDate() {
        return quarterDate;
    }

    public void setQuarterDate(String quarterDate) {
        this.quarterDate = quarterDate;
    }

    public double getGrossProfitMargin() {
        return grossProfitMargin;
    }

    public void setGrossProfitMargin(double grossProfitMargin) {
        this.grossProfitMargin = grossProfitMargin;
    }

    public double getOperatingProfitMargin() {
        return operatingProfitMargin;
    }

    public void setOperatingProfitMargin(double operatingProfitMargin) {
        this.operatingProfitMargin = operatingProfitMargin;
    }

    public double getNetProfitMargin() {
        return netProfitMargin;
    }

    public void setNetProfitMargin(double netProfitMargin) {
        this.netProfitMargin = netProfitMargin;
    }

    public int getInventoryTurnoverDays() {
        return inventoryTurnoverDays;
    }

    public void setInventoryTurnoverDays(int inventoryTurnoverDays) {
        this.inventoryTurnoverDays = inventoryTurnoverDays;
    }

    public long getContractLiabilities() {
        return contractLiabilities;
    }

    public void setContractLiabilities(long contractLiabilities) {
        this.contractLiabilities = contractLiabilities;
    }

    @Override
    public String toString() {
        return String.format("FinancialQuarterData{date=%s, grossMargin=%.2f%%, opMargin=%.2f%%, netMargin=%.2f%%, invDays=%d, contractLiab=%d}",
                quarterDate, grossProfitMargin, operatingProfitMargin, netProfitMargin, inventoryTurnoverDays, contractLiabilities);
    }
}

