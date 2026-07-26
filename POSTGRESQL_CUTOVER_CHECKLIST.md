# PostgreSQL 正式切換與回復清單

## 目前狀態

- 狀態：**2026-07-26 已正式切換 PostgreSQL**。
- `stockpredictor` app 目前使用 `postgresql` profile，正式 JDBC 為 `jdbc:postgresql://database:5432/stockpredictor_cutover`。
- 正式 PostgreSQL 使用獨立 volume `stockpredictor-postgresql-cutover-data`；未沿用 shadow 資料庫。
- 原 H2 容器 `stockpredictor-h2-rollback`、原 named volume 與停機後不可變備份均保留，可供 rollback。

## 已建立的回復資產

| 資產 | 大小 | SHA-256 | 說明 |
|---|---:|---|---|
| `backups/cutover-20260723/stockdb-h2-original-20260723.mv.db` | 13,590,528 bytes | `457F8BDB48BAC63CA4F00C43B080A22D9B1ED29D30ED85B565F70E82E75F7FB5` | H2 原檔的唯讀副本 |
| `backups/cutover-20260723/stockpredictor-shadow-20260723.dump` | 2,674,618 bytes | `77E52E33B261A814809312E22A4184C1FCBA392DEA1EDC83AFBC15C2D9303435` | PostgreSQL 17 custom-format `pg_dump` |
| `backups/cutover-20260723/SHA256SUMS.txt` | — | — | 備份檔雜湊清單 |

H2 正式檔位置：

```text
C:\Users\lee\AppData\Local\StockPredictor\stockdb.mv.db
```

建立備份時，正式 H2 與唯讀副本的 SHA-256 完全相同。

## 切換前置條件

- [x] 排定維護時段，停止所有 StockPredictor Java／Docker 應用。
- [x] 確認 `19090`、`19091` 沒有 StockPredictor 服務寫入資料。
- [x] 確認正式 H2 lock 已解除；不要在 H2 開啟時複製資料庫檔。
- [x] 再複製一次停機後的 H2，記錄檔案大小、時間與 SHA-256。
- [x] PostgreSQL 正式密碼已透過 `POSTGRES_PASSWORD` 提供，不使用本機預設密碼。
- [x] 正式 PostgreSQL 有獨立的持久化 volume、磁碟空間與備份位置。
- [x] `pg_restore --list` 能讀取 PostgreSQL custom-format 備份。

## 正式切換步驟

1. 停止 H2 應用並凍結寫入。
2. 建立停機後 H2 副本與 SHA-256；原始 H2 保持不動。
3. 建立乾淨的 PostgreSQL 正式資料庫，例如 `stockpredictor_cutover`。不要沿用含 shadow 掃描資料的 `stockpredictor`。
4. 使用 `postgresql` profile、停用排程與外部新聞反應，短暫啟動應用，讓 Flyway 建立 schema，然後停止應用。
5. 將 `H2_SOURCE_URL` 指向停機後副本，將 `POSTGRES_JDBC_URL` 指向乾淨目標；先執行 dry-run。
6. dry-run 通過後執行 `H2ToPostgresqlMigrator --execute`。搬遷工具必須完成 row count、日期範圍、symbol 數量與固定抽樣比對後才會 commit。
7. 以正式 `postgresql` profile 啟動 `19090`，先保持 `APP_SCHEDULING_ENABLED=false`。
8. 完成下方驗收；全部通過後才啟用排程。
9. 建立正式切換後的 PostgreSQL `pg_dump`，並保存 H2 停機副本至少一個回復週期。

## 驗收清單

- [x] `GET /api/stocks/runtime-info` 回傳 `database=PostgreSQL`，JDBC URL 指向正式資料庫。
- [x] `/index.html` 回傳 HTTP 200。
- [x] `GET /api/stocks/2330/history?limit=5` 回傳 HTTP 200 且資料合理。
- [x] 完整歷史、法人、當沖與市場廣度 API 均回 HTTP 200。
- [x] 10 張資料表 row count 與停機 H2 搬遷報告一致。
- [x] `STOCK_DATA`、`INSTITUTIONAL_DATA` 等日期最小／最大值與 distinct symbol 數一致。
- [x] 固定抽樣股票（至少 `2330`）的日期、價格、成交量與 H2 相同。
- [x] 執行一個受限背景掃描，狀態為 `COMPLETED` 且結果可讀回。
- [x] 應用 log 沒有 Flyway、SQL、連線池或排程錯誤。
- [ ] 啟用排程後觀察至少一個排程週期，再建立切換後備份。

## Rollback 觸發條件

- 核心 API 持續出現 5xx、SQL 或連線池錯誤。
- 搬遷統計或抽樣資料與 H2 不一致。
- 背景掃描、回補或排程產生重複／遺失資料。
- 無法在維護時段內完成驗收。

## Rollback 步驟

1. 立即停止 PostgreSQL profile 的 StockPredictor 應用，避免繼續寫入。
2. 保留 PostgreSQL volume 與最新 `pg_dump`，不要執行 `down --volumes`。
3. 確認 H2 原檔或停機副本的 SHA-256 與記錄一致。
4. 清除目前工作階段的 `SPRING_PROFILES_ACTIVE=postgresql` 與 `POSTGRES_JDBC_URL`，恢復 H2 JDBC 設定。
5. 若正式 H2 原檔從未被覆寫，直接以原檔啟動；只有原檔損壞時才把已驗證的停機副本複製回原位置。
6. 啟動 H2 應用，重跑相同 API 與 `runtime-info` 驗收；確認資料庫回報為 H2。
7. 記錄切換期間 PostgreSQL 可能新增的資料，之後再決定是否人工補回 H2；不可直接覆蓋任一端。

## 備份還原驗證紀錄

- 正式切換日期：2026-07-26
- 停機 H2：`backups/cutover-20260726-123304/stockdb-h2-stopped.mv.db`，1,110,016 bytes，SHA-256 `FC4A469D355440CA1402219307CD9BA362A66001DAF5C3531C97535F1C14FFFB`。
- 切換後 PostgreSQL：`backups/cutover-20260726-123304/stockpredictor-postgresql-cutover.dump`，216,669 bytes，SHA-256 `84F302B794961369A0A94E12105490A1646A410C04495645BC35C1D61456B2CD`。
- `pg_restore --list`：成功，PostgreSQL 17.10 custom archive，共 59 個 TOC entries。
- 排程已啟用；尚待觀察下一個實際排程週期，因此對應驗收項目仍保持未勾選。

- 日期：2026-07-23
- PostgreSQL：17.10
- `pg_restore --list`：成功，custom archive 共 59 個 TOC entries。
- 實際還原：成功還原至隔離資料庫 `stockpredictor_restore_verify_20260723`。
- 還原筆數：`STOCK_DATA=140032`、`STOCK_UNIVERSE=12735`、`INSTITUTIONAL_DATA=16351`、`SCAN_HISTORY=11403`、`MARKET_BREADTH=16`、`MONTHLY_REVENUE=2682`、`FINMIND_DAY_TRADING=9347`、`APP_META=1`，其餘兩表為 0。
- 驗證完成後已刪除隔離驗證資料庫；shadow 主資料庫與 named volume 保留。
