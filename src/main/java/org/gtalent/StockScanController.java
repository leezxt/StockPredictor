package org.gtalent;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/stocks")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"})
public class StockScanController {
    private static final int MAX_SCAN_RESULTS = 2000;

    private final ScannerService scannerService;
    private final ScanTaskService scanTaskService;

    public StockScanController(ScannerService scannerService, ScanTaskService scanTaskService) {
        this.scannerService = scannerService;
        this.scanTaskService = scanTaskService;
    }

    @GetMapping("/scan/ma-patterns")
    public List<ScannedResult> scanMAPatterns(
            @RequestParam(name = "type", defaultValue = "BULLISH") String type) {
        return scannerService.scanMAPatterns(type);
    }

    @GetMapping("/scan")
    public List<ScannedResult> scanMarket(@RequestParam(name = "top", defaultValue = "10") int top,
                                          @RequestParam(name = "assetType", required = false) String assetType,
                                          @RequestParam(name = "market", required = false) String market) {
        ScanRequest request = normalizeRequest(top, assetType, market);
        return scannerService.scanAllStocks(request.top(), request.assetType(), request.market());
    }

    @PostMapping("/scan/tasks")
    public Map<String, Object> startScanTask(@RequestParam(name = "top", defaultValue = "10") int top,
                                             @RequestParam(name = "assetType", required = false) String assetType,
                                             @RequestParam(name = "market", required = false) String market) {
        ScanRequest request = normalizeRequest(top, assetType, market);
        String taskId = scanTaskService.startScan(request.top(), request.assetType(), request.market());
        return Map.of(
                "taskId", taskId,
                "status", "QUEUED",
                "message", "掃描工作已排入佇列"
        );
    }

    @GetMapping("/scan/tasks/{taskId}")
    public ResponseEntity<ScanTaskService.ScanTaskSnapshot> getScanTask(
            @PathVariable("taskId") String taskId) {
        ScanTaskService.ScanTaskSnapshot snapshot = scanTaskService.getTask(taskId);
        return snapshot == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(snapshot);
    }

    @GetMapping("/trust-locked")
    public List<Map<String, Object>> getTrustLocked(
            @RequestParam(name = "top", defaultValue = "5") int top) {
        int safeTop = top > 0 ? Math.min(top, 20) : 5;
        return scannerService.getTopTrustStocks(safeTop);
    }

    private ScanRequest normalizeRequest(int top, String assetType, String market) {
        int safeTop = Math.max(1, Math.min(top, MAX_SCAN_RESULTS));
        return new ScanRequest(safeTop, normalizeFilter(assetType), normalizeFilter(market));
    }

    private String normalizeFilter(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim().toUpperCase(Locale.ROOT);
    }

    private record ScanRequest(int top, String assetType, String market) {
    }
}
