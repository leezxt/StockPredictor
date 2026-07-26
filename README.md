# StockPredictor — 台股分析與市場掃描系統

StockPredictor 是以 Java 17 與 Spring Boot 建置的台股研究與決策支援系統。它將歷史行情、技術指標、法人籌碼、基本面、ETF 折溢價與市場廣度整合到同一套資料流程，提供個股診斷、雷達評分、策略訊號、批次掃描、背景回補及瀏覽器儀表板。

專案不是單純的價格查詢頁面：後端會將外部市場資料標準化並保存到資料庫，再由 service 層計算 KD、價量、籌碼與策略條件，最後透過 REST API 提供給各分析頁面。資料來源暫時不可用時，核心路徑具備輸入檢查、降級處理與一致的 API 錯誤格式。

目前正式執行環境使用 PostgreSQL，預設 Docker Compose 會同時啟動應用程式與資料庫；H2 僅保留作為本機相容與 rollback 選項。

## 主要功能

- 個股歷史行情、K 線、KD、價量與技術訊號分析
- 法人買賣、當沖、月營收與市場廣度資料
- 個股診斷、雷達評分、策略分析與批次市場掃描
- ETF 折溢價與跨市場分析
- 歷史資料回補、背景任務進度與掃描紀錄
- Dashboard、即時監控及多個瀏覽器分析頁面
- 統一 API 錯誤格式與資料庫 repository 分層

## 技術架構

- Backend：Java 17、Spring Boot 3.3、REST API
- Database：PostgreSQL 17、Flyway、HikariCP
- Frontend：HTML、CSS、JavaScript
- Data sources：FinMind、TWSE／TPEx、Yahoo Finance
- Build & Test：Maven、JUnit 5、Testcontainers
- Deployment：Docker、Docker Compose

資料流程：

```text
FinMind / TWSE / TPEx / Yahoo Finance
                  │
                  ▼
       Client、排程與資料回補服務
                  │
                  ▼
      Repository + PostgreSQL + Flyway
                  │
                  ▼
   技術指標、診斷、評分與掃描 Services
                  │
                  ▼
         REST Controllers / Web UI
```

## 快速啟動

需求：

- Docker Desktop
- Docker Compose

在專案根目錄建立 `.env.postgresql.local`：

```dotenv
POSTGRES_PASSWORD=請替換成安全密碼
FINMIND_API_TOKEN=
```

此檔案已被 `.gitignore` 排除，不會提交到版本控制。

建置並啟動完整 stack：

```powershell
docker compose up -d --build
```

啟動後：

