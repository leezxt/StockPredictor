# PostgreSQL 本機開發環境

PostgreSQL baseline、Repository upsert、資料搬遷與 Testcontainers 整合測試均已完成。
正式服務已於 2026-07-26 切換至 PostgreSQL；原 H2 僅保留作 rollback。

## 啟動資料庫

```powershell
docker compose up -d --build
```

預設連線資訊：

- Host：`localhost`
- Port：`5432`
- Database：`stockpredictor_cutover`
- Username：`stockpredictor`
- Password：由被 `.gitignore` 排除的 `.env.postgresql.local` 提供

Compose 會同時啟動 `database` 與 `app`，app 對外使用 `19090`。正式密碼不應寫入版本控制。

## 啟用 Spring profile

```powershell
$env:SPRING_PROFILES_ACTIVE="postgresql"
$env:POSTGRES_PASSWORD="stockpredictor-local"
mvn spring-boot:run
```

啟動時 Flyway 會讀取 `classpath:db/migration/postgresql` 並建立 baseline schema。

## 執行 PostgreSQL 整合測試

Docker Desktop 啟動後執行：

```powershell
mvn -Dtest=PostgresqlRepositoryIntegrationTest test
```

測試會建立一次性的 PostgreSQL 17 容器，套用 Flyway migration，驗證六個 JDBC Repository，
並在 JVM 結束時自動清除測試容器。Docker 不可用時，這六項測試會標示為 skipped。

若 Windows 的 Testcontainers 被舊設定導向錯誤的 Docker pipe，可只對目前 PowerShell 工作階段指定：

```powershell
$env:DOCKER_HOST="npipe:////./pipe/dockerDesktopLinuxEngine"
mvn -Dtest=PostgresqlRepositoryIntegrationTest test
```

## 搬遷 H2 資料

先確認 PostgreSQL 已啟動且 Flyway baseline 已建立。以下範例的 H2 路徑不含 `.mv.db`：

```powershell
$env:H2_SOURCE_URL="jdbc:h2:file:C:/Users/lee/AppData/Local/StockPredictor/stockdb;ACCESS_MODE_DATA=r"
$env:H2_SOURCE_USER="sa"
$env:H2_SOURCE_PASSWORD=""
$env:POSTGRES_JDBC_URL="jdbc:postgresql://localhost:5432/stockpredictor"
$env:POSTGRES_USER="stockpredictor"
$env:POSTGRES_PASSWORD="stockpredictor-local"
```

先執行 dry-run；預設不會寫入 PostgreSQL：

```powershell
mvn exec:java "-Dexec.mainClass=org.gtalent.H2ToPostgresqlMigrator"
```

確認報告與來源資料合理後，才加入 `--execute`：

```powershell
mvn exec:java "-Dexec.mainClass=org.gtalent.H2ToPostgresqlMigrator" "-Dexec.args=--execute"
```

搬遷工具會：

- 確認來源為 H2、目標為 PostgreSQL，且雙方 10 張必要資料表都存在。
- 在寫入前拒絕 null 或重複 migration key，包含舊 H2 可能重複的 `STOCK_DATA(symbol, trade_date)`。
- 以批次 upsert 搬遷，可安全重跑；預設 batch size 為 500，可用 `MIGRATION_BATCH_SIZE` 調整。
- 在同一 PostgreSQL transaction 內比對 row count、日期範圍、symbol 數量與每張表的固定抽樣列。
- 任一搬遷或驗證失敗即 rollback；來源 H2 不會被修改。

## Shadow run

正式切換前，應以不同連接埠啟動 PostgreSQL shadow 應用，並關閉排程與外部新聞反應：

```powershell
$env:SPRING_PROFILES_ACTIVE="postgresql"
$env:SERVER_PORT="19091"
$env:APP_SCHEDULING_ENABLED="false"
$env:SCANNER_NEWS_REACTION_ENABLED="false"
mvn spring-boot:run
```

可用 `/api/stocks/runtime-info` 確認實際回傳 `database=PostgreSQL` 與 PostgreSQL JDBC URL。
完成 API、背景工作與資料抽樣驗證後停止 shadow 應用；PostgreSQL Compose 使用 `down`
只會停止容器，named volume 仍會保留。

## 停止資料庫

```powershell
docker compose down
```

上述指令不會刪除 named volume。只有明確執行 `down --volumes` 才會刪除 PostgreSQL 資料。

## 正式切換

正式切換前的停機、完整搬遷、API 驗收、備份與 rollback 順序，請依
[`POSTGRESQL_CUTOVER_CHECKLIST.md`](POSTGRESQL_CUTOVER_CHECKLIST.md) 執行。
正式切換紀錄、備份雜湊與 rollback 步驟均記錄於該清單。
