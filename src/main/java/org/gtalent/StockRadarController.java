package org.gtalent;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stocks")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"})
public class StockRadarController {
    private final StockHistoryService stockHistoryService;
    private final RadarService radarService;

    public StockRadarController(StockHistoryService stockHistoryService, RadarService radarService) {
        this.stockHistoryService = stockHistoryService;
        this.radarService = radarService;
    }

    @GetMapping("/{symbol}/radar")
    public ResponseEntity<RadarScoreResult> getRadarScores(@PathVariable("symbol") String symbol) {
        String clean = normalizeSymbol(symbol);
        if (clean.isBlank()) {
            throw new IllegalArgumentException("symbol 不可為空白。");
        }
        try {
            stockHistoryService.ensureRecentHistory(clean, 120);
            return ResponseEntity.ok(radarService.calculateRadarScores(clean));
        } catch (Exception e) {
            System.err.println("❌ 雷達端點例外 [" + clean + "]: " + e.getMessage());
            RadarScoreResult errorResult = new RadarScoreResult();
            errorResult.conclusion = "雷達資料暫時無法取得，請稍後再試：" + e.getMessage();
            errorResult.label = "ERROR";
            return ResponseEntity.ok(errorResult);
        }
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim();
    }
}
