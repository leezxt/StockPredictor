package org.gtalent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockHistoryServiceTest {
    @Mock
    private HistoryBackfillService historyBackfillService;

    @Mock
    private StockDataRepository stockDataRepository;

    @Test
    void shouldReturnCachedRecentHistoryAndQueueMissingBackfill() {
        StockHistoryService service =
                new StockHistoryService(historyBackfillService, stockDataRepository);
        List<StockDataPoint> cached = List.of(new StockDataPoint("2026-07-22", 100, 1_000));
        when(stockDataRepository.getRecentHistory("2330", 5))
                .thenReturn(Collections.emptyList(), cached);

        List<StockDataPoint> result = service.getRecentHistory(" 2330 ", 5);

        assertEquals(cached, result);
        verify(historyBackfillService).requestBackfill("2330", 5, false);
    }

    @Test
    void shouldReturnCachedFullHistoryAndQueueMissingBackfill() {
        StockHistoryService service =
                new StockHistoryService(historyBackfillService, stockDataRepository);
        List<StockDataPoint> cached =
                List.of(new StockDataPoint("2026-07-22", 100, 105, 95, 102, 1_000));
        when(stockDataRepository.getFullHistory("2330", 5))
                .thenReturn(Collections.emptyList(), cached);

        List<StockDataPoint> result = service.getFullHistory(" 2330 ", 5);

        assertEquals(cached, result);
        verify(historyBackfillService).requestBackfill("2330", 5, true);
    }

    @Test
    void shouldNormalizeSymbolWhenReadingLatestPrice() {
        StockHistoryService service =
                new StockHistoryService(historyBackfillService, stockDataRepository);
        when(stockDataRepository.getLatestPrice("2330")).thenReturn(123.45);

        assertEquals(123.45, service.getLatestPrice(" 2330 "));
    }
}
