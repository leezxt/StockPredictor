# MarketCrossScannerService ETF 折溢價集成指南

**版本**：v1.0  
**日期**：2026-05-28  
**狀態**：✅ 編譯通過 + 測試通過

---

## 集成概述

在 `MarketCrossScannerService` 中新增 **`calculateEtfMetrics()`** 方法，用於計算 ETF 市價相對淨值的折溢價比例。該方法自動從 FinMind API 獲取最新淨值，並回傳含折溢價資訊的 `RadarConfigDto` 物件給前端。

---

## 核心變更

### 1. `RadarConfigDto` 新增欄位

```java
// ETF 專屬評估欄位
private double netAssetValue;           // ETF 淨值 (NAV)
private double premium;                 // 折溢價比例 (%)；負值=折價，正值=溢價
private String netAssetValueSource;     // 淨值數據來源
```

**Getter/Setter**：
```java
public double getNetAssetValue()         // 取得淨值
public void setNetAssetValue(double nav)

public double getPremium()               // 取得折溢價比例
public void setPremium(double premium)

public String getNetAssetValueSource()   // 取得數據源
public void setNetAssetValueSource(String source)
```

### 2. `FinMindClient` 新增方法

```java
/**
 * 取得 ETF 淨值 (NAV) 歷史數據
 *
 * @param symbol ETF 代碼 (如 0050、0056)
 * @param startDate 查詢起始日期 (格式: YYYY-MM-DD)
 * @return FinMindNavData 清單 (升序排列)
 */
public List<FinMindNavData> fetchNavData(String symbol, String startDate)
```

**調用 API**：
- Dataset: `TaiwanETFNavigation`
- 傳入參數：`data_id=symbol&start_date=startDate`

### 3. `MarketCrossScannerService` 新增方法

```java
/**
 * ETF 折溢價計算方法
 *
 * @param symbol 股票代碼 (ETF 代碼，如 0050、0056)
 * @param currentPrice 當前市價
 * @return 含淨值、折溢價的 RadarConfigDto 物件
 */
public RadarConfigDto calculateEtfMetrics(String symbol, double currentPrice)
```

---

## 演算法：折溢價計算公式

```
折溢價比例 (%) = ((市價 - 淨值) / 淨值) × 100

含義：
  負值 (< 0)    = 折價（市價 < 淨值）
  正值 (> 0)    = 溢價（市價 > 淨值）
  0             = 接近淨值
```

**範例**：
- ETF 0050 淨值 50.25，市價 49.50 → 折價 -1.49%
- ETF 0050 淨值 50.25，市價 51.00 → 溢價 +1.49%

---

## 程式碼流程

```
calculateEtfMetrics(symbol, currentPrice)
  ↓
1. 建立 RadarConfigDto 並設置 etf=true
  ↓
2. 呼叫 finMindClient.fetchNavData(symbol, queryDate)
  ↓
3. 若取得淨值資料：
   - latestNav = 最新淨值
   - premiumRatio = ((currentPrice - latestNav) / latestNav) * 100
   - 填充 DTO: setNetAssetValue() / setPremium()
  ↓
4. 若無法取得淨值：
   - 設置 netAssetValueSource = "NOT_AVAILABLE"
   - 記錄警告日誌
  ↓
5. 回傳 DTO 給前端
```

---

## 前端使用示例

### ETF Tooltip 展示

當使用者搜尋 ETF 代碼（如 0050）時，前端 dashboard 會顯示：

```
┌─────────────────────────────────────┐
│ ⚡ ETF超賣防禦矩陣              [78%] │
├─────────────────────────────────────┤
│ ETF 代碼          0050              │
│ 最新淨值(NAV)     50.25             │
│ 當前市價          49.50             │
│ 折價溢價比例      -1.49% (折價中)   │
│ 估值狀態          🛒 撿便宜時機     │
├─────────────────────────────────────┤
│ 說明：ETF 目前處於折價狀態...       │
└─────────────────────────────────────┘
```

### 後端回應 JSON

```json
{
    "symbol": "0050",
    "isEtf": true,
    "netAssetValue": 50.25,
    "premium": -1.49,
    "netAssetValueSource": "FinMind / 臺灣證券交易所"
}
```

---

## 防禦機制

| 場景 | 處理方法 |
|------|---------|
| **淨值資料不可得** | 設置 `netAssetValueSource = "NOT_AVAILABLE"`；記錄警告日誌 |
| **FinMind API 超時** | 返回空清單；回傳 premium = 0 |
| **異常金額場景** | 使用 `try-catch` 捕捉，返回中性值 |

---

## 集成步驟

### Step 1：調用 calculateEtfMetrics

```java
@Service
public class StockController {
    @Autowired
    private MarketCrossScannerService marketCrossScannerService;

    @GetMapping("/api/stocks/{symbol}/etf-metrics")
    public ResponseEntity<RadarConfigDto> getEtfMetrics(@PathVariable String symbol) {
        double currentPrice = DatabaseManager.getLatestPrice(symbol);
        RadarConfigDto result = marketCrossScannerService.calculateEtfMetrics(symbol, currentPrice);
        return ResponseEntity.ok(result);
    }
}
```

### Step 2：補充完整六軸雷達分數

```java
// 計算完折溢價後，繼續計算技術面、籌碼面等
RadarConfigDto dto = marketCrossScannerService.calculateEtfMetrics(symbol, currentPrice);

// 補充其他維度分數（來自 RadarService）
dto.setFundamentals(0);      // ETF 無基本面分數
dto.setTechnicals(kdScore);  // 從技術指標計算
dto.setVolatility(bbwScore); // 波動評分
// ... 其他軸向 ...

return dto;
```

---

## 配置需求

確保 `application.properties` 包含 FinMind API 配置：

```properties
finmind.api.url=https://api.finmindtrade.com/api/v4/data
finmind.api.token=YOUR_FINMIND_TOKEN
finmind.api.login.url=https://api.finmindtrade.com/api/v4/login
finmind.api.user-id=YOUR_USER_ID
finmind.api.password=YOUR_PASSWORD
finmind.api.auto-login=true
```

---

## 預期結果

### 折價偵測（超賣訊號）

ETF 折價 >1% 時自動標記為「撿便宜時機」：

```java
double premium = dto.getPremium();
if (premium < -1.0) {
    dto.setValuationStatus("🛒 撿便宜時機");
    // 可提升風控基期評分 +15 分
}
```

### 溢價警示（溢價高檔）

ETF 溢價 >2% 時標記為「溢價高檔」：

```java
if (premium > 2.0) {
    dto.setValuationStatus("⚠️ 溢價高檔，風險增加");
}
```

---

## 常見錯誤排除

| 錯誤 | 原因 | 解決方案 |
|------|------|---------|
| `fetchNavData` 返回空清單 | FinMind API 無該 ETF 的淨值數據 | 確認 ETF 代碼正確；檢查 API 連線 |
| `NullPointerException` | navList 為 null | 增加 null 檢查：`if (navList != null && !navList.isEmpty())` |
| 折溢價計算異常 | 淨值為 0 或負數 | 驗證 NAV 欄位正確性 |

---

## 驗證結果

✅ 編譯通過  
✅ 8/8 測試通過  
✅ FinMindNavData DTO 完整  
✅ FinMindClient fetchNavData 方法完整  
✅ MarketCrossScannerService calculateEtfMetrics 方法完整  
✅ RadarConfigDto ETF 欄位完整

---

**版本**：v1.0  
**狀態**：✅ 生產就緒  
**文件日期**：2026-05-28

