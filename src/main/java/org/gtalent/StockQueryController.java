package org.gtalent;

import org.gtalent.dto.SymbolProfileResult;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stocks")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"})
public class StockQueryController {
    private static final int DEFAULT_HISTORY_DAYS = 100;

    private final TwseService twseService;
    private final SymbolProfileService symbolProfileService;
    private final StockHistoryService stockHistoryService;
    private final BacktestEngine backtestEngine;

    public StockQueryController(TwseService twseService,
                                SymbolProfileService symbolProfileService,
                                StockHistoryService stockHistoryService,
                                BacktestEngine backtestEngine) {
        this.twseService = twseService;
        this.symbolProfileService = symbolProfileService;
        this.stockHistoryService = stockHistoryService;
        this.backtestEngine = backtestEngine;
    }

    @GetMapping("/{symbol}/profile")
    public SymbolProfileResult getSymbolProfile(@PathVariable("symbol") String symbol) {
        String cleanSymbol = normalizeSymbol(symbol);
        stockHistoryService.ensureRecentHistory(cleanSymbol, 120);
        return symbolProfileService.getProfile(cleanSymbol);
    }

    @GetMapping("/{symbol}/name")
    public Map<String, String> getStockName(@PathVariable("symbol") String symbol) {
        String cleanSymbol = normalizeSymbol(symbol);
        Map<String, String> response = new HashMap<>();
        response.put("symbol", cleanSymbol);
        response.put("name", twseService.fetchStockName(cleanSymbol));
        return response;
    }

    @GetMapping("/compare")
    public Map<String, List<StockDataPoint>> compareStocks(
            @RequestParam(name = "symbols") String symbols,
            @RequestParam(name = "limit", defaultValue = "100") int limit) {
        Map<String, List<StockDataPoint>> response = new HashMap<>();
        int safeLimit = limit > 0 ? limit : DEFAULT_HISTORY_DAYS;

        for (String symbol : symbols.split(",")) {
            String cleanSymbol = normalizeSymbol(symbol);
            response.put(cleanSymbol, stockHistoryService.getRecentHistory(cleanSymbol, safeLimit));
        }
        return response;
    }

    @GetMapping("/{symbol}/backtest")
    public BacktestResult runBacktest(@PathVariable("symbol") String symbol,
                                      @RequestParam(name = "lookbackDays", defaultValue = "250") int lookbackDays,
                                      @RequestParam(name = "capital", defaultValue = "100000") double capital) {
        String cleanSymbol = normalizeSymbol(symbol);
        int safeLookback = lookbackDays > 0 ? lookbackDays : 250;
        double safeCapital = capital > 0 ? capital : 100_000.0;
        stockHistoryService.ensureFullHistory(cleanSymbol, safeLookback + 80);
        return backtestEngine.runDiagnosisBacktest(cleanSymbol, safeLookback, safeCapital);
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim();
    }
}
