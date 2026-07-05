# 全市場 Q1 黑馬股掃描控制器 - 使用指南

**完成日期**: 2026-05-19  
**功能**: 一鍵自動掃描全市場，識別符合三率三升條件的黑馬股  
**狀態**: ✅ 編譯通過

---

## 📋 快速概覽

### 核心設計

```
Web 前端按鈕
    ↓
StrategyController.scanQ1BlackHorses()
    ↓
MarketService.getAllSymbols() ← 取得全市場股票清單
    ↓
Loop 2000+ 檔股票：
    ├─ FinMindClient.fetchFinancialStatements() ← API 呼叫
    ├─ AdvancedFundamentalService.checkTripleRiseScore() ← 判定引擎
    └─ if (score > 0) → 加入黑馬股清單
    ↓
返回結果 JSON（含統計與股票名單）
```

---

## 🎯 API 端點

### 1️⃣ **全市場掃描**

```
GET /api/strategy/q1-black-horse
```

**功能**: 掃描全市場所有股票，自動識別三率三升黑馬股

**響應範例**:

```json
{
  "status": "success",
  "totalScanned": 1520,
  "tripleRoseFound": 47,
  "errorCount": 3,
  "scanDurationMs": 312000,
  "successRate": "99.8%",
  "blackHorseList": [
    "2330",
    "2454",
    "3008",
    "6415",
    "1101",
    "2883",
    ...
  ],
  "message": "✅ 掃描完成！共掃描 1520 檔，發現 47 隻符合三率三升條件的黑馬股（耗時 312.0 秒）"
}
```

**耗時說明**:

```
掃描說明：
  - 全市場股票: ~1500-1600 檔
  - 每檔限流: 200ms (避免 API 被阻擋)
  - 估計耗時: 300-350 秒 (~5-6 分鐘)
  - 適合: 早上冷靜時執行，或夜間定時任務
```

**HTTP 狀態碼**:

| 碼 | 含義 |
|---|------|
| 200 | 掃描完成（成功或部分成功） |
| 500 | 掃描任務異常 |

---

### 2️⃣ **自訂掃描**

```
GET /api/strategy/q1-black-horse-custom?symbols=2330,2454,3008,6415
```

**功能**: 掃描指定的股票代號（快速驗證或自訂篩選）

**參數**:

- `symbols` (必需): 逗號分隔的股票代號
  - 格式: `2330,2454,3008`
  - 最多建議: 50 檔（避免超時）

**響應範例**:

```json
{
  "status": "success",
  "totalScanned": 4,
  "tripleRoseFound": 2,
  "errorCount": 0,
  "scanDurationMs": 1200,
  "successRate": "100.0%",
  "blackHorseList": [
    "2330",
    "2454"
  ],
  "message": "✅ 掃描完成！共掃描 4 檔，發現 2 隻符合三率三升條件的黑馬股（耗時 1.2 秒）"
}
```

---

## 📝 使用示例

### 方式 1: cURL 命令行

```bash
# 全市場掃描
curl "http://localhost:8080/api/strategy/q1-black-horse"

# 自訂掃描
curl "http://localhost:8080/api/strategy/q1-black-horse-custom?symbols=2330,2454,3008,6415"
```

### 方式 2: 前端 HTML 按鈕

```html
<!-- 全市場掃描按鈕 -->
<button onclick="scanFullMarket()">🔥 掃描全市場黑馬股</button>

<script>
function scanFullMarket() {
    const btn = event.target;
    btn.disabled = true;
    btn.textContent = "⏳ 掃描中...";
    
    fetch('/api/strategy/q1-black-horse')
        .then(r => r.json())
        .then(data => {
            if (data.status === 'success') {
                alert(`✅ 發現 ${data.tripleRoseFound} 隻黑馬股\n${data.blackHorseList.join(', ')}`);
                // 或顯示在表格中
                displayResults(data);
            } else {
                alert(`❌ 掃描失敗: ${data.message}`);
            }
        })
        .catch(err => alert(`❌ 網路錯誤: ${err}`))
        .finally(() => {
            btn.disabled = false;
            btn.textContent = "🔥 掃描全市場黑馬股";
        });
}

function displayResults(data) {
    const table = document.getElementById('results');
    table.innerHTML = `
        <tr>
            <td>掃描總數</td>
            <td>${data.totalScanned}</td>
        </tr>
        <tr>
            <td>黑馬股數</td>
            <td>${data.tripleRoseFound}</td>
        </tr>
        <tr>
            <td>成功率</td>
            <td>${data.successRate}</td>
        </tr>
        <tr>
            <td>耗時</td>
            <td>${(data.scanDurationMs / 1000).toFixed(1)}s</td>
        </tr>
        <tr>
            <td>名單</td>
            <td>${data.blackHorseList.join(', ')}</td>
        </tr>
    `;
}
</script>

<!-- 自訂掃描 -->
<input type="text" id="customSymbols" placeholder="輸入股票代號 (逗號分隔)">
<button onclick="scanCustom()">🔍 掃描指定股票</button>

<script>
function scanCustom() {
    const symbols = document.getElementById('customSymbols').value;
    fetch(`/api/strategy/q1-black-horse-custom?symbols=${symbols}`)
        .then(r => r.json())
        .then(data => {
            if (data.status === 'success') {
                alert(`✅ 發現 ${data.tripleRoseFound} 隻黑馬股`);
                displayResults(data);
            }
        });
}
</script>
```

