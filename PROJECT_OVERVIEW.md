# StockPredictor Project Overview / 專案概覽

## 中文

StockPredictor 是一個台股分析工具，使用 Spring Boot 後端、H2 內嵌資料庫與靜態 HTML 前端。

### 使用語言與技術

- Java 17：後端服務、掃描邏輯、技術指標、資料快取與 REST API。
- HTML / CSS / JavaScript：主頁、K 線、KD、價量與分析儀表板。
- SQL / H2：本機資料儲存，schema 由 `DatabaseManager` 手動建立。
- Maven：建置、測試與 Spring Boot 可執行 JAR 打包。
- Docker / Compose：跨平台部署，包含 amd64 與 arm64 映像交付。

### 專案結構

- `src/main/java/org/gtalent`：主要 Java 程式碼。
- `src/main/resources/static`：前端靜態頁面與瀏覽器端 JavaScript。
- `src/main/resources/application.properties`：應用程式、資料庫、FinMind 與評分參數。
- `src/test/java`：JUnit 測試。
- `packaging-assets`：Docker 映像啟動與內建資料庫 seed。
- `dist`：離線部署包、Compose 與啟動腳本。

### 資安檢查結果

- 已將 REST API CORS 從 `*` 收斂到本機來源 `localhost` / `127.0.0.1`。
- 已將 H2 Console 改為預設關閉；需要時可用 `H2_CONSOLE_ENABLED=true` 啟用。
- FinMind token 透過環境變數 `FINMIND_API_TOKEN` 注入，未在設定檔放入實際 token。
- SQL 查詢主要使用 `PreparedStatement`，降低 SQL injection 風險。

### 已知限制

- 目前沒有登入與權限系統；若要部署到公開網路，應先加入認證、授權與 HTTPS。
- Docker volume 會保存 H2 資料庫；分享部署包前應確認 seed 不含敏感個人資料。

## English

StockPredictor is a Taiwan stock analysis tool built with a Spring Boot backend, an embedded H2 database, and static HTML frontends.

### Languages and Technology

- Java 17: backend services, scanning logic, technical indicators, data caching, and REST APIs.
- HTML / CSS / JavaScript: dashboard, candlestick chart, KD analysis, price-volume views, and analysis pages.
- SQL / H2: local persistence, with schema managed manually by `DatabaseManager`.
- Maven: build, test, and Spring Boot executable JAR packaging.
- Docker / Compose: cross-platform deployment with amd64 and arm64 image artifacts.

### Project Structure

- `src/main/java/org/gtalent`: main Java source code.
- `src/main/resources/static`: static frontend pages and browser-side JavaScript.
- `src/main/resources/application.properties`: application, database, FinMind, and scoring configuration.
- `src/test/java`: JUnit tests.
- `packaging-assets`: Docker entrypoint and bundled database seed.
- `dist`: offline deployment bundle, Compose files, and startup scripts.

### Security Review

- REST API CORS was restricted from `*` to local origins: `localhost` / `127.0.0.1`.
- H2 Console is now disabled by default; enable it only when needed with `H2_CONSOLE_ENABLED=true`.
- FinMind tokens are provided through the `FINMIND_API_TOKEN` environment variable, not hard-coded in config.
- SQL access mainly uses `PreparedStatement`, reducing SQL injection risk.

### Known Limits

- There is no authentication or authorization layer yet; add auth and HTTPS before exposing the app to the public internet.
- Docker volumes persist the H2 database; verify the bundled seed before sharing release artifacts.
