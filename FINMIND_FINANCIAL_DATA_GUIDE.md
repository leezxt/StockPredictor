# FinMind 三率直接欄位整合指南

## 📋 DTO 結構說明

已建立 `FinMindFinancialData` DTO 類別，接收 FinMind API 財務報表資料：

```java
public class FinMindFinancialData {
    private String date;         // 季度結束日期，格式 YYYY-MM-DD
    private String stock_id;     // 股票代號
    private String type;         // 財務科目類型
    private double value;        // 科目數值
}
```

## 🎯 三率「直接」百分比欄位

FinMind `TaiwanStockFinancialStatements` Dataset 直接提供以下三率欄位，**value 已是百分比數字**（如 58.56），無需推算：

| 欄位名稱 | 中文名稱 | 常見別名 | Value 範圍 | 說明 |
|---------|---------|---------|----------|------|
| `GrossProfitMargin` | 毛利率 | 毛利率 | 0-100 | 毛利÷收入，直接百分比 |
| `OperatingProfitMargin` | 營業利益率 | OperatingIncomeMargin、營業利益率 | -100~100 | 營業利益÷收入，直接百分比 |
| `NetProfitMargin` | 稅後淨利率 | ProfitMargin、稅後淨利率、淨利率 | -100~100 | 稅後淨利÷收入，直接百分比 |

### API 返回範例

```json
{
  "date": "2026-03-31",
  "stock_id": "2330",
  "type": "GrossProfitMargin",
  "value": 58.56
}
```

**注意**：value 58.56 **直接代表 58.56%**，不需要再除以 100。

## 🔄 API 請求流程

### 1. 構建 FinMind API URL

```
https://api.finmindtrade.com/api/v4/data
  ?dataset=TaiwanStockFinancialStatements
  &data_id=2330
  &start_date=2025-01-01
  &token=YOUR_TOKEN
```

### 2. FinMindClient 調用

客戶端已實現的方法：

```java
// 獲取綜合損益表（包含三率直接欄位）
List<FinMindRawFinancialRow> fetchFinancialStatements(String symbol, String startDate)

// 獲取資產負債表（存貨、合約負債等）
List<FinMindRawFinancialRow> fetchBalanceSheet(String symbol, String startDate)
```

### 3. API 回傳資料結構

每筆資料 Json：
```json
{
  "date": "2026-03-31",
  "stock_id": "2330",
  "type": "GrossProfitMargin",
  "value": 58.56
}
```

多筆資料以 Array 返回（FinMind Response 中的 `data` 陣列）。

## 💾 DB 快取層設計

### 建立快取表

```sql
CREATE TABLE IF NOT EXISTS FINANCIAL_QUARTER_DATA (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    symbol                   VARCHAR(20)     NOT NULL,
    quarter_date             DATE            NOT NULL,
    gross_profit_margin      DOUBLE          DEFAULT 0,
    operating_profit_margin  DOUBLE          DEFAULT 0,
    net_profit_margin        DOUBLE          DEFAULT 0,
    inventory_turnover_days  INT             DEFAULT 0,
    contract_liabilities     BIGINT          DEFAULT 0,
    created_at               TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS IDX_FINANCIAL_QUARTER_SYMBOL_DATE
    ON FINANCIAL_QUARTER_DATA(symbol, quarter_date);
```

### 快取讀寫流程

1. **先查快取**：`financialDataRepository.getFinancialQuarterHistory(symbol, quarters)`
2. **快取不足時調用 API**：`finMindClient.fetchFinancialStatements()` 與 `fetchBalanceSheet()`
3. **三率直接欄位識別**：篩選 type 為 GrossProfitMargin / OperatingProfitMargin / NetProfitMargin 的記錄
4. **寫入快取**：`financialDataRepository.saveFinancialQuarterData(symbol, data)`

## 📊 三率直接欄位識別邏輯

在 `AdvancedFundamentalService.java`：