### 方式 3: Java 客戶端

```java
@RestTemplate
RestTemplate restTemplate = new RestTemplate();

// 全市場掃描
String url = "http://localhost:8080/api/strategy/q1-black-horse";
ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
Map<String, Object> result = response.getBody();

System.out.println("發現黑馬股: " + result.get("blackHorseList"));
System.out.println("耗時: " + result.get("scanDurationMs") + "ms");
```

---

## 🔧 組件說明

### StrategyController

**職責**:
1. 提供 REST API 端點
2. 協調掃描流程
3. 處理限流與異常
4. 構建響應 JSON

**核心方法**:

```java
@GetMapping("/q1-black-horse")
public ResponseEntity<?> scanQ1BlackHorses()
  └─ 全市場掃描，回傳黑馬股清單

@GetMapping("/q1-black-horse-custom")
public ResponseEntity<?> scanCustomSymbols(String symbols)
  └─ 自訂掃描，回傳指定股票清單中的黑馬股
```

**限流設定**:

```java
private static final long API_RATE_LIMIT_MS = 200;  // 200ms/次
```

### MarketService

**職責**:
1. 管理全市場股票代碼清單
2. 本地快取與 API 更新
3. 股票代碼驗證與篩選

**核心方法**:

```java
public List<String> getAllSymbols()
  └─ 取得全市場股票代號（優先快取，不足時更新）

public List<String> getSymbolsByType(String type)
  └─ 按類型篩選（STOCK/ETF/ALL） [未實裝]

public boolean isValidSymbol(String symbol)
  └─ 驗證股票代號合法性
```

**資料來源**:

1. 📀 本地快取 (MARKET_SYMBOLS 表)
2. 🌐 外部 API (FinMind / TWSE / TPEX)
3. 🛡️ 硬編碼備用 (演示用清單)

---

## 📊 性能指標

### 掃描耗時預測

| 掃描數量 | 預估耗時 | 成本 |
|-------|-------|------|
| 10 檔（自訂） | ~2 秒 | 低 |
| 100 檔 | ~20 秒 | 低 |
| 1000 檔 | ~200 秒 (~3 分) | 中 |
| 全市場 (~1500 檔) | ~300 秒 (~5 分) | 中-高 |

**關鍵參數**:

```
限流間隔: 200ms/次 (FinMind 免費版建議)
平均 API 回應: 50-100ms
平均判定耗時: 5-10ms
天際線: 約 300ms/檔
```

### 資源消耗

| 資源 | 消耗 | 備註 |
|-----|------|------|
| CPU | 中 | 主要用於 JSON 解析 |
| 記憶 | 低-中 | ~50-100MB (全變數) |
| 網路 | 高 | ~1500 次 API 呼叫 |
| DB | 低 | 僅快取讀 |

---

## ⚠️ 限制與注意事項

### API 限制

1. **FinMind 免費版**
   - 限頻: 建議 200-500ms 間隔
   - 超過會被暫時阻擋
   - 建議在非交易時段執行

2. **連線逾時**
   - 默認逾時: 30 秒 (需 Spring 配置)
   - 建議增加至 60 秒

3. **錯誤處理**
   - 單檔失敗不影響其他檔
   - errorCount 計數可追蹤失敗率

### 最佳實踐

✅ **DO**:
- 在交易時段外執行全市場掃描
- 使用自訂掃描驗證小規模測試
- 監控日誌中的成功/失敗率
- 定期對比結果發現異常

❌ **DON'T**:
- 不要在高市場活動時掃描全市場
- 不要嘗試無限制降低限流間隔
- 不要併行多個全市場掃描任務

---

## 🧪 測試場景

### 測試 1: 快速驗證

```bash
# 測試 5 檔股票
curl "http://localhost:8080/api/strategy/q1-black-horse-custom?symbols=2330,2454,3008,6415,1101"

# 預期: 快速返回 (~1-2 秒)，顯示符合條件的股票
```

### 測試 2: 容錯能力

