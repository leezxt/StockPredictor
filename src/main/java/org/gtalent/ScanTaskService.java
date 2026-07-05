package org.gtalent;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ScanTaskService {
    private final ScannerService scannerService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "market-scan-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, ScanTask> tasks = new ConcurrentHashMap<>();

    public ScanTaskService(ScannerService scannerService) {
        this.scannerService = scannerService;
    }

    public String startScan(int topN, String assetType, String market) {
        String taskId = UUID.randomUUID().toString();
        ScanTask task = new ScanTask(taskId);
        tasks.put(taskId, task);

        executor.submit(() -> {
            task.status = "RUNNING";
            task.message = "正在建立掃描股票池";
            try {
                task.results = scannerService.scanAllStocks(topN, assetType, market,
                        (completed, total, currentSymbol) -> {
                            task.completed = completed;
                            task.total = total;
                            task.currentSymbol = currentSymbol;
                            task.message = total <= 0
                                    ? "沒有符合條件且具足夠歷史資料的標的"
                                    : "正在分析 " + currentSymbol;
                        });
                task.status = "COMPLETED";
                task.completed = task.total;
                task.currentSymbol = "";
                task.message = "掃描完成";
            } catch (Exception e) {
                task.status = "FAILED";
                task.currentSymbol = "";
                task.message = e.getMessage() == null ? "掃描失敗" : e.getMessage();
            } finally {
                task.finishedAt = Instant.now();
                removeExpiredTasks();
            }
        });
        return taskId;
    }

    public ScanTaskSnapshot getTask(String taskId) {
        ScanTask task = tasks.get(taskId);
        if (task == null) {
            return null;
        }
        int progressPct = task.total <= 0
                ? ("COMPLETED".equals(task.status) ? 100 : 0)
                : (int) Math.round(task.completed * 100.0 / task.total);
        return new ScanTaskSnapshot(
                task.taskId,
                task.status,
                task.total,
                task.completed,
                progressPct,
                task.currentSymbol,
                task.message,
                task.results
        );
    }

    private void removeExpiredTasks() {
        Instant cutoff = Instant.now().minusSeconds(3600);
        tasks.entrySet().removeIf(entry -> {
            Instant finishedAt = entry.getValue().finishedAt;
            return finishedAt != null && finishedAt.isBefore(cutoff);
        });
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    public record ScanTaskSnapshot(
            String taskId,
            String status,
            int total,
            int completed,
            int progressPct,
            String currentSymbol,
            String message,
            List<ScannedResult> results
    ) {
    }

    private static final class ScanTask {
        private final String taskId;
        private volatile String status = "QUEUED";
        private volatile int total;
        private volatile int completed;
        private volatile String currentSymbol = "";
        private volatile String message = "等待掃描工作執行";
        private volatile List<ScannedResult> results = Collections.emptyList();
        private volatile Instant finishedAt;

        private ScanTask(String taskId) {
            this.taskId = taskId;
        }
    }
}
