package org.gtalent;

import org.gtalent.dto.PredictionResult;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/stocks")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"})
public class StockSignalController {
    private final StockHistoryService stockHistoryService;
    private final EnhancedInstitutionalService enhancedInstitutionalService;
    private final VolumeAnalysisService volumeAnalysisService;
    private final DivergenceService divergenceService;
    private final AIPredictionService aiPredictionService;

    public StockSignalController(StockHistoryService stockHistoryService,
                                 EnhancedInstitutionalService enhancedInstitutionalService,
                                 VolumeAnalysisService volumeAnalysisService,
                                 DivergenceService divergenceService,
                                 AIPredictionService aiPredictionService) {
        this.stockHistoryService = stockHistoryService;
        this.enhancedInstitutionalService = enhancedInstitutionalService;
        this.volumeAnalysisService = volumeAnalysisService;
        this.divergenceService = divergenceService;
        this.aiPredictionService = aiPredictionService;
    }

    @GetMapping("/{symbol}/chip/analysis")
    public Map<String, Object> getChipAnalysis(@PathVariable("symbol") String symbol) {
        String cleanSymbol = normalizeSymbol(symbol);
        stockHistoryService.ensureRecentHistory(cleanSymbol, 10);

        Map<String, Object> response = new HashMap<>();
        response.put("symbol", cleanSymbol);
        response.put("institutionalSync", enhancedInstitutionalService.calculateInstitutionalSync(cleanSymbol, 10));
        response.put("volumeAnomaly", volumeAnalysisService.detectVolumeAnomaly(cleanSymbol));
        response.put("timestamp", System.currentTimeMillis());

        return response;
    }

    @GetMapping("/{symbol}/divergence")
    public Map<String, Object> getDivergence(@PathVariable("symbol") String symbol) {
        String cleanSymbol = normalizeSymbol(symbol);
        stockHistoryService.ensureRecentHistory(cleanSymbol, 60);

        Map<String, Object> response = new HashMap<>();
        response.put("symbol", cleanSymbol);
        response.put("rsiDivergence", divergenceService.detectRSIDivergence(cleanSymbol));
        response.put("macdDivergence", divergenceService.detectMACDDivergence(cleanSymbol));
        response.put("timestamp", System.currentTimeMillis());

        return response;
    }

    @GetMapping("/{symbol}/prediction")
    public PredictionResult getPrediction(@PathVariable("symbol") String symbol) {
        String cleanSymbol = normalizeSymbol(symbol);
        stockHistoryService.ensureRecentHistory(cleanSymbol, 60);
        return aiPredictionService.predict(cleanSymbol);
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim();
    }
}
