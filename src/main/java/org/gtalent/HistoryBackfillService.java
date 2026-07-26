package org.gtalent;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class HistoryBackfillService {
    private static final int MAX_BACKFILL_MONTHS = 120;
    private static final int MAX_CONSECUTIVE_EMPTY_MONTHS = 6;

    private final TwseService twseService;
    private final StockHistoryStore historyStore;
    private final ExecutorService executor;
    private final Map<String, BackfillTask> tasks = new ConcurrentHashMap<>();

    @Autowired
    public HistoryBackfillService(TwseService twseService, StockHistoryStore historyStore) {
        this(twseService, historyStore, Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "history-backfill-worker");
            thread.setDaemon(true);
            return thread;
        }));
    }

    HistoryBackfillService(TwseService twseService,
                           StockHistoryStore historyStore,
                           ExecutorService executor) {
        this.twseService = twseService;
        this.historyStore = historyStore;
        this.executor = executor;
    }

    public synchronized BackfillTaskSnapshot requestBackfill(String symbol,
                                                             int requiredDays,
                                                             boolean requireFullHistory) {
        String cleanSymbol = symbol == null ? "" : symbol.trim();
        if (cleanSymbol.isBlank()) {
            return null;
        }

        int safeRequiredDays = Math.max(1, requiredDays);
        BackfillTask existing = tasks.get(cleanSymbol);
        if (existing != null && existing.isActive()) {
            existing.requiredDays = Math.max(existing.requiredDays, safeRequiredDays);
            existing.requireFullHistory = existing.requireFullHistory || requireFullHistory;
            existing.message = "已更新背景回補需求";
            return snapshot(existing);
        }

        if (isReady(cleanSymbol, safeRequiredDays, requireFullHistory)) {
            BackfillTask completed = BackfillTask.completed(cleanSymbol, safeRequiredDays, requireFullHistory);
            tasks.put(cleanSymbol, completed);
            return snapshot(completed);
        }

        BackfillTask task = new BackfillTask(cleanSymbol, safeRequiredDays, requireFullHistory);
        tasks.put(cleanSymbol, task);
        executor.submit(() -> runBackfill(task));
        return snapshot(task);
    }

    public BackfillTaskSnapshot getTask(String symbol) {
        if (symbol == null) {
            return null;
        }
        BackfillTask task = tasks.get(symbol.trim());
        return task == null ? null : snapshot(task);
    }

    private void runBackfill(BackfillTask task) {
        task.status = "RUNNING";
        task.startedAt = Instant.now();
        task.message = "正在回補歷史行情";
        LocalDate cursor = LocalDate.now().withDayOfMonth(1);
        int consecutiveEmptyMonths = 0;

        try {
            for (int i = 0; i < MAX_BACKFILL_MONTHS; i++) {
                if (isReady(task.symbol, task.requiredDays, task.requireFullHistory)) {
                    task.dataReady = true;
                    break;
                }

                task.currentMonth = String.format("%04d-%02d", cursor.getYear(), cursor.getMonthValue());
                List<String[]> monthData = twseService.fetchMonthlyData(
                        task.symbol,
                        String.format("%04d%02d01", cursor.getYear(), cursor.getMonthValue())
                );
                task.processedMonths = i + 1;

                if (monthData == null || monthData.isEmpty()) {
                    consecutiveEmptyMonths++;
                    if (consecutiveEmptyMonths >= MAX_CONSECUTIVE_EMPTY_MONTHS
                            && historyStore.getRecentHistory(task.symbol, 1).isEmpty()) {
                        break;
                    }
                } else {
                    consecutiveEmptyMonths = 0;
                    historyStore.saveMonthlyHistory(task.symbol, monthData);
                }
                cursor = cursor.minusMonths(1);
            }

            task.dataReady = isReady(task.symbol, task.requiredDays, task.requireFullHistory);
            task.status = "COMPLETED";
            task.message = task.dataReady ? "歷史行情回補完成" : "回補結束，但可用資料仍不足";
        } catch (Exception e) {
            task.status = "FAILED";
            task.message = e.getMessage() == null ? "歷史行情回補失敗" : e.getMessage();
        } finally {
            task.currentMonth = "";
            task.finishedAt = Instant.now();
        }
    }

    private boolean isReady(String symbol, int requiredDays, boolean requireFullHistory) {
        List<StockDataPoint> data = requireFullHistory
                ? historyStore.getFullHistory(symbol, requiredDays)
                : historyStore.getRecentHistory(symbol, requiredDays);
        if (data.size() < requiredDays) {
            return false;
        }

        long validCount = requireFullHistory
                ? data.stream().filter(point -> point.o > 0 && point.h > 0 && point.l > 0 && point.c > 0).count()
                : data.stream().filter(point -> point.volume > 0).count();
        long minimumValid = Math.max(1L, (long) Math.ceil(data.size() * 0.9));
        return validCount >= minimumValid;
    }

    private BackfillTaskSnapshot snapshot(BackfillTask task) {
        int progressPct = task.dataReady
                ? 100
                : Math.min(99, (int) Math.round(task.processedMonths * 100.0 / MAX_BACKFILL_MONTHS));
        return new BackfillTaskSnapshot(
                task.symbol,
                task.status,
                task.requiredDays,
                task.requireFullHistory,
                task.processedMonths,
                MAX_BACKFILL_MONTHS,
                progressPct,
                task.currentMonth,
                task.dataReady,
                task.message,
                task.startedAt,
                task.finishedAt
        );
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    public record BackfillTaskSnapshot(
            String symbol,
            String status,
            int requiredDays,
            boolean requireFullHistory,
            int processedMonths,
            int maxMonths,
            int progressPct,
            String currentMonth,
            boolean dataReady,
            String message,
            Instant startedAt,
            Instant finishedAt
    ) {
    }

    private static final class BackfillTask {
        private final String symbol;
        private volatile String status = "QUEUED";
        private volatile int requiredDays;
        private volatile boolean requireFullHistory;
        private volatile int processedMonths;
        private volatile String currentMonth = "";
        private volatile boolean dataReady;
        private volatile String message = "歷史行情回補工作已排入佇列";
        private volatile Instant startedAt;
        private volatile Instant finishedAt;

        private BackfillTask(String symbol, int requiredDays, boolean requireFullHistory) {
            this.symbol = symbol;
            this.requiredDays = requiredDays;
            this.requireFullHistory = requireFullHistory;
        }

        private static BackfillTask completed(String symbol, int requiredDays, boolean requireFullHistory) {
            BackfillTask task = new BackfillTask(symbol, requiredDays, requireFullHistory);
            task.status = "COMPLETED";
            task.dataReady = true;
            task.message = "本機歷史行情已足夠";
            task.finishedAt = Instant.now();
            return task;
        }

        private boolean isActive() {
            return "QUEUED".equals(status) || "RUNNING".equals(status);
        }
    }
}
