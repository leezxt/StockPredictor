package org.gtalent;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 全市場批次補抓服務：
 * 一鍵下載 STOCK_UNIVERSE 中所有股票最近 N 個月的歷史收盤資料。
 */
@Service
public class BulkFetchService {

    private final AtomicBoolean running   = new AtomicBoolean(false);
    private final AtomicInteger total     = new AtomicInteger(0);
    private final AtomicInteger completed = new AtomicInteger(0);
    private final AtomicInteger saved     = new AtomicInteger(0);
    private volatile String     status    = "idle";  // idle | running | done | error

    /** 是否有任務正在執行 */
    public boolean isRunning() {
        return running.get();
    }

    /** 取得目前進度快照 */
    public ProgressSnapshot getProgress() {
        return new ProgressSnapshot(
                status,
                total.get(),
                completed.get(),
                saved.get()
        );
    }

    /**
     * 啟動非同步批次補抓（每支股票抓最近 backMonths 個月資料）。
     * 若已在執行中則直接回傳 false。
     */
    public boolean startBulkBackfill(int backMonths) {
        if (!running.compareAndSet(false, true)) {
            return false;  // 已有任務在跑
        }

        int safeMonths = Math.max(1, Math.min(backMonths, 24));

        Thread worker = new Thread(() -> {
            try {
                status = "running";
                completed.set(0);
                saved.set(0);

                // 1. 先確保股票池是最新的
                TwseService twse = new TwseService();
                twse.syncMarketUniverse();

                // 2. 取得所有 active 股票
                List<String> symbols = DatabaseManager.getAllSymbols();
                total.set(symbols.size());
                System.out.printf("[BulkFetch] 開始補抓 %d 支股票，各抓最近 %d 個月%n", symbols.size(), safeMonths);

                // 3. 逐支股票補抓
                for (String symbol : symbols) {
                    if (symbol == null || symbol.isBlank()) {
                        completed.incrementAndGet();
                        continue;
                    }
                    try {
                        LocalDate cursor = LocalDate.now().withDayOfMonth(1);
                        for (int m = 0; m < safeMonths; m++) {
                            String dateStr = String.format("%04d%02d01",
                                    cursor.getYear(), cursor.getMonthValue());
                            List<String[]> monthData = twse.fetchMonthlyData(symbol, dateStr);
                            if (monthData != null && !monthData.isEmpty()) {
                                DatabaseManager.saveAllToDatabase(symbol, monthData);
                                saved.addAndGet(monthData.size());
                            }
                            cursor = cursor.minusMonths(1);
                            // 短暫節流，避免觸發 TWSE 限速（每月請求間隔 300ms）
                            Thread.sleep(300);
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        status = "error";
                        System.err.println("[BulkFetch] 任務被中斷");
                        return;
                    } catch (Exception e) {
                        System.err.printf("[BulkFetch] 跳過 %s: %s%n", symbol, e.getMessage());
                    }
                    completed.incrementAndGet();
                }

                status = "done";
                System.out.printf("[BulkFetch] 完成！processed=%d, savedRows=%d%n",
                        completed.get(), saved.get());
            } catch (Exception e) {
                status = "error";
                System.err.println("[BulkFetch] 補抓失敗: " + e.getMessage());
            } finally {
                running.set(false);
            }
        }, "bulk-fetch-worker");

        worker.setDaemon(true);
        worker.start();
        return true;
    }

    /**
     * 使用 TWSE STOCK_DAY_ALL OpenAPI 一次抓取今日所有上市收盤資料並存庫。
     * 搭配 TPEX daily quotes API 補齊上櫃股票。
     *
     * @return 寫入的 row 數
     */
    public int bulkSaveTodayData() {
        TwseService twse = new TwseService();
        return twse.bulkFetchAndSaveTodayData();
    }

    public record ProgressSnapshot(String status, int total, int completed, int savedRows) {}
}