```java
// 定義直接欄位的可能 type 名稱變體
private static final String[] TYPE_GROSS_PROFIT_MARGIN = {
    "GrossProfitMargin", "毛利率"
};
private static final String[] TYPE_OPERATING_PROFIT_MARGIN = {
    "OperatingProfitMargin", "OperatingIncomeMargin", "營業利益率"
};
private static final String[] TYPE_NET_PROFIT_MARGIN = {
    "NetProfitMargin", "ProfitMargin", "稅後淨利率", "淨利率"
};

// 在解析時，優先使用直接欄位
double grossProfitMargin = resolveValue(fs, TYPE_GROSS_PROFIT_MARGIN);
double operatingProfitMargin = resolveValue(fs, TYPE_OPERATING_PROFIT_MARGIN);
double netProfitMargin = resolveValue(fs, TYPE_NET_PROFIT_MARGIN);

// 若直接欄位值為 0，回退至計算邏輯
if (grossProfitMargin == 0.0 && revenue > 0) {
    grossProfitMargin = (grossProfit / revenue) * 100.0;
}
```

## 🔧 工作流程完整示例

```
1. RadarService.getRiskScore(symbol)
   ↓
2. AdvancedFundamentalService.getQuarterHistory(symbol, 4)
   ├─ 查 DB 快取 (FINANCIAL_QUARTER_DATA)
   ├── 若快取不足：
   │  ├─ API 呼叫 FinMind 損益表（含三率直接欄位）
   │  ├─ API 呼叫 FinMind 資產負債表（存貨、合約負債）
   │  ├─ 篩選三率直接 type：GrossProfitMargin 等
   │  ├─ 計算存貨週轉天數（若無直接欄位）
   │  └─ 寫入 DB 快取
   └─ 回傳 List<FinancialQuarterData>
   ↓
3. AdvancedFundamentalService.calculateRefinedFundamentalScore()
   ├─ 使用三率直接欄位判斷「三率三升」
   ├─ 使用存貨週轉天數判斷「庫存健康度」
   └─ 使用合約負債判斷「未來訂單能見度」
   ↓
4. AdvancedFundamentalService.buildFundamentalDetail()
   ├─ 填充 FundamentalDetail 旗標
   └─ 回傳至前端
   ↓
5. index.html generateDiagnosis()
   └─ 展示「基本面 AI 洞察」報告卡片
```

## ⚠️ 常見問題 & 解決方案

### Q1: 為何 value 已是百分比，還要做回退推算？

**A**：FinMind 不同版本/時段可能缺少直接欄位，此時需從原始金額反推。回退邏輯保證系統可靠性。

### Q2: 直接欄位和計算結果不一致怎麼辦？

**A**：優先信任直接欄位。如發現差異，應回報 FinMind 數據品質問題。

### Q3: 季度日期如何判斷？

**A**：API 回傳的 `date` 欄位已是季度結束日期（如 2026-03-31 代表 Q1）。直接作為 FINANCIAL_QUARTER_DATA 的 `quarter_date` 存儲。

### Q4: 如何處理多個 type 名稱變體？

**A**：使用 `resolveValue()` 工具方法，按優先順序嘗試多個 type 候選名稱，第一個匹配即返回。

## 🧪 測試驗證

運行編譯測試：
```bash
mvn -q -DskipTests compile
```

檢查快取表建立：
```sql
SELECT * FROM FINANCIAL_QUARTER_DATA;
```

調用 API 並驗證三率欄位：
```bash
curl "https://api.finmindtrade.com/api/v4/data?dataset=TaiwanStockFinancialStatements&data_id=2330&start_date=2026-01-01&token=YOUR_TOKEN"
```

## 📝 相關檔案

- **DTO**: `FinMindFinancialData.java`
- **DTO 映射**: `FinMindRawFinancialRow.java`（已存在，相同結構）
- **客戶端**: `FinMindClient.java`
- **業務邏輯**: `AdvancedFundamentalService.java`
- **快取層**: `FinancialDataRepository.java`
- **資料模型**: `FinancialQuarterData.java`
- **結果模型**: `RadarScoreResult.java`
- **前端整合**: `index.html`（AI 診斷報告區塊）

## ✅ 實裝檢查清單

- [x] 建立 `FinMindFinancialData` DTO
- [x] 驗證 FinMind API 返回格式相符
- [x] 三率直接欄位識別邏輯已在 `AdvancedFundamentalService` 中實現
- [x] DB 快取表已建立（FINANCIAL_QUARTER_DATA）
- [x] 快取讀寫方法已實現（`FinancialDataRepository`）
- [x] 編譯通過
- [ ] 實機測試 (需 FinMind token)
- [ ] 前端展示驗證

---

**最後更新**: 2026-05-19
**系統版本**: 基本面精細化評分引擎 v2.0

