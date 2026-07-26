package org.gtalent;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/stocks")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"})
public class StockDataOperationsController {
    private final BulkFetchService bulkFetchService;
    private final ScheduledService scheduledService;

    public StockDataOperationsController(BulkFetchService bulkFetchService,
                                         ScheduledService scheduledService) {
        this.bulkFetchService = bulkFetchService;
        this.scheduledService = scheduledService;
    }

    @PostMapping("/bulk-fetch")
    public Map<String, Object> startBulkFetch(
            @RequestParam(name = "months", defaultValue = "3") int months) {
        Map<String, Object> result = new HashMap<>();
        int safeMonths = Math.max(1, Math.min(months, 24));
        boolean started = bulkFetchService.startBulkBackfill(safeMonths);
        result.put("started", started);
        result.put("months", safeMonths);
        result.put("message", started
                ? "全市場批次補抓已啟動（背景執行），請定期查詢 /api/stocks/bulk-fetch/status 追蹤進度。"
                : "已有補抓作業在執行中，請稍後再試。");
        return result;
    }

    @GetMapping("/bulk-fetch/status")
    public Map<String, Object> getBulkFetchStatus() {
        BulkFetchService.ProgressSnapshot snapshot = bulkFetchService.getProgress();
        Map<String, Object> result = new HashMap<>();
        result.put("status", snapshot.status());
        result.put("total", snapshot.total());
        result.put("completed", snapshot.completed());
        result.put("savedRows", snapshot.savedRows());
        int progressPct = snapshot.total() <= 0
                ? 0
                : (int) Math.round(snapshot.completed() * 100.0 / snapshot.total());
        result.put("progressPct", progressPct);
        return result;
    }

    @PostMapping("/today-data")
    public Map<String, Object> fetchTodayData() {
        Map<String, Object> result = new HashMap<>();
        int saved = bulkFetchService.bulkSaveTodayData();
        result.put("savedRows", saved);
        result.put("message", "今日全市場資料更新完成，共寫入 " + saved + " 筆。");
        return result;
    }

    @PostMapping("/{symbol}/backup")
    public Map<String, Object> startSingleStockBackup(@PathVariable("symbol") String symbol) {
        return scheduledService.startSingleStockBackup(normalizeSymbol(symbol));
    }

    @GetMapping("/{symbol}/backup/status")
    public Map<String, Object> getSingleStockBackupStatus(@PathVariable("symbol") String symbol) {
        return scheduledService.getSingleStockBackupStatus(normalizeSymbol(symbol));
    }

    @PostMapping("/backup/full")
    public Map<String, Object> startFullBackup() {
        return scheduledService.startFullBackup();
    }

    @GetMapping("/backup/full/status")
    public Map<String, Object> getFullBackupStatus() {
        return scheduledService.getFullBackupStatus();
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim();
    }
}
