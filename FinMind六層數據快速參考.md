# ⚡ FinMind 六層數據 - 快速參考手冊

## 📌 六個核心 FinMind API 速查表

| # | 數據源 | Enum值 | DTO類 | 核心欄位 | 應用 |
|----|--------|--------|---------|---------|------|
| 1️⃣ | 法人買賣 | `CHIP` | FinMindChipData | buy/sell | ✅ 投信鎖碼 |
| 2️⃣ | 月營收 | `REVENUE` | - | revenue | 📊 基本面 |
| 3️⃣ | **股權分散** | `SHAREHOLDING` | StockHoldingData | 大戶%, 散戶% | 👥 籌碼集中 |
| 4️⃣ | **融資融券** | `MARGIN` | MarginPurchaseShortSaleData | 融資/借券餘額 | ⚠️ 死結預警 |
| 5️⃣ | **股利政策** | `DIVIDEND` | StockDividendData | 現金股利, 殖利率 | 🛡️ 防守評估 |
| 6️⃣ | **當沖統計** | `DAY_TRADE` | StockDayTradingData | 當沖%, 市場熱度 | 🔄 技術失真 |

---

## 🚀 三行代碼查詢各類數據

### 股權分散（千張大戶）
```java
List<StockHoldingData> data = finMindClient.fetchStockHoldingData("2330", "2024-05-01");
System.out.println("大戶控盤度: " + data.get(0).calculateLargeHolderConcentration() + "%");
System.out.println("散戶被套度: " + data.get(0).calculateRetailTrappedRatio() + "%");
```

### 融資融券（死結預警）
```java
List<MarginPurchaseShortSaleData> data = finMindClient.fetchMarginData("2330", "2024-05-01");
if (data.get(0).hasMarginDeadlock(500000L)) { System.out.println("❌ 融資死結！"); }
if (data.get(0).isSqueezeRisk(2000000L)) { System.out.println("🔥 軋空火箭！"); }
```

### 股利政策（高息股評估）
```java
List<StockDividendData> data = finMindClient.fetchDividendData("2330", "2024-05-01");
Double yield = data.get(0).calculateYieldRate(600.0);
System.out.println("殖利率: " + String.format("%.2f%%", yield));
```

### 當沖統計（技術指標失真）
```java
List<StockDayTradingData> data = finMindClient.fetchDayTradingData("2330", "2024-05-01");
Double ratio = data.get(0).calculateDayTradingRatio(5000000L);
if (ratio > 70) { System.out.println("⚠️ 當沖過熱，KD失真"); }
```

---

## 📋 四個關鍵判斷邏輯

### 邏輯 1️⃣：籌碼集中度識別
```
條件：大戶持股 > 50% AND 散戶 < 20%
結果：籌碼極度集中 ✅
風險：易出現單邊行情（可能暴漲或暴跌）
```

### 邏輯 2️⃣：融資死結識別
```
條件：融資餘額 > 500,000張 AND 股價下跌
結果：散戶套牢 ❌
風險：反彈時會遇到散戶高位解套賣壓
```

### 邏輯 3️⃣：軋空火箭預警
```
條件：借券賣出 > 200,000張 AND 股價 > 布林上軌
結果：軋空預警 🔥
風險：外資被迫認賠買回，股價爆發
```

### 邏輯 4️⃣：當沖過熱識別
```
條件：當沖比例 > 70%
結果：短線客主宰市場 🔄
風險：技術指標失真，改用 MA 均線
特徵：KD值 30 → 70 在一個交易日內往返
```

---

## 💡 四種場景的組合判斷

### 場景 A：「投信買 + 大戶也買」= 🚀 最強信號
```
投信鎖碼評分   > 70 分
大戶持股比例   > 50%
散戶持股比例   < 20%
融資餘額      正常（< 300k張）
當沖比例       < 50%  // 機構主導
─────────────────────────
結論：法人級聯手看多 → 買進信號最強
帳位目標：中線 1-3 個月翻升
```

### 場景 B：「投信買但融資爆增」= ❌ 虛假信號
```
投信鎖碼評分   > 50 分
融資餘額      > 500k張且持續增加
股價          下跌中或盤整
───────────────────────────
結論：投信誘多 / 融資散戶接刀 → 避開
風險：反彈時散戶解套拋售
```

### 場景 C：「高息股 + 投信鎖 + 作帳期」= 📅 季底作帳
```
現金股利      > 4 元（殖利率 > 5%）
除權息日      前 30 天
投信鎖碼評分   > 60 分  
融資餘額      緩步下降
───────────────────────────
結論：季底/ 除權息作帳股 → 可以參與反彈
風險：除權息後可能有填息壓力
```

### 場景 D：「當沖爆表」= ⚠️ 不宜技術交易
```
當沖比例      > 75%
KD 值         1 天內 20→80→30（來回洗盤）
RSI 失真       過度擺盪
───────────────────────────
結論：短線客炒作戰場 → 改用長線策略
建議：使用 MA(20/60) 支撐/阻力，忽視 KD
```

---

## 🧮 五個常用計算方法

### 計算 1：大戶控盤度
```java
// StockHoldingData
Double concentration = data.calculateLargeHolderConcentration();
Boolean isConcentrated = data.isChipHighlyConcentrated();  // > 50%
```

