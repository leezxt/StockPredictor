# ETF 淨值數據 DTO 集成指南

**版本**：v1.0  
**日期**：2026-05-28  
**狀態**：✅ 編譯通過 + 測試通過

> 現況更新（2026-07-26）：production 方法為 `FinMindClient.fetchNavData(symbol, startDate)`，
> 由 `ScannerService`、`MarketCrossScannerService` 與 `SymbolProfileService` 使用。
> NAV 原始 JSON contract、日期排序、空白參數與 HTTP 錯誤降級由
> `FinMindClientNavDataTest` 驗證；下方 `fetchEtfNavHistory()`／`EtfValuationService`
> 程式片段保留為 2026-05 的設計示例，不代表現行類別名稱。

---

## FinMindNavData DTO 概述

淨值數據模型用於接收 FinMind API 的 ETF 淨資產價值（NAV）回應，支持：
- ETF 歷史淨值查詢
- 折價溢價計算
- ETF 風險評估

---

## 數據結構

```java
public class FinMindNavData {
    private String date;       // 日期 (YYYY-MM-DD)
    private String stockId;    // ETF 代碼 (0050、0056、00919 等)
    private double nav;        // 淨資產價值 (Net Asset Value)
}
```

---

## FinMind API 映射

### 端點範例

```
GET /TaiwanETFNavigation?data_id=0050&start_date=2026-05-01&end_date=2026-05-28
```

### API 回應範例

```json
{
    "status": 200,
    "data": [
        {
            "date": "2026-05-01",
            "stock_id": "0050",
            "NAV": 50.10
        },
        {
            "date": "2026-05-02",
            "stock_id": "0050",
            "NAV": 50.15
        },
        {
            "date": "2026-05-28",
            "stock_id": "0050",
            "NAV": 50.25
        }
    ]
}
```

### DTO 對應

| JSON 欄位 | DTO 欄位 | 類型 |
|----------|---------|------|
| `date` | `date` | String |
| `stock_id` | `stockId` | String |
| `NAV` | `nav` | double |

---

## 核心方法

### Getter

```java
public String getDate()     // 取得日期
public String getStockId()  // 取得 ETF 代碼
public double getNav()      // 取得淨值
```

### Setter

```java
public void setDate(String date)       // 設置日期
public void setStockId(String stockId) // 設置 ETF 代碼
public void setNav(double nav)         // 設置淨值
```

### 輔助方法

#### 計算折價溢價比例

```java
public double calculatePremiumDiscount(double currentPrice)
```

**用途**：計算市價相對淨值的折價或溢價比例

**參數**：`currentPrice`（當前市價）

**返回值**：折價溢價比例
- `-0.05` = 折價 5%（市價低於淨值）
- `+0.03` = 溢價 3%（市價高於淨值）
- `0.0` = 淨值相等

**示例**：

```java
FinMindNavData nav = new FinMindNavData();
nav.setNav(50.25);

double premium = nav.calculatePremiumDiscount(51.00); // 溢價 1.49%
double discount = nav.calculatePremiumDiscount(49.50); // 折價 1.49%
```

#### 格式化顯示

```java
public String format()
```

**返回值**：格式化字符串 `"YYYY-MM-DD | stock_id | NAV: value"`

**示例**：

```java
FinMindNavData nav = new FinMindNavData("2026-05-28", "0050", 50.25);
System.out.println(nav.format()); 
// 輸出：2026-05-28 | 0050 | NAV: 50.25
```

---

## 集成示例

### Step 1：在 FinMindClient 中使用

```java
@Service
public class FinMindClient {
    private static final String NAV_ENDPOINT = "https://api.finmind.co.tw/api/v4/data?dataset=TaiwanETFNavigation";

    /**
     * 取得 ETF 淨值歷史數據
     */
    public List<FinMindNavData> fetchEtfNavHistory(String etfCode, String startDate, String endDate) {
        String url = NAV_ENDPOINT + "&data_id=" + etfCode 
                   + "&start_date=" + startDate 
                   + "&end_date=" + endDate;

        try {
            RestTemplate restTemplate = new RestTemplate();
            FinMindResponse response = restTemplate.getForObject(url, FinMindResponse.class);
            
            // API 回應包含 data 陣列，直接轉換為 List<FinMindNavData>
            return response.getData().stream()
                    .map(raw -> {
                        FinMindNavData nav = new FinMindNavData();
                        nav.setDate((String) raw.get("date"));
                        nav.setStockId((String) raw.get("stock_id"));
                        nav.setNav(((Number) raw.get("NAV")).doubleValue());
                        return nav;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Failed to fetch ETF NAV data: " + e.getMessage());
            return new ArrayList<>();
        }
    }
}
```

### Step 2：計算折價溢價

