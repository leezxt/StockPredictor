# StockPredictor / 台股分析系統

## 中文

StockPredictor 是一個台股分析工具，提供股價走勢、K 線、KD 動能、法人籌碼、雷達評分與市場掃描功能。

- 後端：Java 17、Spring Boot 3、REST API
- 前端：HTML、CSS、JavaScript 靜態頁面
- 資料庫：H2 embedded database，schema 由 Flyway／`DatabaseSchemaInitializer` 管理
- 建置：Maven
- 部署：Docker / Docker Compose，支援 `linux/amd64` 與 `linux/arm64`

更多語言、技術結構與資安檢查請見 [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md)。

## English

StockPredictor is a Taiwan stock analysis tool with price charts, candlestick views, KD momentum analysis, institutional trading data, radar scoring, and market scanning.

- Backend: Java 17, Spring Boot 3, REST APIs
- Frontend: static HTML, CSS, and JavaScript
- Database: H2 embedded database, with schema managed by Flyway and `DatabaseSchemaInitializer`
- Build: Maven
- Deployment: Docker / Docker Compose, supporting `linux/amd64` and `linux/arm64`

See [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md) for the full bilingual technology structure and security review.
