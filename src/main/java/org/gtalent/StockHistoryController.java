package org.gtalent;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stocks")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"})
public class StockHistoryController {
    private static final int DEFAULT_HISTORY_DAYS = 100;

    private final StockHistoryService stockHistoryService;

    public StockHistoryController(StockHistoryService stockHistoryService) {
        this.stockHistoryService = stockHistoryService;
    }

    @GetMapping("/{symbol}/history")
    public List<StockDataPoint> getHistory(@PathVariable("symbol") String symbol,
                                           @RequestParam(name = "limit", defaultValue = "100") int limit) {
        int safeLimit = limit > 0 ? limit : DEFAULT_HISTORY_DAYS;
        return stockHistoryService.getRecentHistory(symbol, safeLimit);
    }

    @GetMapping("/{symbol}/full-history")
    public List<StockDataPoint> getFullHistory(@PathVariable("symbol") String symbol,
                                               @RequestParam(name = "limit", defaultValue = "120") int limit) {
        int safeLimit = limit > 0 ? limit : DEFAULT_HISTORY_DAYS;
        return stockHistoryService.getFullHistory(symbol, safeLimit);
    }

    @GetMapping("/{symbol}/history/backfill")
    public ResponseEntity<HistoryBackfillService.BackfillTaskSnapshot> getHistoryBackfillTask(
            @PathVariable("symbol") String symbol) {
        HistoryBackfillService.BackfillTaskSnapshot snapshot = stockHistoryService.getBackfillTask(symbol);
        return snapshot == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(snapshot);
    }
}