```java
@Service
public class EtfValuationService {
    @Autowired
    private FinMindClient finMindClient;

    /**
     * 評估 ETF 折價溢價狀態
     */
    public EtfValuationResult evaluateEtfValuation(String etfCode, double currentPrice) {
        // 取得最新淨值
        List<FinMindNavData> navHistory = finMindClient.fetchEtfNavHistory(
            etfCode, 
            LocalDate.now().minusDays(1).toString(),
            LocalDate.now().toString()
        );

        if (navHistory.isEmpty()) {
            return null; // 數據不足
        }

        FinMindNavData latestNav = navHistory.get(navHistory.size() - 1);
        double premiumDiscount = latestNav.calculatePremiumDiscount(currentPrice);

        EtfValuationResult result = new EtfValuationResult();
        result.setEtfCode(etfCode);
        result.setNav(latestNav.getNav());
        result.setCurrentPrice(currentPrice);
        result.setPremiumDiscountPct(premiumDiscount * 100);
        result.setValuationStatus(classifyValuation(premiumDiscount));

        return result;
    }

    private String classifyValuation(double premiumDiscount) {
        if (premiumDiscount < -0.02) {
            return "超便宜";     // 折價超 2%
        } else if (premiumDiscount < 0) {
            return "折價中";     // 0 ~ -2%
        } else if (premiumDiscount < 0.02) {
            return "接近淨值";   // 0 ~ +2%
        } else {
            return "溢價";       // 超過 2%
        }
    }
}
```

### Step 3：整合到 RadarService（ETF 模式）

```java
@Service
public class RadarService {
    @Autowired
    private EtfValuationService etfValuationService;

    public RadarScoreResult calculateRadarScores(String symbol) {
        RadarScoreResult result = new RadarScoreResult();
        
        if (result.etfMode) {
            // ETF 專屬路由：加入折價溢價評估
            double currentPrice = stockDataRepository.getLatestPrice(symbol);
            EtfValuationResult valuation = etfValuationService.evaluateEtfValuation(symbol, currentPrice);
            
            if (valuation != null) {
                result.etfNav = valuation.getNav();
                result.etfPremiumDiscountPct = valuation.getPremiumDiscountPct();
                result.etfValuationStatus = valuation.getValuationStatus();
                
                // 如果 ETF 處於超便宜狀態（折價超 2%），提升風控基期評分
                if (valuation.getPremiumDiscountPct() < -2) {
                    result.context = Math.min(100, result.context + 15); // +15 分
                }
            }
        }
        
        return result;
    }
}
```

---

## 數據流程

```
FinMind API
↓
GET /TaiwanETFNavigation
↓
JSON Response [{date, stock_id, NAV}, ...]
↓
FinMindClient.fetchNavData()
↓
List<FinMindNavData>
↓
ScannerService / MarketCrossScannerService / SymbolProfileService
↓
計算折價溢價 → 分類估值狀態
↓
ETF 評分、跨市場分析與 symbol profile 回傳
```

---

## 前端展示示例

### Tooltip 展示（ETF 模式）

```
┌─────────────────────────────────────┐
│ ⚡ ETF超賣防禦矩陣              [78%] │
├─────────────────────────────────────┤
│ ETF 代碼          0050              │
│ 最新淨值(NAV)     50.25             │
│ 當前市價          49.50             │
│ 折價溢價比例      -1.49% (折價中)   │  ← 綠色
│ 估值狀態          🛒 撿便宜時機     │
├─────────────────────────────────────┤
│ 說明：ETF 目前處於折價狀態...       │
│ 市價低於淨值 1.49%，適合逢低加碼。  │
└─────────────────────────────────────┘
```

---

## Jackson 註解

DTO 使用 `@JsonProperty` 自動對應 JSON 欄位：

```java
@JsonProperty("date")      // JSON "date" → Java date
@JsonProperty("stock_id")  // JSON "stock_id" → Java stockId
@JsonProperty("NAV")       // JSON "NAV" → Java nav
```

無需手動序列化 / 反序列化。

---

## 構建函數

### 無參構造

```java
FinMindNavData nav = new FinMindNavData();
```

### 帶參構造

```java
FinMindNavData nav = new FinMindNavData("2026-05-28", "0050", 50.25);
```

---

## 使用場景

| 場景 | 用途 |
|------|------|
| ETF 折價偵測 | 識別超賣時機，觸發買進信號 |
| ETF 風險評估 | 納入 RadarService 風控基期加權 |
| ETF 歷史追蹤 | 長期監控 ETF 折價溢價趨勢 |
| 投資決策支持 | 配合市場廣度環境選擇 ETF or 個股 |

---

## 驗證結果

✅ 編譯通過  
✅ `FinMindClientNavDataTest` 3 項 contract test 通過
✅ Jackson 序列化無誤  
✅ 輔助方法邏輯完整  
✅ 文檔和示例完善

---

**版本**：v1.0  
**狀態**：✅ 生產就緒  
**文件日期**：2026-05-28

