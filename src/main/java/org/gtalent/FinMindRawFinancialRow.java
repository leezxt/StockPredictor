package org.gtalent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * FinMind 財務報表原始列資料
 * 用於 TaiwanStockFinancialStatements（綜合損益表）與 TaiwanStockBalanceSheet（資產負債表）
 * 這兩個 Dataset 的 JSON 欄位格式相同，故共用同一模型。
 *
 * FinMind API 返回格式：
 * {"date": "2024-03-31", "stock_id": "2330", "type": "Revenue", "value": 592640000}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FinMindRawFinancialRow {

    /** 季度結束日期，例如 "2024-03-31" */
    private String date;

    /** 股票代號 */
    @JsonProperty("stock_id")
    private String stockId;

    /**
     * 財務科目名稱。
     * 損益表常見值：Revenue / GrossProfit / OperatingIncome / NetIncome
     * 資產負債表常見值：Inventories / ContractLiabilities / ContractLiabilitiesCurrent
     */
    private String type;

    /** 科目金額（元），已由 FinMind 換算為新台幣元 */
    private double value;

    public FinMindRawFinancialRow() {
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getStockId() {
        return stockId;
    }

    public void setStockId(String stockId) {
        this.stockId = stockId;
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
}

