package org.gtalent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockTechnicalAnalysisControllerTest {
    @Mock
    private StockHistoryService stockHistoryService;

    @Mock
    private StockDataRepository stockDataRepository;

    @Mock
    private IndicatorCalculator indicatorCalculator;

    @Test
    void shouldNormalizeSymbolAndKeepMaContract() {
        StockTechnicalAnalysisController controller =
                new StockTechnicalAnalysisController(
                        stockHistoryService, stockDataRepository, indicatorCalculator);
        when(stockDataRepository.calculateMA("2330", 5)).thenReturn(100.0);
        when(stockDataRepository.calculateMA("2330", 20)).thenReturn(90.0);

        StockAnalysis result = controller.getAnalysis(" 2330 ");

        assertEquals("2330", result.symbol);
        assertEquals(100.0, result.ma5);
        assertEquals(90.0, result.ma20);
        verify(stockHistoryService).ensureRecentHistory("2330", 20);
    }

    @Test
    void shouldUseDefaultBollingerLimitForNonPositiveInput() {
        StockTechnicalAnalysisController controller =
                new StockTechnicalAnalysisController(
                        stockHistoryService, stockDataRepository, indicatorCalculator);
        List<StockDataPoint> history =
                List.of(new StockDataPoint("2026-07-22", 100, 1_000));
        Map<String, Double> bands = Map.of("middle", 100.0);

        when(stockDataRepository.getRecentHistory("2330", 100)).thenReturn(history);
        when(stockDataRepository.calculateBollinger("2330")).thenReturn(bands);

        Map<String, Object> result = controller.getBollingerData("2330", 0);

        assertEquals(history, result.get("history"));
        assertEquals(bands, result.get("bands"));
        verify(stockHistoryService).ensureRecentHistory("2330", 100);
    }

    @Test
    void shouldReturnInsufficientStatusForShortVolatilityHistory() {
        StockTechnicalAnalysisController controller =
                new StockTechnicalAnalysisController(
                        stockHistoryService, stockDataRepository, indicatorCalculator);
        when(stockDataRepository.getFullHistory("2330", 80))
                .thenReturn(Collections.emptyList());

        Map<String, Object> result = controller.getVolatility("2330");

        assertEquals(0, result.get("bbwScore"));
        assertEquals(0, result.get("currentBBW"));
        assertEquals("資料不足", result.get("status"));
        verify(stockHistoryService).ensureRecentHistory("2330", 80);
    }

    @Test
    void shouldReturnEmptyBbwForBlankSymbolWithoutBackfill() {
        StockTechnicalAnalysisController controller =
                new StockTechnicalAnalysisController(
                        stockHistoryService, stockDataRepository, indicatorCalculator);

        assertEquals(Collections.emptyList(), controller.getBbwData(" ", 1));
    }
}
