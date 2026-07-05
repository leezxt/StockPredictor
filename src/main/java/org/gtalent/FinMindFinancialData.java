package org.gtalent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * FinMind 財務報表單筆資料 DTO
 *
 * 用於接收 FinMind API 返回的財務報表明細，包括：
 * - TaiwanStockFinancialStatements（綜合損益表）：如 Revenue、GrossProfit、NetIncome、GrossProfitMargin 等
 * - TaiwanStockBalanceSheet（資產負債表）：如 Inventories、ContractLiabilities 等
 *
 * API 返回格式（JSON）：
 * {
 *   "date": "2026-03-31",
 *   "stock_id": "2330",
 *   "type": "GrossProfitMargin",
 *   "value": 58.56
 * }
 *
 * @author StockPredictor Team
 * @version 1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FinMindFinancialData {

    /** 財報公告日期或季度結束日期（格式：YYYY-MM-DD，例如 2026-03-31 代表 2026 Q1） */
    private String date;

    /** 股票代號 */
    @JsonProperty("stock_id")
    private String stock_id;

    /**
     * 財務科目類型名稱。
     *
     * <p><b>常見直接百分比欄位</b>（三率，FinMind 直接提供，無需推算）：
     * <ul>
     *   <li>GrossProfitMargin - 毛利率（%）</li>
     *   <li>OperatingProfitMargin / OperatingIncomeMargin - 營業利益率（%）</li>
     *   <li>NetProfitMargin / ProfitMargin / 稅後淨利率 - 淨利率（%）</li>
     * </ul>
     *
     * <p><b>原始金額欄位</b>（損益表，用於推算上述三率或庫存週轉）：
     * <ul>
     *   <li>Revenue / 營業收入淨額 - 營業收入</li>
     *   <li>GrossProfit / 毛利淨額 - 毛利</li>
     *   <li>OperatingIncome / 營業利益 - 營業利益</li>
     *   <li>NetIncome / 稅後淨利 - 稅後淨利</li>
     * </ul>
     *
     * <p><b>資產負債表欄位</b>：
     * <ul>
     *   <li>Inventories / 存貨 - 存貨</li>
     *   <li>ContractLiabilities / ContractLiabilitiesCurrent / 合約負債 - 合約負債</li>
     * </ul>
     */
    private String type;

    /**
     * 科目數值。
     *
     * <p>若 type 為直接百分比欄位（如 GrossProfitMargin），value 已是百分比數字（如 58.56），<b>無需再除以 100</b>。
     *
     * <p>若 type 為原始金額欄位（如 Revenue、Inventories），value 則為金額數字（元），
     * 需由調用端進一步加工（如計算三率、存貨週轉天數等）。
     */
    private double value;

    // ════════════════════════════════════════════════════════════
    //  建構子 & Getters/Setters
    // ════════════════════════════════════════════════════════════

    public FinMindFinancialData() {
    }

    public FinMindFinancialData(String date, String stock_id, String type, double value) {
        this.date = date;
        this.stock_id = stock_id;
        this.type = type;
        this.value = value;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getStock_id() {
        return stock_id;
    }

    public void setStock_id(String stock_id) {
        this.stock_id = stock_id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "FinMindFinancialData{" +
                "date='" + date + '\'' +
                ", stock_id='" + stock_id + '\'' +
                ", type='" + type + '\'' +
                ", value=" + value +
                '}';
    }
}

