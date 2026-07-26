package org.gtalent;

import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 綜合交叉診斷 Controller（ETF / 個股自動分流）。
 *
 * <p>路由：</p>
 * <ul>
 *   <li>{@code GET  /api/cross/{symbol}} — 單檔診斷</li>
 *   <li>{@code POST /api/cross/batch}    — 批次診斷，body: {"symbols": ["2330", "0050", ...]}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/cross")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"})
public class CrossDiagnosisController {

    /** 單次批次最大筆數（保護 FinMind API 流量與後端負載） */
    private static final int BATCH_LIMIT = 50;

    private final MarketCrossScannerService marketCrossScannerService;

    public CrossDiagnosisController(MarketCrossScannerService marketCrossScannerService) {
        this.marketCrossScannerService = marketCrossScannerService;
    }

    @GetMapping("/{symbol}")
    public StockDiagnosticResult diagnoseSingle(@PathVariable("symbol") String symbol) {
        return marketCrossScannerService.crossTestBySymbol(symbol);
    }

    @PostMapping("/batch")
    public Map<String, Object> diagnoseBatch(@RequestBody BatchRequest request) {
        if (request == null || request.symbols == null || request.symbols.isEmpty()) {
            throw new IllegalArgumentException(
                    "請於 body 提供 symbols 陣列，例如 {\"symbols\":[\"2330\",\"0050\"]}。");
        }

        List<String> symbols = request.symbols.stream()
                .filter(s -> s != null && !s.isBlank())
                .limit(BATCH_LIMIT)
                .toList();
        if (symbols.isEmpty()) {
            throw new IllegalArgumentException("symbols 至少需要一個非空白股票代號。");
        }

        long startTime = System.currentTimeMillis();
        List<StockDiagnosticResult> diagnoses = new ArrayList<>();
        List<StockDiagnosticResult> perfectMatches = new ArrayList<>();
        int skipped = 0;
        for (String symbol : symbols) {
            StockDiagnosticResult r = marketCrossScannerService.crossTestBySymbol(symbol);
            diagnoses.add(r);
            if (r.isSkip()) skipped++;
            if (r.isPerfectMatch()) perfectMatches.add(r);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("totalRequested", request.symbols.size());
        response.put("totalScanned", symbols.size());
        response.put("skipped", skipped);
        response.put("perfectMatches", perfectMatches.size());
        response.put("durationMs", System.currentTimeMillis() - startTime);
        response.put("results", diagnoses);
        return response;
    }

    /** 批次請求 body。 */
    public static class BatchRequest {
        public List<String> symbols;
    }
}