```bash
# 包含不存在的股票代號
curl "http://localhost:8080/api/strategy/q1-black-horse-custom?symbols=2330,XXXX,2454"

# 預期: 顯示 errorCount = 1，其他正常掃描
```

### 測試 3: 全市場掃描

```bash
# 完整掃描 (耗時 5-10 分)
curl "http://localhost:8080/api/strategy/q1-black-horse" -w "\n耗時: %{time_total}s\n"

# 監控進度
tail -f app.log | grep "符合三率三升"
```

---

## 📈 監控與日誌

### 日誌關鍵詞

```
✅ [N/1500]  symbol 符合三率三升
⚠️  掃描 XX 失敗
🔥 偵測到三率三升黑馬股
✅ 掃描統計: ...
```

### 監控指標

```properties
# application.properties
logging.level.org.gtalent.StrategyController=INFO
logging.level.org.gtalent.MarketService=INFO
logging.level.org.gtalent.AdvancedFundamentalService=FINE
```

### 常見問題排查

| 症狀 | 原因 | 解決 |
|-----|-----|------|
| 掃描很慢 | 限流設定過小 | 增加 API_RATE_LIMIT_MS |
| 連線逾時 | API 回應慢 | 檢查 FinMind 狀態 |
| errorCount 高 | API 頻率限制 | 增加限流間隔 |
| 結果為空 | 無符合條件 | 檢查財報資料是否齊全 |

---

## 🚀 部署建議

### 開發環境

```bash
# 本機執行 (5-10 分鐘)
mvn spring-boot:run

# 監控日誌
tail -f target/app.log
```

### 生產環境

```yaml
# application-prod.yml
server:
  tomcat:
    threads:
      max: 10  # 限制並行請求
      
spring:
  web:
    resources:
      cache:
        period: 3600  # 快取響應

# API 限制配置
finmind:
  rate-limit-ms: 250  # 可根據 API 配額調整
```

### 定時任務

```java
@Configuration
@EnableScheduling
public class ScanningTasks {
    
    @Autowired
    private StrategyController controller;
    
    @Scheduled(cron = "0 6 * * MON")  // 每週一 6:00 AM
    public void weeklyBlackHorseScan() {
        logger.info("🤖 自動執行每週黑馬股掃描");
        controller.scanQ1BlackHorses();
    }
}
```

---

## 📊 響應字段說明

| 字段 | 類型 | 說明 |
|-----|-----|------|
| status | String | "success" 或 "error" |
| totalScanned | int | 掃描的股票總數 |
| tripleRoseFound | int | 符合三率三升的股票數 |
| errorCount | int | 掃描失敗的股票數 |
| scanDurationMs | long | 掃描耗時 (毫秒) |
| successRate | String | 成功率百分比 |
| blackHorseList | List<String> | 黑馬股代號清單 |
| message | String | 可讀的摘要訊息 |
| timestamp | long | 響應時間戳 (僅錯誤時) |

---

## 💡 進階用途

### 1. 與前端儀表板整合

```html
<div id="dashboard">
    <h2>Q1 黑馬股掃描結果</h2>
    <button onclick="startScan()">🚀 開始掃描</button>
    
    <div id="progress">
        加載中... <span id="count">0</span> / <span id="total">0</span>
    </div>
    
    <table id="results">
        <!-- 動態填充 -->
    </table>
</div>
```

### 2. 定期生成報告

```java
@Scheduled(cron = "0 22 * * FRI")  // 每週五 22:00
public void generateWeeklyReport() {
    ResponseEntity<?> result = controller.scanQ1BlackHorses();
    Map data = (Map) result.getBody();
    
    // 存檔、郵件通知等
    EmailService.sendReport(data);
    ReportService.archive(data);
}
```

### 3. 與其他策略結合

```java
// 黑馬股 + 技術面篩選
List<String> blackHorses = scanQ1BlackHorses().getBlackHorseList();

for (String symbol : blackHorses) {
    TechnicalAnalysis ta = technicalService.analyze(symbol);
    if (ta.isGoldenCross() && ta.getVolume() > avgVolume) {
        potentialTrades.add(symbol);  // 二次篩選
    }
}
```

---

## 📞 常見問題

**Q: 掃描全市場需要多久？**  
A: 約 5-6 分鐘（取決於網路和 FinMind 頻率限制）

**Q: 可以降低限流間隔嗎？**  
A: 不建議。FinMind 免費版可能被阻擋。

**Q: 可以並行掃描嗎？**  
A: 可以，但會加重 API 負擔，建議順序執行。

**Q: 如何設定自動掃描？**  
A: 使用 @Scheduled 註解（見「定時任務」章節）

**Q: 結果會改變嗎？**  
A: 會。取決於最新的財報資料發布時間。

---

**版本**: v1.0  
**狀態**: Ready to use  
**最後更新**: 2026-05-19

