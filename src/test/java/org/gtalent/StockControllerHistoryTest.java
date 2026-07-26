package org.gtalent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockControllerHistoryTest {

    @Mock
    private StockHistoryService stockHistoryService;

    @InjectMocks
    private StockHistoryController controller;

    @Test
    void shouldDelegateRecentHistoryWithExistingContract() {
        List<StockDataPoint> cached = List.of(new StockDataPoint("2026-07-22", 100, 1_000));
        when(stockHistoryService.getRecentHistory("2330", 5)).thenReturn(cached);

        List<StockDataPoint> result = controller.getHistory("2330", 5);

        assertEquals(cached, result);
        verify(stockHistoryService).getRecentHistory("2330", 5);
    }

    @Test
    void shouldDelegateFullHistoryWithExistingContract() {
        List<StockDataPoint> cached =
                List.of(new StockDataPoint("2026-07-22", 100, 105, 95, 102, 1_000));
        when(stockHistoryService.getFullHistory("2330", 5)).thenReturn(cached);

        List<StockDataPoint> result = controller.getFullHistory("2330", 5);

        assertEquals(cached, result);
        verify(stockHistoryService).getFullHistory("2330", 5);
    }
}
