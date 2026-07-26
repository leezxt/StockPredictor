package org.gtalent;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

@RestController
@RequestMapping("/api/stocks")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"})
public class StockKdController {
    private final StockHistoryService stockHistoryService;
    private final StockKdAnalysisService stockKdAnalysisService;

    public StockKdController(StockHistoryService stockHistoryService,
                             StockKdAnalysisService stockKdAnalysisService) {
        this.stockHistoryService = stockHistoryService;
        this.stockKdAnalysisService = stockKdAnalysisService;
    }

    @GetMapping("/{symbol}/kd")
    public ResponseEntity<KDInfo> getKdDataEndpoint(@PathVariable("symbol") String symbol,
                                                    @RequestParam(name = "limit", defaultValue = "120") int limit) {
        String clean = symbol == null ? "" : symbol.trim();
        if (clean.isBlank()) {
            throw new IllegalArgumentException("symbol 不可為空白。");
        }
        try {
            stockHistoryService.ensureRecentHistory(clean, Math.max(60, limit + 40));
            KDInfo info = stockKdAnalysisService.analyze(clean, limit);
            info.setLatestClosePrice(stockHistoryService.getLatestPrice(clean));
            return ResponseEntity.ok(info);
        } catch (Exception e) {
            System.err.println("❌ KD 端點例外 [" + clean + "]: " + e.getMessage());
            KDInfo errorInfo = new KDInfo();
            errorInfo.setKdSeries(Collections.emptyList());
            errorInfo.setDiagnosis("KD 資料暫時無法取得，請稍後再試：" + e.getMessage());
            errorInfo.setLatestClosePrice(stockHistoryService.getLatestPrice(clean));
            return ResponseEntity.ok(errorInfo);
        }
    }
}
