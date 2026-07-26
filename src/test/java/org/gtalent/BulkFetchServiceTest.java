package org.gtalent;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BulkFetchServiceTest {

    private final TwseService twseService = mock(TwseService.class);
    private final StockUniverseRepository stockUniverseRepository = mock(StockUniverseRepository.class);
    private final StockDataRepository stockDataRepository = mock(StockDataRepository.class);
    private final BulkFetchService service =
            new BulkFetchService(twseService, stockUniverseRepository, stockDataRepository);

    @Test
    void shouldFinishWithoutFetchingWhenUniverseIsEmpty() {
        when(stockUniverseRepository.getAllSymbols()).thenReturn(List.of());

        assertTrue(service.startBulkBackfill(1));

        verify(twseService, timeout(1_000)).syncMarketUniverse();
        verify(stockUniverseRepository, timeout(1_000)).getAllSymbols();
        awaitCompletion();
        assertEquals("done", service.getProgress().status());
        assertEquals(0, service.getProgress().total());
    }

    @Test
    void shouldPersistFetchedMonthlyHistoryThroughRepository() {
        List<String[]> monthlyRows = Collections.singletonList(new String[]{"115/07/23", "1,125"});
        when(stockUniverseRepository.getAllSymbols()).thenReturn(List.of("2330"));
        when(twseService.fetchMonthlyData(eq("2330"), anyString())).thenReturn(monthlyRows);

        assertTrue(service.startBulkBackfill(1));

        verify(stockDataRepository, timeout(2_000)).saveMonthlyHistory("2330", monthlyRows);
        awaitCompletion();
        assertEquals("done", service.getProgress().status());
        assertEquals(1, service.getProgress().completed());
        assertEquals(1, service.getProgress().savedRows());
    }

    private void awaitCompletion() {
        long deadline = System.currentTimeMillis() + 2_000;
        while (service.isRunning() && System.currentTimeMillis() < deadline) {
            Thread.onSpinWait();
        }
        assertTrue(!service.isRunning(), "批次補抓工作應在測試期限內完成");
    }
}
