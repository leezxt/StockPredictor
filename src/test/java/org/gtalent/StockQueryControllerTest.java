package org.gtalent;

import org.gtalent.dto.SymbolProfileResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockQueryControllerTest {
    @Mock
    private TwseService twseService;

    @Mock
    private SymbolProfileService symbolProfileService;

    @Mock
    private StockHistoryService stockHistoryService;

    @Mock
    private BacktestEngine backtestEngine;

    @Test
    void shouldNormalizeStockNameRequest() {
        when(twseService.fetchStockName("2330")).thenReturn("台積電");
        StockQueryController controller =
                new StockQueryController(twseService, symbolProfileService, stockHistoryService, backtestEngine);

        Map<String, String> result = controller.getStockName(" 2330 ");

        assertEquals("2330", result.get("symbol"));
        assertEquals("台積電", result.get("name"));
    }

    @Test
    void shouldPrepareHistoryBeforeProfile() {
        SymbolProfileResult expected = new SymbolProfileResult();
        when(symbolProfileService.getProfile("2330")).thenReturn(expected);
        StockQueryController controller =
                new StockQueryController(twseService, symbolProfileService, stockHistoryService, backtestEngine);

        assertEquals(expected, controller.getSymbolProfile(" 2330 "));
        verify(stockHistoryService).ensureRecentHistory("2330", 120);
    }

    @Test
    void shouldCompareNormalizedSymbolsWithDefaultLimit() {
        List<StockDataPoint> first = List.of(new StockDataPoint("2026-07-22", 100, 1_000));
        List<StockDataPoint> second = List.of(new StockDataPoint("2026-07-22", 200, 2_000));
        when(stockHistoryService.getRecentHistory("2330", 100)).thenReturn(first);
        when(stockHistoryService.getRecentHistory("2317", 100)).thenReturn(second);
        StockQueryController controller =
                new StockQueryController(twseService, symbolProfileService, stockHistoryService, backtestEngine);

        Map<String, List<StockDataPoint>> result = controller.compareStocks(" 2330, 2317 ", 0);

        assertEquals(first, result.get("2330"));
        assertEquals(second, result.get("2317"));
    }

    @Test
    void shouldNormalizeBacktestDefaults() {
        BacktestResult expected = new BacktestResult();
        StockQueryController controller =
                new StockQueryController(twseService, symbolProfileService, stockHistoryService, backtestEngine);
        when(backtestEngine.runDiagnosisBacktest("2330", 250, 100_000.0)).thenReturn(expected);

        assertEquals(expected, controller.runBacktest(" 2330 ", 0, 0));
        verify(stockHistoryService).ensureFullHistory("2330", 330);
        verify(backtestEngine).runDiagnosisBacktest("2330", 250, 100_000.0);
    }
}
