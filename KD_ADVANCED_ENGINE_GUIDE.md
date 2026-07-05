# 🚀 KD 高級動能引擎使用指南

## 📋 概述

KD 高級動能引擎是一個專業級的技術分析工具，能夠識別：
- ✅ 高檔/低檔鈍化（飆股基因）
- ✅ 黃金交叉/死亡交叉位置細化
- ✅ 低檔背離（反轉訊號）
- ✅ 綜合動能評分

---

## 📦 核心類

### 1. KdData.java - 數據模型

```java
public class KdData {
    private String date;        // 日期（YYYY-MM-DD）
    private double closePrice;  // 收盤價
    private double kValue;      // K 值（0-100）
    private double dValue;      // D 值（0-100）
}
```

**工具方法**：
- `isHighZone()` - 是否在高檔（K >= 80）
- `isLowZone()` - 是否在低檔（K <= 20）
- `isMidZone()` - 是否在中軸（K = 45-55）
- `isKAboveD()` - K 線是否在 D 線之上
- `getKdDiff()` - KD 差值
- `isValid()` - 數據驗證

### 2. KdAdvancedService.java - 分析引擎

```java
@Service
public class KdAdvancedService {
    // 簡化版：只返回評分
    public int analyzeKdStrength(List<KdData> kdHistory)
    
    // 完整版：返回評分 + 信號
    public AnalysisResult analyze(List<KdData> kdHistory)
}
```

---

## 🎯 評分規則

| 訊號類型 | 條件 | 評分 | 說明 |
|---------|------|------|------|
| 🔥 高檔鈍化 | 連續3天 K >= 80 | **+15** | 飆股基因，極端多頭 |
| ⚠️ 低檔鈍化 | 連續3天 K <= 20 | **-15** | 千萬不可抄底 |
| ✅ 低檔黃金交叉 | K 上穿 D，且 K < 30 | **+10** | 安全打底進場點 |
| 🚀 中軸黃金交叉 | K 上穿 D，且 K = 45-55 | **+12** | 多頭加速點 |
| ⬇️ 高檔死亡交叉 | K 下穿 D，且 K > 70 | **-12** | 波段獲利了結 |
| 🚀 低檔背離 | 價跌 + K 升，且 K < 40 | **+10** | 反轉訊號 |

**評分範圍**：-15 ~ +37

**動能等級**：
- 極強：>= 20
- 強：>= 10
- 中性：>= -5
- 弱：>= -10
- 極弱：< -10

---

## 💻 使用示例

### 示例 1：基礎用法（只獲取評分）

```java
@Service
public class MyStockService {
    
    @Autowired
    private KdAdvancedService kdAdvancedService;
    
    public void analyzeStock(String symbol) {
        // 準備 KD 歷史資料（至少 5 筆）
        List<KdData> kdHistory = new ArrayList<>();
        kdHistory.add(new KdData("2026-05-14", 580.0, 65.5, 62.3));
        kdHistory.add(new KdData("2026-05-15", 585.0, 68.2, 64.1));
        kdHistory.add(new KdData("2026-05-16", 590.0, 72.8, 67.5));
        kdHistory.add(new KdData("2026-05-17", 595.0, 78.5, 71.2));
        kdHistory.add(new KdData("2026-05-20", 600.0, 82.3, 75.8));
        
        // 獲取 KD 動能評分
        int score = kdAdvancedService.analyzeKdStrength(kdHistory);
        System.out.println("KD 動能評分：" + score);
    }
}
```

### 示例 2：完整分析（獲取評分 + 信號）

```java
public void fullAnalysis(String symbol) {
    List<KdData> kdHistory = prepareKdData(symbol);
    
    // 完整分析
    KdAdvancedService.AnalysisResult result = kdAdvancedService.analyze(kdHistory);
    
    // 獲取評分
    int score = result.getScore();
    
    // 獲取信號列表
    List<String> signals = result.getSignals();
    
    // 獲取動能等級
    String level = result.getStrengthLevel();
    
    System.out.println("評分：" + score);
    System.out.println("動能：" + level);
    System.out.println("信號：");
    for (String signal : signals) {
        System.out.println("  - " + signal);
    }
}
```

**輸出示例**：
```
📊 開始分析 KD 動能：K=82.3, D=75.8
🔥 強勢高檔鈍化！極端多頭動能
📊 KD 分析完成，總評分：15 分

評分：15
動能：強
信號：
  - 🔥 強勢高檔鈍化：極端多頭動能（連續3天 K >= 80）
```

