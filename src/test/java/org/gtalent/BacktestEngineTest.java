package org.gtalent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BacktestEngineTest {

    private final StockDataRepository stockDataRepository = mock(StockDataRepository.class);
    private final BacktestEngine engine = new BacktestEngine(stockDataRepository);

    @Test
    void shouldReturnDataInsufficientResultWhenDiagnosisHistoryIsTooShort() {
        List<StockDataPoint> history = List.of(
                new StockDataPoint("2026-07-22", 100.0),
                new StockDataPoint("2026-07-23", 101.0)
        );
        when(stockDataRepository.getFullHistory("2330", 310)).thenReturn(history);

        BacktestResult result = engine.runDiagnosisBacktest(" 2330 ", 250, 100_000.0);

        assertEquals(100_000.0, result.finalCapital);
        assertTrue(result.note.contains("至少需要 60 筆"));
        verify(stockDataRepository).getFullHistory("2330", 310);
    }

    @Test
    void shouldLoadHistoryFromRepositoryForMovingAverageBacktest() {
        List<StockDataPoint> history = List.of(
                new StockDataPoint("2026-07-20", 10.0),
                new StockDataPoint("2026-07-21", 10.0),
                new StockDataPoint("2026-07-22", 11.0),
                new StockDataPoint("2026-07-23", 12.0),
                new StockDataPoint("2026-07-24", 13.0)
        );
        when(stockDataRepository.getFullHistory("2330", 13)).thenReturn(history);

        BacktestResult result = engine.runMovingAverageCrossover("2330", 2, 3, 5, 100_000.0);

        assertEquals("2330", result.symbol);
        assertEquals(3, result.longWindow);
        verify(stockDataRepository).getFullHistory("2330", 13);
    }
}
