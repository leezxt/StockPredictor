# FinMind 三率直接欄位實機測試指南

## 📋 檢查清單

實裝完成後，在運行應用以前，請按以下步驟驗證三率直接欄位的識別與使用：

## 1️⃣ 編譯驗證

```bash
cd C:\Users\lee\Documents\intel j ide\StockPredictor\StockPredictor
mvn -q -DskipTests compile
```

**預期結果**：無編譯錯誤

## 2️⃣ 資料庫快取表初始化

啟動應用後，驗證快取表是否建立：

```sql
-- 在 H2 Console 或任何 SQL 工具中執行
SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME='FINANCIAL_QUARTER_DATA';
```

**預期結果**：返回一筆記錄，表結構如下：

```
TABLE_SCHEMA    TABLE_NAME                    TABLE_TYPE
PUBLIC          FINANCIAL_QUARTER_DATA        TABLE
```

查看欄位：
```sql
SELECT * FROM FINANCIAL_QUARTER_DATA LIMIT 0;
```

**預期結構**：
```
id | symbol | quarter_date | gross_profit_margin | operating_profit_margin | net_profit_margin | inventory_turnover_days | contract_liabilities | created_at
```

## 3️⃣ API 連接測試

### 3.1 驗證 FinMind Token 有效性

```bash
curl -X GET \
  "https://api.finmindtrade.com/api/v4/data?dataset=TaiwanStockFinancialStatements&data_id=2330&start_date=2026-01-01&token=YOUR_TOKEN" \
  -H "Accept: application/json"
```

**預期回傳**（成功示例）：

```json
{
  "status": 200,
  "msg": "OK",
  "data": [
    {
      "date": "2026-03-31",
      "stock_id": "2330",
      "type": "GrossProfitMargin",
      "value": 58.56
    },
    {
      "date": "2026-03-31",
      "stock_id": "2330",
      "type": "OperatingProfitMargin",
      "value": 35.42
    },
    {
      "date": "2026-03-31",
      "stock_id": "2330",
      "type": "NetProfitMargin",
      "value": 28.75
    },
    ...其他欄位...
  ]
}
```

### 3.2 驗證應用設定中包含 Token

檢查 `application.properties` 或 `application.yml`：

```properties
# application.properties
finmind.api.token=YOUR_VALID_TOKEN
finmind.api.url=https://api.finmindtrade.com/api/v4/data
```

或

```yaml
# application.yml
finmind:
  api:
    token: YOUR_VALID_TOKEN
    url: https://api.finmindtrade.com/api/v4/data
```

## 4️⃣ 前端 UI 測試

### 4.1 訪問主頁

```
http://localhost:8080
```

### 4.2 輸入股票代號進行搜尋

輸入股票代號（例如 `2330` 或 `0050`）

### 4.3 驗證基本面 AI 洞察卡片

在診斷報告區塊中，應看到：

```
📊 基本面 AI 洞察（精細化分數：XX/20）
[根據 FundamentalDetail 旗標生成的專業洞察文字]
```

**預期狀態**：
- ✅ 若為 ETF 模式：顯示「ETF 模式，已排除財報評分。」
- ✅ 若為股票但季報資料有限：顯示「季報資料有限，評分以月營收為主。」
- ✅ 若為股票且季報完整：顯示對應的基本面洞察（7 種情境之一）

## 5️⃣ 日誌檢查

### 5.1 啟用詳細日誌

修改 `application.properties`：

```properties
logging.level.org.gtalent.AdvancedFundamentalService=FINE
logging.level.org.gtalent.RadarService=FINE
```

或在啟動時指定：

```bash
java -jar ... -Dlogging.level.org.gtalent.AdvancedFundamentalService=FINE
```

### 5.2 預期日誌輸出

#### 首次請求（快取未命中）：

```
[AdvancedFundamental] 快取未命中或不足 (2330)，準備呼叫 FinMind API
[FinMind] 發送資料請求: dataset=TaiwanStockFinancialStatements, symbol=2330, startDate=2026-01-01
✅ 成功獲取 FinMind 財務數據: 狀態碼=200, 資料筆數=36
✅ 三率三升 +6
✅ 存貨週轉天數下降 +3
[AdvancedFundamental] 儲存季報至快取: symbol=2330, date=2026-03-31
```

#### 第二次請求（快取命中）：

```
[AdvancedFundamental] DB 快取命中(2330)，共 4 季
[AdvancedFundamental] 季報已從快取讀取，無需打 API
```

### 5.3 檢查三率欄位識別

在日誌中搜尋「resolveValue」或「GrossProfitMargin」，應看到：

```
[AdvancedFundamental] 識別到直接三率欄位: GrossProfitMargin=58.56%
[AdvancedFundamental] 識別到直接三率欄位: OperatingProfitMargin=35.42%
[AdvancedFundamental] 識別到直接三率欄位: NetProfitMargin=28.75%
```

