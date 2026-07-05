# 三率三升過濾引擎 - 使用示例

## 📋 快速使用指南

### 1. 基本呼叫

```java
@Service
public class StockAnalysisService {
    
    @Autowired
    private AdvancedFundamentalService advancedFundamentalService;
    
    @Autowired
    private FinMindClient finMindClient;
    
    /**
     * 檢測單支股票是否為三率三升黑馬股
     */
    public void analyzeStock(String symbol) {
        // Step 1: 從 FinMind 取得原始財報資料
        List<FinMindFinancialData> rawFinancials = 
            finMindClient.fetchFinancialStatements(symbol);
        
        // Step 2: 呼叫三率三升過濾引擎
        int tripleRiseScore = advancedFundamentalService.checkTripleRiseScore(rawFinancials);
        
        // Step 3: 判定結果
        if (tripleRiseScore == 8) {
            System.out.println("🔥 黑馬股偵測成功: " + symbol);
            System.out.println("   得分: " + tripleRiseScore + "/8");
            notifyTrader(symbol); // 通知交易員
        } else {
            System.out.println("⚠️  未符合三率三升條件: " + symbol);
        }
    }
}
```

### 2. 批量篩選

```java
@Service
public class StockScreeningService {
    
    @Autowired
    private AdvancedFundamentalService advancedFundamentalService;
    
    @Autowired
    private FinMindClient finMindClient;
    
    /**
     * 從股票清單中篩選出三率三升黑馬股
     */
    public List<String> screenTripleRiseStocks(List<String> symbols) {
        List<String> tripleRiseStocks = new ArrayList<>();
        
        for (String symbol : symbols) {
            try {
                // 獲取財報資料
                List<FinMindFinancialData> rawFinancials = 
                    finMindClient.fetchFinancialStatements(symbol);
                
                // 判定是否三率三升
                int score = advancedFundamentalService.checkTripleRiseScore(rawFinancials);
                
                if (score == 8) {
                    tripleRiseStocks.add(symbol);
                    System.out.println("✅ " + symbol + " 三率三升成立");
                }
            } catch (Exception e) {
                System.err.println("❌ 分析 " + symbol + " 失敗: " + e.getMessage());
            }
        }
        
        return tripleRiseStocks;
    }
}
```

### 3. 整合進 RadarService

```java
@Service
public class RadarService {
    
    @Autowired
    private AdvancedFundamentalService advancedFundamentalService;
    
    @Autowired
    private FinMindClient finMindClient;
    
    /**
     * 計算雷達圖評分時整合三率三升
     */
    public RadarScoreResult calculateRadarScores(String symbol) {
        RadarScoreResult result = new RadarScoreResult();
        
        // ... 其他評分邏輯 ...
        
        // 新增：三率三升評分
        try {
            List<FinMindFinancialData> rawFinancials = 
                finMindClient.fetchFinancialStatements(symbol);
            int tripleRiseScore = 
                advancedFundamentalService.checkTripleRiseScore(rawFinancials);
            
            // 加權整合（可視需要調整權重）
            result.fundamental = (result.fundamental * 0.5 + tripleRiseScore * 0.5);
            
        } catch (Exception e) {
            logger.warning("無法取得三率三升評分: " + e.getMessage());
        }
        
        return result;
    }
}
```

### 4. REST Controller 整合

```java
@RestController
@RequestMapping("/api/stock")
public class StockController {
    
    @Autowired
    private StockAnalysisService analysisService;
    
    /**
     * GET /api/stock/check-triple-rise?symbol=2330
     */
    @GetMapping("/check-triple-rise")
    public ResponseEntity<?> checkTripleRise(
            @RequestParam String symbol) {
        
        try {
            Map<String, Object> response = new HashMap<>();
            
            // 調用分析服務
            analysisService.analyzeStock(symbol);
            
            response.put("symbol", symbol);
            response.put("status", "success");
            response.put("message", "三率三升分析完成");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.status(500)
                .body(Map.of("error", e.getMessage()));
        }
    }
}
```

---

## 🧪 測試示例

### 單元測試

