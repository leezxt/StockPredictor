# FinMind HTTP Client 服務實現指南

## 📋 概述
本文檔說明如何使用 `FinMindClient` 服務來調用 FinMind API 獲取台灣股票的籌碼資料。

## 🔧 已實現的組件

### 1. **AppConfig.java** - 應用配置類
負責配置 Spring Bean，特別是 RestTemplate：
- 連接超時：5 秒
- 讀取超時：10 秒

### 2. **FinMindClient.java** - FinMind HTTP 客戶端
提供以下方法來獲取籌碼資料：

#### 基礎方法
```java
// 獲取基礎籌碼資料
List<FinMindChipData> fetchChipData(String symbol, String startDate)
```

#### 帶重試機制的方法
```java
// 獲取籌碼資料，失敗時自動重試
List<FinMindChipData> fetchChipDataWithRetry(String symbol, String startDate, int retries)
```

#### 日期範圍方法
```java
// 獲取指定日期範圍內的籌碼資料
List<FinMindChipData> fetchChipDataByDateRange(String symbol, String startDate, String endDate)
```

#### 獲取最新的方法
```java
// 獲取最新的籌碼資料（使用當天日期）
List<FinMindChipData> fetchLatestChipData(String symbol)
```

## 📝 使用示例

### 示例 1：基本使用
```java
@Service
public class MyStockService {
    
    @Autowired
    private FinMindClient finMindClient;
    
    public void analyzeStock(String symbol) {
        // 獲取從 2024-01-01 開始的籌碼資料
        List<FinMindChipData> chipData = finMindClient.fetchChipData(symbol, "2024-01-01");
        
        for (FinMindChipData data : chipData) {
            System.out.println("日期: " + data.getDate());
            System.out.println("代號: " + data.getStockId());
            System.out.println("法人買進: " + data.getBuy());
            System.out.println("法人賣出: " + data.getSell());
        }
    }
}
```

### 示例 2：使用重試機制
```java
// 如果首次請求失敗，最多重試 3 次
List<FinMindChipData> chipData = finMindClient.fetchChipDataWithRetry(
    "2330",      // 股票代號
    "2024-01-01", // 開始日期
    3             // 重試次數
);
```

### 示例 3：獲取日期範圍內的資料
```java
// 獲取 2024 年 1 月至 3 月的籌碼資料
List<FinMindChipData> rangeData = finMindClient.fetchChipDataByDateRange(
    "2330",
    "2024-01-01",
    "2024-03-31"
);
```

### 示例 4：獲取最新資料
```java
// 使用當天日期作為起始點獲取最新資料
List<FinMindChipData> latestData = finMindClient.fetchLatestChipData("2330");
```

## 🔐 配置說明

### application.properties 配置
```properties
# FinMind API 配置
finmind.api.token=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...
finmind.api.url=https://api.finmindtrade.com/api/v4/data
```

**說明：**
- `finmind.api.token`：您的 FinMind API Token（從 FinMind 官網獲取）
- `finmind.api.url`：FinMind API 的基礎 URL

## 📊 FinMindChipData 數據結構

| 欄位     | 類型   | 說明                      |
|---------|--------|--------------------------|
| date    | String | 日期（格式：YYYY-MM-DD）  |
| stockId | String | 股票代號（如："2330"）   |
| name    | String | 股票名稱                  |
| buy     | long   | 法人買進股數              |
| sell    | long   | 法人賣出股數              |

## 🔄 與現有服務的集成

### 集成到 InstitutionalService
可以增強 `InstitutionalService` 以使用 FinMind API 作為備份源：

```java
@Service
public class EnhancedInstitutionalService {
    
    @Autowired
    private FinMindClient finMindClient;
    
    @Autowired
    private TwseService twseService;
    
    public List<InstitutionalTrade> getInstitutionalData(String symbol, int days) {
        // 優先使用本地 TWSE 數據
        List<InstitutionalTrade> localData = twseService.fetchRecentInstitutionalData(symbol, days);
        
        if (localData.isEmpty()) {
            // 如果失敗，則使用 FinMind API 作為備份
            String startDate = calculateStartDate(days);
            List<FinMindChipData> finmindData = finMindClient.fetchChipData(symbol, startDate);
            
            // 轉換 FinMind 數據為 InstitutionalTrade 格式
            return convertFinmindData(finmindData);
        }
        
        return localData;
    }
}
```

## 🚨 錯誤處理

所有方法都已內建錯誤處理機制：
- 連接超時會返回空列表
- API 錯誤會被記錄並返回空列表
- 異常會被捕捉並記錄到日誌

## 📊 日誌輸出

客戶端會輸出以下日誌信息：
```
INFO: 🔗 發送請求到 FinMind API: 2330 從 2024-01-01 開始
INFO: ✅ 成功獲取 FinMind 籌碼資料: 狀態碼=1, 資料筆數=20
WARNING: ⚠️  FinMind API 返回非成功狀態: 500
SEVERE: 🚨 [FinMind 備援也掛了] 無法取得籌碼資料: Connection refused
```

## 🎯 最佳實踐

1. **使用依賴注入**：使用 `@Autowired` 注入 `FinMindClient`
2. **異常處理**：本客戶端已處理所有異常，無需額外捕捉
3. **日期格式**：始終使用 `YYYY-MM-DD` 格式的日期字符串
4. **超時設置**：默認超時設置適合大多數場景
5. **重試策略**：在不穩定網路環境中使用 `fetchChipDataWithRetry`

## 🔗 API 端點

FinMind API 的構成格式：
```
https://api.finmindtrade.com/api/v4/data
?dataset=TaiwanStockInstitutionalInvestorsBuySell
&data_id={股票代號}
&start_date={開始日期}
&token={API Token}
```

## 📚 相關資源

- [FinMind API 文檔](https://finmind.github.io/)
- [Spring RestTemplate 文檔](https://spring.io/projects/spring-framework)
- [台灣股票代號查詢](https://www.twse.com.tw/)

---

**版本：1.0**  
**最後更新：2026-05-19**