## 6️⃣ 資料庫驗證

### 6.1 查詢快取表內容

```sql
SELECT * FROM FINANCIAL_QUARTER_DATA WHERE symbol='2330' ORDER BY quarter_date DESC;
```

**預期結果**：應有多筆記錄（4~8 季），欄位 `gross_profit_margin`、`operating_profit_margin`、`net_profit_margin` 應填滿百分比值（如 58.56）。

### 6.2 驗證 UNIQUE 索引

```sql
-- 重複插入相同 symbol 和 quarter_date，應自動更新（MERGE）而非拋出錯誤
INSERT INTO FINANCIAL_QUARTER_DATA 
  (symbol, quarter_date, gross_profit_margin, operating_profit_margin, 
   net_profit_margin, inventory_turnover_days, contract_liabilities, created_at) 
VALUES 
  ('2330', '2026-03-31', 60.0, 37.0, 30.0, 50, 1000000000, CURRENT_TIMESTAMP);

SELECT * FROM FINANCIAL_QUARTER_DATA 
WHERE symbol='2330' AND quarter_date='2026-03-31';
```

**預期結果**：毛利率應更新為 60.0，無重複記錄錯誤。

## 7️⃣ 性能測試

### 7.1 快取效能對比

**首次查詢**（無快取）：
```
耗時: ~1.5-3 秒（需等待 FinMind API 響應）
```

**第二次查詢**（有快取）：
```
耗時: ~0.05-0.1 秒（直接從 DB 讀取）
```

驗證方式：在前端F12 開發者工具中查看 Network 選項卡的 `/api/radar/risk-scores` 請求耗時。

### 7.2 批量查詢測試

```bash
# 依序查詢 10 檔股票，第二輪應明顯加速
for symbol in 2330 2454 3008 6415 0050 0051 00878 00713 1101 2883; do
  echo "查詢 $symbol..."
  curl -s "http://localhost:8080/api/radar/risk-scores/$symbol" | jq '.fundamental, .fundamentalDetail' | head -20
  echo "---"
done
```

## ⚠️ 常見問題排除

### 問題1：API 403 Forbidden

**症狀**：日誌出現 `TOKEN 可能失效`

**解決**：
1. 驗證 token 值是否正確
2. 檢查 token 是否已過期
3. 嘗試使用 finmind.api.auto-login 機制重新登入

```properties
finmind.api.auto-login=true
finmind.api.user-id=your_user_id
finmind.api.password=your_password
```

### 問題2：快取表不存在

**症狀**：異常 `TABLE FINANCIAL_QUARTER_DATA NOT FOUND`

**解決**：
1. 確認 `schema.sql` 已被執行
2. 檢查 H2 資料庫初始化邏輯
3. 手動執行 schema.sql：

```bash
# 在 H2 Console 中執行
CLASSPATH=/path/to/h2-*.jar java -cp . org.h2.tools.Shell -user sa -sql "@schema.sql"
```

### 問題3：基本面 AI 洞察為空白

**症狀**：前端顯示「季報資料有限，評分以月營收為主。」

**解決**：
1. 驗證 FinMind API 是否返回財務數據
2. 檢查 API token 權限是否包含 `TaiwanStockFinancialStatements` Dataset
3. 檢查日誌中是否有「⚠️ 財報資料為空」的訊息

### 問題4：三率欄位識別失敗（值為 0.0）

**症狀**：毛利率、營業利益率、淨利率都顯示 0

**解決**：
1. 檢查 API 回傳的 JSON 中 `type` 欄位名稱是否與預期匹配
2. 增加新的 type 候選名稱到以下陣列：
   - `TYPE_GROSS_PROFIT_MARGIN`
   - `TYPE_OPERATING_PROFIT_MARGIN`
   - `TYPE_NET_PROFIT_MARGIN`

修改位置：`AdvancedFundamentalService.java` 第 40-52 行

## 📊 測試用股票清單

推薦用以下股票進行完整測試（各類型的代表）：

| 代號 | 公司名稱 | 類型 | 特點 |
|-----|---------|------|------|
| 2330 | 台灣積電 | 股票 | 科技龍頭，財報完整 |
| 1101 | 台泥 | 股票 | 傳統產業，化工有合約負債 |
| 0050 | 台灣50 | ETF | 應排除財報評分 |
| 00878 | 00878 | ETF | 應排除財報評分 |

## 📝 驗證完成後的交付檢查

- [ ] 編譯通過，無錯誤警告
- [ ] 快取表成功建立
- [ ] API 連接正常，Token 有效
- [ ] 首次查詢速度 1.5-3 秒
- [ ] 二次查詢速度 < 0.1 秒
- [ ] 前端顯示基本面 AI 洞察卡片
- [ ] 日誌中相應輸出三率欄位識別訊息
- [ ] 資料庫內有快取記錄

---

**最後更新**: 2026-05-19
**實機測試版本**: FinMind 三率直接欄位 v1.0