```java
@SpringBootTest
public class TripleRiseFilterTest {
    
    @Autowired
    private AdvancedFundamentalService service;
    
    /**
     * 測試：所有三率都上升
     */
    @Test
    public void testAllThreeRise() {
        List<FinMindFinancialData> data = Arrays.asList(
            // 2025 Q4 (前一季)
            new FinMindFinancialData("2025-12-31", "2330", "GrossProfitMargin", 57.50),
            new FinMindFinancialData("2025-12-31", "2330", "OperatingProfitMargin", 34.20),
            new FinMindFinancialData("2025-12-31", "2330", "NetProfitMargin", 27.30),
            // 2026 Q1 (最新季)
            new FinMindFinancialData("2026-03-31", "2330", "GrossProfitMargin", 58.56),
            new FinMindFinancialData("2026-03-31", "2330", "OperatingProfitMargin", 35.42),
            new FinMindFinancialData("2026-03-31", "2330", "NetProfitMargin", 28.75)
        );
        
        int score = service.checkTripleRiseScore(data);
        
        // 預期：8 分（三率三升）
        assertEquals(8, score);
    }
    
    /**
     * 測試：毛利下降，其他上升（不符合三率三升）
     */
    @Test
    public void testGrossMarginDown() {
        List<FinMindFinancialData> data = Arrays.asList(
            // 2025 Q4
            new FinMindFinancialData("2025-12-31", "2330", "GrossProfitMargin", 57.50),
            new FinMindFinancialData("2025-12-31", "2330", "OperatingProfitMargin", 34.20),
            new FinMindFinancialData("2025-12-31", "2330", "NetProfitMargin", 27.30),
            // 2026 Q1
            new FinMindFinancialData("2026-03-31", "2330", "GrossProfitMargin", 56.80), // ↓ 下降
            new FinMindFinancialData("2026-03-31", "2330", "OperatingProfitMargin", 35.42),
            new FinMindFinancialData("2026-03-31", "2330", "NetProfitMargin", 28.75)
        );
        
        int score = service.checkTripleRiseScore(data);
        
        // 預期：0 分（不符合三率三升）
        assertEquals(0, score);
    }
    
    /**
     * 測試：資料為空
     */
    @Test
    public void testEmptyData() {
        int score = service.checkTripleRiseScore(new ArrayList<>());
        
        // 預期：0 分
        assertEquals(0, score);
    }
    
    /**
     * 測試：只有 1 季資料
     */
    @Test
    public void testInsufficientQuarters() {
        List<FinMindFinancialData> data = Arrays.asList(
            new FinMindFinancialData("2026-03-31", "2330", "GrossProfitMargin", 58.56),
            new FinMindFinancialData("2026-03-31", "2330", "OperatingProfitMargin", 35.42),
            new FinMindFinancialData("2026-03-31", "2330", "NetProfitMargin", 28.75)
        );
        
        int score = service.checkTripleRiseScore(data);
        
        // 預期：0 分（無法進行 QoQ 比較）
        assertEquals(0, score);
    }
    
    /**
     * 測試：支援中文 type 名稱
     */
    @Test
    public void testChineseTypeNames() {
        List<FinMindFinancialData> data = Arrays.asList(
            // 使用中文 type
            new FinMindFinancialData("2025-12-31", "2330", "毛利率", 57.50),
            new FinMindFinancialData("2025-12-31", "2330", "營業利益率", 34.20),
            new FinMindFinancialData("2025-12-31", "2330", "淨利率", 27.30),
            new FinMindFinancialData("2026-03-31", "2330", "毛利率", 58.56),
            new FinMindFinancialData("2026-03-31", "2330", "營業利益率", 35.42),
            new FinMindFinancialData("2026-03-31", "2330", "淨利率", 28.75)
        );
        
        int score = service.checkTripleRiseScore(data);
        
        // 預期：8 分（應正確識別中文 type）
        assertEquals(8, score);
    }
}
```

### 集成測試（含 Mock）