### 計算 2：融資壓力指數
```java
// MarginPurchaseShortSaleData
Integer pressureIndex = data.calculateMarginPressureIndex();  // 0-100
if (pressureIndex > 70) { System.out.println("融資高危"); }
```

### 計算 3：股票殖利率
```java
// StockDividendData
Double yield = data.calculateYieldRate(currentPrice);
Boolean isHighYield = data.isHighYieldStock(currentPrice, 5.0);  // > 5%
```

### 計算 4：當沖比例
```java
// StockDayTradingData
Double ratio = data.calculateDayTradingRatio(totalVolume);  // %
Integer trustLevel = data.evaluteTechnicalIndicatorValidity(totalVolume);  // 0-100
```

### 計算 5：綜合評分
```java
// 自定義綜合評分
Integer score = 0;

// 籌碼面（40 分）
score += data.holding.calculateDisperseScore() * 0.4;

// 基本面（30 分）
score += data.dividend.calculateDividendScore() * 0.3;

// 市場熱度（30 分）
score += (100 - data.dayTrading.calculateDayTradingHeatIndex()) * 0.3;

return Math.min(score, 100);
```

---

## ✅ 快速診斷清單

詢問以下 6 個問題，快速判斷股票狀態：

```
1. 投信鎖碼評分 >= 60?    ☐ 是  ☐ 否
2. 大戶持股比 >= 50%?     ☐ 是  ☐ 否
3. 融資餘額 <= 300k張?    ☐ 是  ☐ 否  
4. 殖利率 >= 5%?          ☐ 是  ☐ 否
5. 當沖比例 <= 50%?       ☐ 是  ☐ 否
6. 借券賣出 <= 200k張?    ☐ 是  ☐ 否

計分：
  是 >= 5 個 → 🚀 強勢股，可考慮進場
  是 = 3-4 個 → 🔶 中立，等待訊號確認
  是 <= 2 個 → 🔴 弱勢股，建議觀望
```

---

## 🎯 按交易策略查詢需求

### 短線交易（1-5 天）
```
優先級 1：當沖統計       → 判斷市場是否過熱
優先級 2：融資融券       → 識別做空方向
優先級 3：股權分散       → 確認主力控盤度
```

### 波段交易（1-3 週）
```
優先級 1：股權分散       → 大戶是否持續買進
優先級 2：投信鎖碼       → 中期籌碼是否強勢
優先級 3：融資融券       → 散戶槓桿是否過度
優先級 4：當沖統計       → 避開過熱個股
```

### 長線投資（3 個月 +）
```
優先級 1：股利政策       → 高息股防守評估
優先級 2：月營收趨勢     → 基本面成長性
優先級 3：股權分散       → 大戶承接力
優先級 4：融資融券       → 是否有被做空
```

---

## 🎨 API 調用程式碼樣板

### 樣板 1：單股票完整分析
```java
public void analyzeOneStock(String symbol) {
    String date = LocalDate.now().toString();
    
    // 1. 籌碼層面
    List<StockHoldingData> holding = finMindClient.fetchStockHoldingData(symbol, date);
    List<MarginPurchaseShortSaleData> margin = finMindClient.fetchMarginData(symbol, date);
    
    // 2. 基本面
    List<StockDividendData> dividend = finMindClient.fetchDividendData(symbol, date);
    
    // 3. 市場面
    List<StockDayTradingData> dayTrade = finMindClient.fetchDayTradingData(symbol, date);
    
    // 4. 綜合判斷
    // ... 邏輯判斷與評分 ...
}
```

### 樣板 2：批量監控多股票
```java
public void monitorPortfolio(List<String> symbols) {
    String date = LocalDate.now().toString();
    
    for (String symbol : symbols) {
        List<StockHoldingData> holding = finMindClient.fetchStockHoldingData(symbol, date);
        // 記錄關鍵指標
        logMetric(symbol, "大戶持股", holding.get(0).getLargeHoldersOverThousandRatio());
    }
}
```

### 樣板 3：預警系統
```java
public List<String> detectWarnings(List<String> symbols) {
    List<String> warnings = new ArrayList<>();
    
    for (String symbol : symbols) {
        List<MarginPurchaseShortSaleData> margin = finMindClient.fetchMarginData(symbol, today);
        if (margin.get(0).hasMarginDeadlock(500000L)) {
            warnings.add("🚨 " + symbol + ": 融資死結預警");
        }
    }
    
    return warnings;
}
```

---

## 📞 最常見的 3 個問題

### Q1: 數據會不會有延遲？
A: FinMind 數據通常在隔日更新。若需即時數據，建議結合 TWSE 和本地計算。

### Q2: 如何判斷當日的當沖比例？
A: 
```java
// 從 StockDayTradingData 取得當沖成交量
// 再除以當日總成交量
Double ratio = dayTradingData.calculateDayTradingRatio(totalVolume);
```

### Q3: 為什麼同一股票不同來源的數據有差異？
A: 技術差異和統計口徑不同。建議多源交叉驗證，而不是單一數據源。

---

## 🎊 你現在擁有：

✅ **6 層 FinMind API** - 完整集成  
✅ **4 個核心 DTO** - 數據模型完備  
✅ **10+ 計算方法** - 業務邏輯豐富  
✅ **4 個判斷場景** - 決策支援完善  
✅ **自動容錯機制** - 系統可靠性高  

**準備好進行專業級量化交易了！** 🚀


