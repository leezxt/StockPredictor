# MarketCrossScannerService ETF 折溢價整合指南

**原始版本**：v1.0（2026-05-28）

**現況校準**：2026-07-26

**狀態**：production 流程已整合；歷史 helper 保留相容性

## 現況摘要

2026-05 的原始交付以 `calculateEtfMetrics()` 與 `RadarConfigDto` 說明 ETF NAV 與折溢價計算。現行 production 跨市場診斷已改由 `CrossDiagnosisController` 呼叫 `MarketCrossScannerService.crossTestBySymbol()`，回傳 `StockDiagnosticResult`。

`calculateEtfMetrics()` 仍是 public helper，已使用動態 45 天查詢區間並防護無效 NAV，但目前沒有 production Java 呼叫端。請勿再以舊文件中的假想 `StockController` endpoint 當作現行 API。

## Production 入口與資料流

現行 REST endpoints：

- `GET /api/cross/{symbol}`：單檔診斷。
- `POST /api/cross/batch`：批次診斷，最多 50 個非空白代號。

```text
CrossDiagnosisController
  → MarketCrossScannerService.crossTestBySymbol(symbol)
  → StockUniverseRepository 判斷 ETF
  → StockDataRepository 取得最新價格與一年歷史
  → buildPriceInfo(symbol, true)
      1. FinMindClient.fetchEtfDiscountPremium(symbol)
         - TWSE／TPEx 公開 NAV 類來源優先
         - FinMind 折溢價 datasets 備援
      2. 若仍不可用，calculatePremiumRatioFromNav()
         - fetchNavData(symbol, LocalDate.now().minusDays(45))
         - 以最新有效正值 NAV 與市價自行計算
      3. 全部不可用時標記 NOT_AVAILABLE
  → analyzeEtfStrategy()
  → StockDiagnosticResult
```

production 流程內的折溢價比率使用 decimal：

```text
discountPremiumRatio = (市價 - NAV) / NAV
```

例如 `-0.012` 表示折價 `1.2%`。寫入 `StockDiagnosticResult.premium` 時才乘以 100，轉成百分比數值。

## 資料來源優先序

| 優先序 | 來源 | 成功時的 source |
|---|---|---|
| 1 | TWSE／TPEx NAV 類公開來源 | `TWSE_TPEX_PRIMARY` |
| 2 | FinMind 折溢價 datasets | `FINMIND_FALLBACK` |
| 3 | `TaiwanETFNavigation` 的 NAV 與本機最新市價自行計算 | `FinMind_NAV` |
| 4 | 無可用資料 | `NOT_AVAILABLE` |

`FinMindClient.fetchNavData(symbol, startDate)` 會：

- 查詢 `TaiwanETFNavigation`。
- 直接由 raw JSON `data` 陣列反序列化成 `FinMindNavData`。
- 依日期升序回傳。
- 對空白代號、空白日期、HTTP 錯誤與不可用回應降級為空清單。

## ETF 策略規則

ETF 不使用個股的三率三升判定。現行 `analyzeEtfStrategy()` 主要依以下條件分類：

1. K 值低於 25 且出現 KD 黃金交叉：標記「ETF 點心時間」。
2. 折溢價 decimal 小於 `-0.01`：標記「ETF 撿便宜」。
3. 其他情況：標記等待更佳折價或技術訊號。

最終分數由折溢價分數、KD 強度及融資分數組成。NAV 不可用時會保留 `navAvailable=false` 與來源狀態，不把 `0` 誤認為真實折溢價資料。

## 歷史相容 helper

原始交付的 public 方法仍保留：

```java
public RadarConfigDto calculateEtfMetrics(String symbol, double currentPrice)
```

其行為為：

1. 查詢最近 45 天的 NAV。
2. 從最新資料往回尋找第一筆大於 0 的 NAV。
3. 計算百分比：

   ```text
   premium (%) = ((市價 - NAV) / NAV) × 100
   ```

4. 成功時填入 `RadarConfigDto.netAssetValue`、`premium` 與來源。
5. 無資料或 NAV 全部無效時回傳 `0`，並將來源設為 `NOT_AVAILABLE`。

此 helper 與 production 的 NAV fallback 有部分重複。未完成公開相容性評估前先保留，不新增專屬 endpoint。

## 依賴注入

`MarketCrossScannerService` 與 `CrossDiagnosisController` 均使用 constructor injection。歷史文件中的 `@Autowired` field injection 範例已移除。

需要的核心依賴包括：

- `FinMindClient`
- `StockUniverseRepository`
- `StockDataRepository`
- `IndicatorCalculator`
- 基本面、融資與 KD 分析服務

## 設定

`application.properties` 的現行 FinMind 設定：

```properties
finmind.api.token=${FINMIND_API_TOKEN:}
finmind.api.url=https://api.finmindtrade.com/api/v4/data
finmind.api.login.url=https://api.finmindtrade.com/api/v4/login
```

Token 應由環境變數 `FINMIND_API_TOKEN` 提供，不要寫入 repository。登入帳密與自動重新登入屬選配；未設定時仍可依公開來源與可用 API 權限降級。

## 防禦與失效情境

| 場景 | 現行處理 |
|---|---|
| symbol 空白 | client 不發請求；Controller／service 依入口規則拒絕或標記 skip |
| primary 折溢價來源失敗 | 依序嘗試 FinMind dataset 與 NAV 自算 |
| NAV 清單為空 | 標記 `NOT_AVAILABLE` |
| 最新 NAV 為 0 或負值 | 往回尋找最新正值；全部無效才降級 |
| current price 小於等於 0 | NAV 自算 fallback 不計算比率 |
| FinMind HTTP／JSON 錯誤 | 記錄必要資訊並回傳不可用結果 |

NAV 與市價可能不是同一交易時點，因此自行計算的 `FinMind_NAV` 僅是 fallback 推估，不應視為盤中即時官方折溢價。

## 驗證證據

截至 2026-07-26：

- `FinMindClientNavDataTest`：3 項，涵蓋 URL contract、JSON 映射、排序、空白參數與 HTTP 降級。
- `MarketCrossScannerServiceTest`：10 項，涵蓋 ETF metadata、Repository 價格區間、primary 優先、NAV fallback、來源全失敗、NAV unavailable 中性化、折價門檻、KD 低檔黃金交叉、動態 NAV 日期、最新有效 NAV 與公式。
- 完整 `mvn test`：182 項，0 failures、0 errors、0 skipped。
- `PostgresqlRepositoryIntegrationTest` 7 項已透過 Docker Testcontainers 實際執行成功。

## 後續整理

- 評估 `calculateEtfMetrics()` 是否屬外部相容 contract；若否，可與 `calculatePremiumRatioFromNav()` 整併以消除重複。
- 若要提供獨立 ETF metrics REST API，應先定義回應 contract、資料時點與來源語意，再由現有 constructor-injected Controller 接線。
- PostgreSQL 正式切換仍需依 `POSTGRESQL_CUTOVER_CHECKLIST.md` 安排維護時段，不屬本文件校準範圍。