### 示例 3：整合到 Controller

```java
@RestController
@RequestMapping("/api/kd")
public class KdController {
    
    @Autowired
    private KdAdvancedService kdAdvancedService;
    
    @GetMapping("/{symbol}/analysis")
    public Map<String, Object> analyzeKd(@PathVariable String symbol) {
        List<KdData> kdHistory = fetchKdHistory(symbol);
        KdAdvancedService.AnalysisResult result = kdAdvancedService.analyze(kdHistory);
        
        Map<String, Object> response = new HashMap<>();
        response.put("symbol", symbol);
        response.put("score", result.getScore());
        response.put("level", result.getStrengthLevel());
        response.put("signals", result.getSignals());
        response.put("timestamp", LocalDateTime.now());
        
        return response;
    }
}
```

**API 響應示例**：
```json
{
  "symbol": "2330",
  "score": 15,
  "level": "強",
  "signals": [
    "🔥 強勢高檔鈍化：極端多頭動能（連續3天 K >= 80）"
  ],
  "timestamp": "2026-05-20T14:30:00"
}
```

---

## 🔍 應用場景

### 場景 1：飆股篩選（高檔鈍化）

```java
public List<String> findStrongStocks(List<String> symbols) {
    List<String> strongStocks = new ArrayList<>();
    
    for (String symbol : symbols) {
        List<KdData> kdHistory = fetchKdData(symbol);
        int score = kdAdvancedService.analyzeKdStrength(kdHistory);
        
        // 篩選出高檔鈍化的強勢股（評分 >= 15）
        if (score >= 15) {
            strongStocks.add(symbol);
        }
    }
    
    return strongStocks;
}
```

### 場景 2：低檔進場點（黃金交叉 + 背離）

```java
public boolean isBuySignal(String symbol) {
    List<KdData> kdHistory = fetchKdData(symbol);
    KdAdvancedService.AnalysisResult result = kdAdvancedService.analyze(kdHistory);
    
    // 檢查是否有低檔買進信號
    List<String> signals = result.getSignals();
    for (String signal : signals) {
        if (signal.contains("低檔黃金交叉") || signal.contains("低檔背離")) {
            return true;
        }
    }
    
    return false;
}
```

### 場景 3：獲利了結點（高檔死亡交叉）

```java
public boolean isSellSignal(String symbol) {
    List<KdData> kdHistory = fetchKdData(symbol);
    KdAdvancedService.AnalysisResult result = kdAdvancedService.analyze(kdHistory);
    
    // 檢查是否有高檔賣出信號
    return result.getSignals().stream()
            .anyMatch(s -> s.contains("高檔死亡交叉"));
}
```

---

## 📊 數據準備

### 方法 1：從 FinMind API 獲取

```java
public List<KdData> fetchKdFromFinMind(String symbol, String startDate) {
    // 假設你已經有 FinMindClient
    List<TechnicalIndicatorData> rawData = finMindClient.fetchTechnicalIndicator(
        symbol, 
        startDate, 
        "KD"
    );
    
    List<KdData> kdHistory = new ArrayList<>();
    for (TechnicalIndicatorData data : rawData) {
        KdData kd = new KdData();
        kd.setDate(data.getDate());
        kd.setClosePrice(data.getClosePrice());
        kd.setKValue(data.getK());
        kd.setDValue(data.getD());
        kdHistory.add(kd);
    }
    
    return kdHistory;
}
```

### 方法 2：從本地數據庫讀取

```java
public List<KdData> fetchKdFromDatabase(String symbol) {
    String sql = "SELECT date, close_price, k_value, d_value " +
                 "FROM kd_indicators " +
                 "WHERE symbol = ? " +
                 "ORDER BY date DESC " +
                 "LIMIT 30";
    
    return jdbcTemplate.query(sql, new Object[]{symbol}, (rs, rowNum) -> 
        new KdData(
            rs.getString("date"),
            rs.getDouble("close_price"),
            rs.getDouble("k_value"),
            rs.getDouble("d_value")
        )
    );
}
```

### 方法 3：自己計算 KD 值

