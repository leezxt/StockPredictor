# FinMind 信用交易數據（融資融券）完整指南

**完成日期**: 2026-05-19  
**功能**: 融資融券數據接收與分析  
**狀態**: ✅ 編譯通過

---

## 📋 DTO 結構設計

### FinMindMarginData 類別

```java
public class FinMindMarginData {
    // 基本欄位
    private String date;
    private String stock_id;
    
    // 融資相關
    private long MarginPurchaseBuy;      // 今日融資買進
    private long MarginPurchaseSell;     // 今日融資賣出
    private long MarginPurchaseLimit;    // 融資當日餘額 ★
    
    // 融券相關
    private long ShortSaleBuy;           // 今日融券買進
    private long ShortSaleSell;          // 今日融券賣出
    private long ShortSaleLimit;         // 融券當日餘額 ★
}
```

### FinMind API 數據源

**Dataset**: `TaiwanStockMarginPurchaseShortSale`

**API URL 範例**:
```
https://api.finmindtrade.com/api/v4/data
  ?dataset=TaiwanStockMarginPurchaseShortSale
  &data_id=2330
  &start_date=2026-01-01
  &token=YOUR_TOKEN
```

**回傳 JSON 格式**:
```json
{
  "status": 200,
  "msg": "OK",
  "data": [
    {
      "date": "2026-05-19",
      "stock_id": "2330",
      "MarginPurchaseBuy": 5000,
      "MarginPurchaseSell": 3000,
      "MarginPurchaseLimit": 150000,
      "ShortSaleBuy": 2000,
      "ShortSaleSell": 1500,
      "ShortSaleLimit": 80000
    },
    ...
  ]
}
```

---

## 🔍 核心欄位詳解

### 融資相關欄位

| 欄位 | 類型 | 含義 | 單位 | 說明 |
|-----|------|------|------|------|
| **MarginPurchaseBuy** | long | 今日融資買進 | 張 | 用融資去買股票 |
| **MarginPurchaseSell** | long | 今日融資賣出 | 張 | 用融資賣出的股票 |
| **MarginPurchaseLimit** ⭐ | long | 融資當日餘額 | 張 | **報告日期之融資餘額** |

### 融券相關欄位

| 欄位 | 類型 | 含義 | 單位 | 說明 |
|-----|------|------|------|------|
| **ShortSaleBuy** | long | 今日融券買進 | 張 | 融券借票去買回 |
| **ShortSaleSell** | long | 今日融券賣出 | 張 | 融券借票去賣出 |
| **ShortSaleLimit** ⭐ | long | 融券當日餘額 | 張 | **報告日期之融券餘額** |

---

## 📊 衍生計算方法

### 1. 資券比 (Margin/Short Sale Ratio)

```java
double ratio = marginData.getMarginShortRatio();
// 公式: MarginPurchaseLimit / ShortSaleLimit
```

**含意**:

| 資券比 | 市場意涵 | 買賣壓力 |
|--------|---------|---------|
| > 1.0 | 融資多（買方主導） | 看漲 ↑ |
| = 1.0 | 融資融券均等 | 平衡 → |
| < 1.0 | 融券多（賣方主導） | 看跌 ↓ |
| NaN | 融券為 0（無法計算） | 特殊情況 |

**實際例**:
```
資券比 = 150000 / 80000 = 1.88
→ 融資籌碼是融券的 1.88 倍
→ 買方明顯更強勢
```

### 2. 融資買賣超

```java
long netChange = marginData.getMarginPurchaseNetChange();
// 公式: MarginPurchaseBuy - MarginPurchaseSell
```

**含意**:
- 正數: 融資買超（看好股票的人較多）
- 負數: 融資賣超（看空股票的人較多）

### 3. 融券買賣超

```java
long shortNetChange = marginData.getShortSaleNetChange();
// 公式: ShortSaleBuy - ShortSaleSell
```

**含意**:
- 正數: 融券買回（借票的人開始回補）
- 負數: 融券放空（借票放空的信心）

### 4. 日期變化 (與前一日比較)

```java
long marginChange = marginData.getMarginDayChange(previousMarginLimit);
long shortChange = marginData.getShortDayChange(previousShortLimit);
```

---

## 💼 實際使用場景

### 場景 1: 融資爆增（看漲訊號）

