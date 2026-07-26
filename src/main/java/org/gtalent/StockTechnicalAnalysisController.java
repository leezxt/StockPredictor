package org.gtalent;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stocks")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"})
public class StockTechnicalAnalysisController {
    private static final int DEFAULT_HISTORY_DAYS = 100;

    private final StockHistoryService stockHistoryService;
    private final StockDataRepository stockDataRepository;
    private final IndicatorCalculator indicatorCalculator;

    public StockTechnicalAnalysisController(
            StockHistoryService stockHistoryService,
            StockDataRepository stockDataRepository,
            IndicatorCalculator indicatorCalculator) {
        this.stockHistoryService = stockHistoryService;
        this.stockDataRepository = stockDataRepository;
        this.indicatorCalculator = indicatorCalculator;
    }

    @GetMapping("/{symbol}/ma")
    public StockAnalysis getAnalysis(@PathVariable("symbol") String symbol) {
        String cleanSymbol = normalizeSymbol(symbol);
        stockHistoryService.ensureRecentHistory(cleanSymbol, 20);
        double ma5 = stockDataRepository.calculateMA(cleanSymbol, 5);
        double ma20 = stockDataRepository.calculateMA(cleanSymbol, 20);
        return new StockAnalysis(cleanSymbol, ma5, ma20);
    }

    @GetMapping("/{symbol}/indicators/advanced")
    public Map<String, Object> getAdvancedIndicators(@PathVariable("symbol") String symbol) {
        String cleanSymbol = normalizeSymbol(symbol);
        stockHistoryService.ensureRecentHistory(cleanSymbol, 120);

        Map<String, Object> response = new HashMap<>();
        response.put("symbol", cleanSymbol);
        response.put("bollinger", indicatorCalculator.calculateBollinger(cleanSymbol));
        response.put("ichimoku", indicatorCalculator.calculateIchimoku(cleanSymbol));
        response.put("vwap", indicatorCalculator.calculateVWAP(cleanSymbol));
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }

    @GetMapping("/{symbol}/bollinger")
    public Map<String, Object> getBollingerData(@PathVariable("symbol") String symbol,
                                                @RequestParam(name = "limit", defaultValue = "100") int limit) {
        String cleanSymbol = normalizeSymbol(symbol);
        int safeLimit = limit > 0 ? limit : DEFAULT_HISTORY_DAYS;
        stockHistoryService.ensureRecentHistory(cleanSymbol, Math.max(20, safeLimit));

        Map<String, Object> response = new HashMap<>();
        response.put("history", stockDataRepository.getRecentHistory(cleanSymbol, safeLimit));
        response.put("bands", stockDataRepository.calculateBollinger(cleanSymbol));
        return response;
    }

    @GetMapping("/{symbol}/macd")
    public List<MACDResult> getMacdSeries(@PathVariable("symbol") String symbol,
                                          @RequestParam(name = "limit", defaultValue = "60") int limit) {
        String cleanSymbol = normalizeSymbol(symbol);
        stockHistoryService.ensureRecentHistory(cleanSymbol, Math.max(40, limit + 10));
        return indicatorCalculator.calculateMACDSeries(cleanSymbol, limit);
    }

    @GetMapping("/{symbol}/volatility")
    public Map<String, Object> getVolatility(@PathVariable("symbol") String symbol) {
        String cleanSymbol = normalizeSymbol(symbol);
        stockHistoryService.ensureRecentHistory(cleanSymbol, 80);
        Map<String, Object> response = new HashMap<>();

        List<StockDataPoint> fullHistory = stockDataRepository.getFullHistory(cleanSymbol, 80);
        if (fullHistory.size() < 20) {
            response.put("bbwScore", 0);
            response.put("currentBBW", 0);
            response.put("status", "資料不足");
            return response;
        }

        List<Double> bbwHistory = calculateBbwHistory(fullHistory);
        VolatilityAnalyzer analyzer = new VolatilityAnalyzer();
        int bbwScore = analyzer.calculateBBWScore(bbwHistory);
        double currentBBW = bbwHistory.isEmpty() ? 0.0 : bbwHistory.get(bbwHistory.size() - 1);
        String status = bbwScore >= 15
                ? "擠壓中/可能爆發"
                : (bbwScore >= 8 ? "動能釋放期" : "波動穩定");

        response.put("bbwScore", bbwScore);
        response.put("currentBBW", Math.round(currentBBW * 100.0) / 100.0);
        response.put("status", status);
        return response;
    }

    @GetMapping("/{symbol}/bbw")
    public List<Map<String, Object>> getBbwData(@PathVariable("symbol") String symbol,
                                                @RequestParam(name = "limit", defaultValue = "1") int limit) {
        String cleanSymbol = normalizeSymbol(symbol);
        if (cleanSymbol.isBlank()) {
            return Collections.emptyList();
        }
        stockHistoryService.ensureRecentHistory(cleanSymbol, 85);
        List<Double> bbwSeries = indicatorCalculator.calculateBBWSeries(cleanSymbol, 60);
        if (bbwSeries.isEmpty()) {
            return Collections.emptyList();
        }

        int score = new VolatilityAnalyzer().calculateBBWScore(bbwSeries);
        double currentBBW = bbwSeries.get(bbwSeries.size() - 1);
        Map<String, Object> entry = new HashMap<>();
        entry.put("score", score);
        entry.put("bbw", Math.round(currentBBW * 10000.0) / 10000.0);
        return List.of(entry);
    }

    private List<Double> calculateBbwHistory(List<StockDataPoint> fullHistory) {
        List<Double> bbwHistory = new ArrayList<>();
        for (int index = 19; index < fullHistory.size(); index++) {
            double sum = 0.0;
            for (int historyIndex = index - 19; historyIndex <= index; historyIndex++) {
                StockDataPoint point = fullHistory.get(historyIndex);
                sum += point.c > 0 ? point.c : point.price;
            }
            double ma20 = sum / 20.0;
            double squaredDeviationSum = 0.0;
            for (int historyIndex = index - 19; historyIndex <= index; historyIndex++) {
                StockDataPoint point = fullHistory.get(historyIndex);
                double close = point.c > 0 ? point.c : point.price;
                squaredDeviationSum += Math.pow(close - ma20, 2);
            }
            double standardDeviation = Math.sqrt(squaredDeviationSum / 20.0);
            double bbw = ma20 == 0.0 ? 0.0 : standardDeviation * 4.0 / ma20 * 100.0;
            bbwHistory.add(bbw);
        }
        return bbwHistory;
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim();
    }
}