```java
public List<KdData> calculateKd(List<PriceData> priceHistory) {
    List<KdData> kdHistory = new ArrayList<>();
    int period = 9;  // KD 週期
    
    for (int i = period; i < priceHistory.size(); i++) {
        // 取最近 9 天的高低收
        List<PriceData> window = priceHistory.subList(i - period, i);
        
        double highest = window.stream().mapToDouble(PriceData::getHigh).max().orElse(0);
        double lowest = window.stream().mapToDouble(PriceData::getLow).min().orElse(0);
        double close = window.get(window.size() - 1).getClose();
        
        // RSV = (今收 - 最低) / (最高 - 最低) * 100
        double rsv = (close - lowest) / (highest - lowest) * 100;
        
        // K = 前K * 2/3 + RSV * 1/3
        // D = 前D * 2/3 + K * 1/3
        // (簡化版，實際需要迭代計算)
        
        KdData kd = new KdData();
        kd.setDate(window.get(window.size() - 1).getDate());
        kd.setClosePrice(close);
        // ... 設置 K 和 D 值
        
        kdHistory.add(kd);
    }
    
    return kdHistory;
}
```

---

## 🎯 最佳實踐

### 1. 數據驗證

```java
public void analyzeWithValidation(String symbol) {
    List<KdData> kdHistory = fetchKdData(symbol);
    
    // 驗證數據完整性
    if (kdHistory.size() < 5) {
        throw new IllegalArgumentException("KD 資料不足（至少需要 5 筆）");
    }
    
    // 驗證數據有效性
    for (KdData kd : kdHistory) {
        if (!kd.isValid()) {
            throw new IllegalArgumentException("KD 資料無效：" + kd);
        }
    }
    
    // 執行分析
    KdAdvancedService.AnalysisResult result = kdAdvancedService.analyze(kdHistory);
    System.out.println(result);
}
```

### 2. 與其他指標結合

```java
public TradeSignal comprehensiveAnalysis(String symbol) {
    // KD 動能分析
    List<KdData> kdHistory = fetchKdData(symbol);
    int kdScore = kdAdvancedService.analyzeKdStrength(kdHistory);
    
    // MACD 分析
    int macdScore = macdService.analyze(symbol);
    
    // 法人買賣分析
    int institutionalScore = institutionalService.calculateScore(symbol);
    
    // 綜合評分
    int totalScore = kdScore + macdScore + institutionalScore;
    
    if (totalScore > 30) return TradeSignal.STRONG_BUY;
    if (totalScore > 15) return TradeSignal.BUY;
    if (totalScore < -15) return TradeSignal.SELL;
    return TradeSignal.HOLD;
}
```

### 3. 批量分析

```java
public Map<String, Integer> batchAnalyze(List<String> symbols) {
    Map<String, Integer> results = new HashMap<>();
    
    for (String symbol : symbols) {
        try {
            List<KdData> kdHistory = fetchKdData(symbol);
            int score = kdAdvancedService.analyzeKdStrength(kdHistory);
            results.put(symbol, score);
        } catch (Exception e) {
            System.err.println("分析 " + symbol + " 失敗：" + e.getMessage());
            results.put(symbol, 0);
        }
    }
    
    return results;
}
```

---

## ⚠️ 注意事項

### 1. 數據要求
- ✅ 至少需要 **5 筆歷史資料**
- ✅ 資料必須**按時間順序排列**（舊 → 新）
- ✅ K、D 值範圍必須在 **0-100**
- ✅ 收盤價必須 **> 0**

### 2. 訊號解讀
- 🔥 **高檔鈍化**不是賣出訊號，而是**極強多頭**
- ⚠️ **低檔鈍化**千萬不可抄底，等待背離或黃金交叉
- ✅ **低檔黃金交叉** + **背離**是最強進場點（評分可達 +20）
- ⬇️ **高檔死亡交叉**是獲利了結訊號，不是做空訊號

### 3. 性能優化
- 使用緩存避免重複計算
- 批量獲取數據減少 API 調用
- 異步處理大量股票分析

---

## 📚 相關文檔

- [FinMind API 使用指南](./FINMIND_CLIENT_GUIDE.md)
- [技術指標計算方法](./TECHNICAL_INDICATORS.md)
- [綜合評分系統](./SCORING_PARAMETER_TABLE.md)

---

## 🎉 快速開始

```java
// 1. 注入服務
@Autowired
private KdAdvancedService kdAdvancedService;

// 2. 準備數據
List<KdData> kdHistory = fetchKdData("2330");

// 3. 分析
KdAdvancedService.AnalysisResult result = kdAdvancedService.analyze(kdHistory);

// 4. 使用結果
System.out.println("評分：" + result.getScore());
System.out.println("動能：" + result.getStrengthLevel());
result.getSignals().forEach(System.out::println);
```

---

**版本**: v2.1  
**狀態**: ✅ Production Ready  
**最後更新**: 2026-05-20

_StockPredictor Team - 讓技術分析更專業_ 🚀