```
前一日: 融資 100,000 張
今日:   融資 150,000 張
變化:   +50,000 張

含意: 買方氣勢強勁，融資大增
風險: 融資過高可能面臨軋空
```

### 場景 2: 融券大量回補（空頭翻多）

```
融券買進 (ShortSaleBuy)  = 8,000 張
融券賣出 (ShortSaleSell) = 2,000 張
融券淨買回 = 6,000 張

含意: 之前借票放空者現在開始買回
訊號: 可能空頭即將翻多
```

### 場景 3: 資券失衡（高風險區）

```
融資: 500,000 張
融券:  50,000 張
資券比: 10.0

含意: 融資過度集中（高槓桿）
風險: 一旦下跌，融資客集體平倉
     股價可能雪崩式下跌
```

### 場景 4: 融資融券齊漲（多空都難行）

```
融資 ↑ 50,000 張
融券 ↑ 30,000 張

含意: 多空都在加碼
訊號: 市場分歧，觀望為主
```

---

## 🔧 FinMindClient 集成方式

### 新增 API 呼叫方法

```java
@Autowired
private FinMindClient finMindClient;

// 獲取融資融券數據
List<FinMindMarginData> marginData = finMindClient
    .fetchMarginData("2330", "2026-01-01");
```

### 在 MarginPurchaseShortSaleData 中使用

```java
// 假設 FinMindClient 已有此方法
public List<FinMindMarginData> fetchMarginData(String symbol, String startDate) {
    return fetchMarginPurchaseShortSale(symbol, startDate, 
                                        FinMindMarginData.class);
}
```

---

## 📈 分析示例

### 範例代碼: 監控融資暴漲

```java
public void analyzeMarginExplosion(String symbol) {
    List<FinMindMarginData> historyData = 
        finMindClient.fetchMarginData(symbol, "2026-01-01");
    
    if (historyData.size() < 2) return;
    
    FinMindMarginData latest = historyData.get(historyData.size() - 1);
    FinMindMarginData previous = historyData.get(historyData.size() - 2);
    
    long marginChange = latest.getMarginDayChange(previous.getMarginPurchaseLimit());
    double ratio = latest.getMarginShortRatio();
    
    // 融資大增 + 資券比偏高 = 警告訊號
    if (marginChange > 50000 && ratio > 2.0) {
        System.out.println("⚠️ 警告: " + symbol + 
            " 融資暴增 " + marginChange + " 張，資券比達 " + 
            String.format("%.2f", ratio) + "，風險升高");
        // 通知交易員
    }
}
```

### 範例代碼: 資券平衡監控

```java
public void monitorMarginBalance(String symbol) {
    FinMindMarginData latest = finMindClient.fetchMarginData(symbol, "2026-05-19")
        .stream().findFirst().orElse(null);
    
    if (latest == null) return;
    
    double ratio = latest.getMarginShortRatio();
    
    if (ratio > 3.0) {
        System.out.println("🔴 高風險: 資券比 " + 
            String.format("%.2f", ratio) + 
            "（融資過度集中）");
    } else if (ratio > 1.5) {
        System.out.println("🟡 中風險: 資券比 " + 
            String.format("%.2f", ratio) + 
            "（買方主導）");
    } else if (ratio < 0.7) {
        System.out.println("🔵 看跌訊號: 資券比 " + 
            String.format("%.2f", ratio) + 
            "（賣方主導）");
    }
}
```

---

## 💡 交易策略應用

### 策略 1: 融資極限判定 (融資爆表)

```
條件:
  融資餘額 > 歷史 90 分位
  ↓
  資券比 > 2.5
  ↓
  融資買賣超 > 10,000 張

風險: 融資客集體追高，隨時可能砍殺
建議: 空單機會或減倉
```

### 策略 2: 融券回補訊號（空轉多）

```
條件:
  融券買進 > 融券賣出
  ↓
  連續 3 日融券買進>賣出
  ↓
  融資同時溫和上升

訊號: 空方認輸，多方接棒
建議: 可考慮布局或加倉
```

### 策略 3: 資券失衡預警 (市場風險)

```
條件:
  資券比 > 5.0
  ↓
  融資連日創新高

風險: 極度不穩定，隨時可能暴殺
建議: 止損離場
```

---

## 📊 數據解讀案例

