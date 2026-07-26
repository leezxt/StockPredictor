package org.gtalent;

import org.gtalent.dto.DivergenceResult;
import org.gtalent.dto.InstitutionalSyncResult;
import org.gtalent.dto.PredictionResult;
import org.gtalent.dto.VolumeAnomalyResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockSignalControllerTest {
    @Mock
    private StockHistoryService stockHistoryService;

    @Mock
    private EnhancedInstitutionalService enhancedInstitutionalService;

    @Mock
    private VolumeAnalysisService volumeAnalysisService;

    @Mock
    private DivergenceService divergenceService;

    @Mock
    private AIPredictionService aiPredictionService;

    @Test
    void shouldPrepareHistoryAndReturnChipAnalysis() {
        InstitutionalSyncResult institutionalSync =
                new InstitutionalSyncResult(80, "買超", "買超", true, 1_000);
        VolumeAnomalyResult volumeAnomaly =
                new VolumeAnomalyResult("量增價漲", 1.8, 2.5, "測試");
        when(enhancedInstitutionalService.calculateInstitutionalSync("2330", 10))
                .thenReturn(institutionalSync);
        when(volumeAnalysisService.detectVolumeAnomaly("2330")).thenReturn(volumeAnomaly);
        StockSignalController controller = createController();

        Map<String, Object> result = controller.getChipAnalysis(" 2330 ");

        assertEquals("2330", result.get("symbol"));
        assertSame(institutionalSync, result.get("institutionalSync"));
        assertSame(volumeAnomaly, result.get("volumeAnomaly"));
        assertNotNull(result.get("timestamp"));
        verify(stockHistoryService).ensureRecentHistory("2330", 10);
    }

    @Test
    void shouldPrepareHistoryAndReturnDivergenceAnalysis() {
        DivergenceResult rsi = new DivergenceResult("RSI", "底背離", 0.8, true, "測試");
        DivergenceResult macd = new DivergenceResult("MACD", "無", 0, false, "測試");
        when(divergenceService.detectRSIDivergence("2330")).thenReturn(rsi);
        when(divergenceService.detectMACDDivergence("2330")).thenReturn(macd);
        StockSignalController controller = createController();

        Map<String, Object> result = controller.getDivergence(" 2330 ");

        assertEquals("2330", result.get("symbol"));
        assertSame(rsi, result.get("rsiDivergence"));
        assertSame(macd, result.get("macdDivergence"));
        assertNotNull(result.get("timestamp"));
        verify(stockHistoryService).ensureRecentHistory("2330", 60);
    }

    @Test
    void shouldPrepareHistoryAndReturnPrediction() {
        PredictionResult prediction = new PredictionResult();
        when(aiPredictionService.predict("2330")).thenReturn(prediction);
        StockSignalController controller = createController();

        assertSame(prediction, controller.getPrediction(" 2330 "));
        verify(stockHistoryService).ensureRecentHistory("2330", 60);
    }

    private StockSignalController createController() {
        return new StockSignalController(
                stockHistoryService,
                enhancedInstitutionalService,
                volumeAnalysisService,
                divergenceService,
                aiPredictionService);
    }
}