```java
@SpringBootTest
public class TripleRiseIntegrationTest {
    
    @Autowired
    private StockScreeningService screeningService;
    
    @MockBean
    private FinMindClient finMindClient;
    
    @Test
    public void testScreeningMultipleStocks() {
        // Mock FinMind 回傳資料
        when(finMindClient.fetchFinancialStatements("2330"))
            .thenReturn(Arrays.asList(
                new FinMindFinancialData("2025-12-31", "2330", "GrossProfitMargin", 57.50),
                new FinMindFinancialData("2025-12-31", "2330", "OperatingProfitMargin", 34.20),
                new FinMindFinancialData("2025-12-31", "2330", "NetProfitMargin", 27.30),
                new FinMindFinancialData("2026-03-31", "2330", "GrossProfitMargin", 58.56),
                new FinMindFinancialData("2026-03-31", "2330", "OperatingProfitMargin", 35.42),
                new FinMindFinancialData("2026-03-31", "2330", "NetProfitMargin", 28.75)
            ));
        
        when(finMindClient.fetchFinancialStatements("2454"))
            .thenReturn(Arrays.asList(
                new FinMindFinancialData("2025-12-31", "2454", "GrossProfitMargin", 30.50),
                new FinMindFinancialData("2025-12-31", "2454", "OperatingProfitMargin", 12.20),
                new FinMindFinancialData("2025-12-31", "2454", "NetProfitMargin", 8.30),
                new FinMindFinancialData("2026-03-31", "2454", "GrossProfitMargin", 29.80), // ↓ 下降
                new FinMindFinancialData("2026-03-31", "2454", "OperatingProfitMargin", 13.42),
                new FinMindFinancialData("2026-03-31", "2454", "NetProfitMargin", 9.75)
            ));
        
        // 篩選
        List<String> results = screeningService.screenTripleRiseStocks(
            Arrays.asList("2330", "2454")
        );
        
        // 預期：只有 2330 符合三率三升
        assertEquals(1, results.size());
        assertTrue(results.contains("2330"));
        assertFalse(results.contains("2454"));
    }
}
```

---

## 📊 日誌範例

### 三率三升成功

```
📊 季度 2025-12-31 打包完成: QuarterMargin{毛利=57.50%, 營業=34.20%, 淨利=27.30%}
📊 季度 2026-03-31 打包完成: QuarterMargin{毛利=58.56%, 營業=35.42%, 淨利=28.75%}
比較季度: 前期 2025-12-31 [QuarterMargin{毛利=57.50%, 營業=34.20%, 淨利=27.30%}] 
         vs 本期 2026-03-31 [QuarterMargin{毛利=58.56%, 營業=35.42%, 淨利=28.75%}]
三率漲跌: 毛利[↑] 營業[↑] 淨利[↑]
🔥 偵測到三率三升黑馬股！季度：2026-03-31 
   | 毛利 57.50% → 58.56%
   | 營業 34.20% → 35.42%
   | 淨利 27.30% → 28.75%
```

### 三率三升失敗

```
📊 季度 2025-12-31 打包完成: QuarterMargin{毛利=30.50%, 營業=12.20%, 淨利=8.30%}
📊 季度 2026-03-31 打包完成: QuarterMargin{毛利=29.80%, 營業=13.42%, 淨利=9.75%}
比較季度: 前期 2025-12-31 [QuarterMargin{毛利=30.50%, 營業=12.20%, 淨利=8.30%}] 
         vs 本期 2026-03-31 [QuarterMargin{毛利=29.80%, 營業=13.42%, 淨利=9.75%}]
三率漲跌: 毛利[↓] 營業[↑] 淨利[↑]
⚠️  未滿足三率三升條件（需全三項同步上升）
```

---

## 🔍 除錯技巧

### 1. 啟用 DEBUG 日誌

```properties
# application.properties
logging.level.org.gtalent.AdvancedFundamentalService=DEBUG
```

### 2. 檢查 TreeMap 排序

```java
TreeMap<String, QuarterMargin> quarterMap = new TreeMap<>();
// ... 打包資料 ...
System.out.println("季度清單: " + new ArrayList<>(quarterMap.keySet()));
// 預期輸出: [2025-12-31, 2026-03-31, ...]
```

### 3. 驗證 FinMindFinancialData 解析

```java
// 確認 type 欄位被正確解析
rawFinancials.forEach(data -> {
    System.out.println("Type: [" + data.getType() + "] Value: " + data.getValue());
});
```

### 4. 檢查 QuarterMargin 數值

```java
quarterMap.forEach((date, margin) -> {
    System.out.println(date + " → " +
        String.format("毛利=%.2f%%, 營業=%.2f%%, 淨利=%.2f%%",
                      margin.grossMargin, margin.operatingMargin, margin.netMargin));
});
```

---

## 🚀 生產環境部署

### Spring Boot Application 設定

```yaml
# application.yml
spring:
  application:
    name: stock-predictor
    
logging:
  level:
    org.gtalent.AdvancedFundamentalService: INFO
    org.gtalent.StockAnalysisService: INFO

# FinMind API 設定
finmind:
  api:
    url: https://api.finmindtrade.com/api/v4/data
    token: ${FINMIND_API_TOKEN}
```

### Docker 部署

```dockerfile
FROM openjdk:11-jre-slim

COPY target/stock-predictor.jar app.jar

ENTRYPOINT ["java", 
    "-Dlogging.level.org.gtalent=INFO",
    "-jar", "app.jar"]
```

---

**版本**: v1.0 | **完成日期**: 2026-05-19

