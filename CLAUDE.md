# CLAUDE.md — StockPredictor

此檔案在 Claude Code 啟動時自動載入，作為本專案的常駐指引。

## 語言設定

- **一律使用繁體中文（zh-TW）回應**，包含說明、規劃、錯誤分析、commit 訊息草稿、PR 描述。
- 維持完整的中文字符與標點正確性，不要把中文字轉成拼音或同音字。
- 以下內容**保持原文**，不要翻譯：
  - 程式碼識別字（變數、方法、類別、套件名稱）
  - 檔案路徑、URL

## 行為準則

- **誠實原則**：針對任何不確定的資訊、未知的功能或無法確認的問題，**一律回答「不知道」**，並明確表示需要進一步調查，絕對不可進行猜測或虛構資訊。、套件版本
  - 既有的英文程式碼註解（除非使用者要求翻譯）
  - 設定檔的 key（例如 `spring.datasource.url`）
  - Git 指令、Maven 指令、Shell 指令本身
- 新寫的程式碼註解：若該檔案既有註解風格是中文則用中文，是英文則用英文，**不要混用**。
- 技術術語沿用業界常見譯名；無公認譯名時保留英文（例如 `Repository`、`DTO`、`HikariCP`）。

## 專案架構概況

- **類型**: Spring Boot 3.x + H2 嵌入式資料庫的台股分析工具
- **架構**: Java 21, Maven, Spring Data JPA / Hibernate
- **API**: RESTful, Spring MVC, Spring Security + JWT
- **測試**: JUnit 5 + Mockito
- **主套件**: `org.gtalent`
- **進入點**: `src/main/java/org/gtalent/Main.java`
- **資料來源**: FinMind API, TWSE

## 核心模組與約定

### 核心服務
- `ScannerService` / `RadarService`: 選股與雷達評分
- `ScoreEngine` / `StrategyScorer`: 多面向評分 (趨勢、動能、籌碼、波動、市場廣度、基本面)
- `BacktestEngine` / `KellyCalculator`: 回測與凱利公式倉位
- `DatabaseSchemaInitializer` / Repository: Flyway schema 管理與資料存取 (不走 Hibernate ddl-auto)

### 開發約定
- **封裝**: Controller (Web) / Service (Business Logic) / Repository (Data Access) / DTO / Model / Config
- **命名**: CamelCase class, lowerCamelCase method, UPPER_SNAKE_CASE 常數
- **DTO**: DTO 與 Entity 分離，使用手動 mapper 轉換
- **Exception**: 統一使用 `@ControllerAdvice` + 自訂 RuntimeException，回應格式統一為 `ApiResponse`
- **注入**: **禁止使用 Field Injection**，一律使用 Constructor Injection
- **交易**: 寫入操作方法需加 `@Transactional`
- **查詢**: 複雜查詢優先使用 `@Query`，避免 Repository 層寫複雜 SQL

## 工作守則與注意事項

- **設定**: 編輯 `application.properties` 權重時，確認對應的讀取點 (`@Value` in AppConfig 或相關 Service)。
- **評分**: 修改評分邏輯前，查看 `StrategyScorer` / `ScoreEngine` 既有規則，避免重複實作。
- **資料**: 動到 FinMind API 時，注意速率限制與 token 認證流程 (`FinMindClient`)。
- **錯誤處理**: 不要 catch Exception 後直接 swallow，一律向上拋或記 log。
- **查詢優化**: 複雜查詢盡量使用 QueryDSL 或 `@Query`，不建議直接在 Repository 層寫過於複雜的原生 SQL。
- **數據遷移**: H2 僅用於開發/測試，目標遷移至 PostgreSQL。