- Web UI：[http://localhost:19090](http://localhost:19090)
- PostgreSQL：`localhost:5432`
- Database：`stockpredictor_cutover`
- Username：`stockpredictor`

確認服務狀態：

```powershell
docker compose ps
```

停止服務：

```powershell
docker compose down
```

`docker compose down` 會保留 PostgreSQL named volume。除非確定要永久刪除資料，否則不要使用 `docker compose down --volumes`。

## 本機開發與測試

需要 Java 17、Maven 與可用的 PostgreSQL。預設 PostgreSQL profile 會連線到 `localhost:5432`：

```powershell
$env:SPRING_PROFILES_ACTIVE="postgresql"
$env:POSTGRES_PASSWORD="你的本機密碼"
mvn spring-boot:run
```

執行完整測試：

```powershell
mvn test
```

PostgreSQL repository 整合測試使用 Testcontainers，需要 Docker daemon。

## Compose 設定

- `compose.yaml`：預設 PostgreSQL app + database stack
- `compose.postgresql.yaml`：相容既有 PostgreSQL 切換流程
- `compose.h2.yaml`：H2 rollback 用途，預設關閉排程並使用 `19091`

正式切換與資料庫操作請參考：

- [PostgreSQL 操作說明](POSTGRESQL_SETUP.md)
- [PostgreSQL 切換與回復清單](POSTGRESQL_CUTOVER_CHECKLIST.md)
- [專案技術概覽](PROJECT_OVERVIEW.md)

## 專案結構

```text
src/main/java/org/gtalent/       Spring Boot API、service 與 repository
src/main/resources/db/           H2／PostgreSQL Flyway migrations
src/main/resources/static/       Web UI 與前端 JavaScript
src/test/java/org/gtalent/       單元及 PostgreSQL integration tests
packaging-assets/                Docker entrypoint 與映像資產
```

## 安全與部署注意事項

- 不要提交 `.env.postgresql.local`、API token、資料庫密碼或備份檔。
- 系統目前沒有登入與權限管理；部署到公開網路前應加入身分驗證、授權、HTTPS 與網路存取限制。
- PostgreSQL volume 與 `pg_dump` 應納入定期備份與還原演練。
- FinMind token 應透過 `FINMIND_API_TOKEN` 環境變數提供。

---

# English

## Overview

StockPredictor is a Taiwan equity research and decision-support system built with Java 17 and Spring Boot. It combines historical market prices, technical indicators, institutional trading activity, fundamentals, ETF premium/discount data, and market breadth in one data pipeline. The application provides stock diagnostics, radar scoring, strategy signals, batch scanning, background backfills, REST APIs, and browser-based dashboards.

It is more than a price lookup interface. External market data is validated, normalized, and persisted before the service layer calculates momentum, price-volume, institutional, and strategy signals. Controllers expose those results to focused analysis pages. Core boundaries include input validation, controlled fallback behavior, and a consistent API error model when an upstream provider is unavailable.

PostgreSQL is the primary runtime database. The default Docker Compose configuration starts both the application and PostgreSQL, while H2 remains available only for local compatibility and controlled rollback.

## Key Features

- Historical stock prices, candlestick charts, KD momentum, volume, and technical signals
- Institutional trading, day trading, monthly revenue, fundamentals, and market breadth
- Stock diagnostics, radar scoring, strategy analysis, and batch market scanning
- ETF premium/discount and cross-market analysis
- Historical data backfills, asynchronous task progress, and persisted scan history
- Main dashboard, real-time dashboard, and specialized browser analysis pages
- Repository-based persistence and a unified REST API error contract

## Architecture

- Backend: Java 17, Spring Boot 3.3, REST APIs
- Database: PostgreSQL 17, Flyway migrations, and HikariCP
- Frontend: static HTML, CSS, and JavaScript
- Data sources: FinMind, TWSE/TPEx, and Yahoo Finance
- Build and test: Maven, JUnit 5, and Testcontainers
- Deployment: Docker and Docker Compose

```text
FinMind / TWSE / TPEx / Yahoo Finance
                  |
                  v
       Clients, schedulers, and backfills
                  |
                  v
      Repositories + PostgreSQL + Flyway
                  |
                  v
 Indicators, diagnostics, scoring, and scans
                  |
                  v
         REST controllers and Web UI
```

## Quick Start

Requirements:

- Docker Desktop
- Docker Compose

Create `.env.postgresql.local` in the project root:

```dotenv
POSTGRES_PASSWORD=replace-with-a-secure-password
FINMIND_API_TOKEN=
```

The file is excluded by `.gitignore` and must not be committed.

Build and start the complete stack:

```powershell
docker compose up -d --build
```

Services:

- Web UI: [http://localhost:19090](http://localhost:19090)
- PostgreSQL: `localhost:5432`
- Database: `stockpredictor_cutover`
- Username: `stockpredictor`

Inspect or stop the stack:

```powershell
docker compose ps
docker compose down
```

`docker compose down` preserves the PostgreSQL named volume. Do not add `--volumes` unless permanent database deletion is intended.

## Local Development and Testing

Java 17, Maven, and a reachable PostgreSQL instance are required. The PostgreSQL profile defaults to `localhost:5432`:

```powershell
$env:SPRING_PROFILES_ACTIVE="postgresql"
$env:POSTGRES_PASSWORD="your-local-password"
mvn spring-boot:run
```

Run the complete test suite:

```powershell
mvn test
```

PostgreSQL repository integration tests use Testcontainers and require a running Docker daemon.

## Compose Files

- `compose.yaml`: default application and PostgreSQL stack
- `compose.postgresql.yaml`: compatibility entry point for the existing PostgreSQL cutover workflow
- `compose.h2.yaml`: rollback-only H2 stack on port `19091`, with scheduling disabled

Additional documentation:

- [PostgreSQL setup](POSTGRESQL_SETUP.md)
- [PostgreSQL cutover and rollback checklist](POSTGRESQL_CUTOVER_CHECKLIST.md)
- [Project technology overview](PROJECT_OVERVIEW.md)

## Project Layout

```text
src/main/java/org/gtalent/       Spring Boot APIs, services, and repositories
src/main/resources/db/           H2 and PostgreSQL Flyway migrations
src/main/resources/static/       Web interfaces and browser-side JavaScript
src/test/java/org/gtalent/       Unit and PostgreSQL integration tests
packaging-assets/                Docker entrypoint and image assets
```

## Security and Deployment Notes

- Never commit `.env.postgresql.local`, API tokens, database passwords, or database backups.
- Authentication and authorization are not implemented yet. Add identity controls, HTTPS, and network restrictions before exposing the application publicly.
- Include the PostgreSQL volume and `pg_dump` archives in a tested backup and restore process.
- Supply FinMind credentials through the `FINMIND_API_TOKEN` environment variable.