### 案例: 台積電 (2330) 融資融券監控

```
日期 | 融資 (張) | 融券 (張) | 資券比 | 買賣超 | 訊號
--  | ---- | ---- | ---- | ---- | ----
5/15 | 145000 | 75000 | 1.93 | +3000 | 穩定
5/16 | 158000 | 68000 | 2.32 | +8000 | ⚠️ 融資急增
5/17 | 172000 | 62000 | 2.77 | +12000 | 🔴 高風險
5/18 | 165000 | 70000 | 2.36 | -5000 | ✓ 緩解
5/19 | 150000 | 80000 | 1.88 | -8000 | ✓ 回穩
```

**分析**:
- 5/16-5/17: 融資暴增，資券比飆升 → 風險期
- 5/18-5/19: 融資回落，融券買回 → 危機解除

---

## 🎨 UI 展示建議

### 側邊欄融資融券卡片

```
┌──────────────────────────────┐
│ 📊 融資融券指標              │
│                              │
│ 融資餘額: 150,000 張        │
│ 融券餘額:  80,000 張        │
│ 資券比:     1.88 (正常) ✓  │
│                              │
│ 買賣超:                      │
│  融資: +5,000 張 (買超)     │
│  融券: +2,000 張 (買回)     │
│                              │
│ 風險等級: 🟢 低 (安全)      │
└──────────────────────────────┘
```

---

## 🔗 與其他模組的連結

### 與籌碼分析模組

```
融資融券數據 
  ↓
MoneySourceAnalysis (判定資金來源)
  ├─ 融資客 (散戶槓桿)
  ├─ 融券客 (放空者)
  └─ 自有資金 (穩定主力)
  ↓
InstitutionalService (融合法人數據)
  ↓
整體籌碼景氣指數
```

### 與基本面模組

```
融資融券數據
  ↓
景氣循環判定
  ├─ 融資高 + 基本面差 = 風險區
  ├─ 融資高 + 基本面優 = 看漲區
  └─ 融資低 + 基本面優 = 底部區
  ↓
買點/賣點決策
```

---

## 🧪 測試案例

### 測試 1: DTO 正確映射

```java
@Test
public void testFinMindMarginDataMapping() {
    FinMindMarginData data = new FinMindMarginData(
        "2026-05-19", "2330",
        5000, 3000, 150000,
        2000, 1500, 80000
    );
    
    assertEquals(150000, data.getMarginPurchaseLimit());
    assertEquals(80000, data.getShortSaleLimit());
    double ratio = data.getMarginShortRatio();
    assertEquals(1.875, ratio, 0.001);
}
```

### 測試 2: 衍生計算正確性

```java
@Test
public void testDerivedCalculations() {
    FinMindMarginData data = new FinMindMarginData(
        "2026-05-19", "2330",
        5000, 3000, 150000,
        2000, 1500, 80000
    );
    
    // 融資買賣超
    assertEquals(2000, data.getMarginPurchaseNetChange());
    
    // 融券買賣超
    assertEquals(500, data.getShortSaleNetChange());
    
    // 資券比
    assertEquals(1.875, data.getMarginShortRatio(), 0.001);
}
```

---

## 📝 API 集成檢查清單

- [x] FinMindMarginData DTO 已建立
- [x] Jackson 序列化配置正確（`FinMindMarginResponse` 已由 `RestTemplate` production 路徑使用）
- [x] FinMindClient 已支援 `fetchMarginData()` 與 `fetchMarginHistory()`
- [x] 衍生計算方法完整，並由 `MarginAnalysisService`、`RadarService`、`MarketCrossScannerService` 使用
- [x] 異常處理（融券為 0 時回傳 `Double.NaN`，UI 顯示 N/A）
- [x] 單元測試通過（`FinMindMarginDataTest`、`MarginAnalysisServiceTest`）
- [x] 前端 UI 已整合（`index.html`、`dashboard.html` 顯示融資趨勢、資券比與軋空警示）

> 現況更新（2026-07-26）：production 整合、UI 與專屬單元測試均已完成；
> 測試涵蓋衍生計算、融券為 0、資料不足、評分門檻及條件疊加。

---

**版本**: v1.0  
**狀態**: ✅ DTO、production 整合與 UI 完成
**下一步**: 視產品需求擴充更多歷史趨勢與實機資料驗證

