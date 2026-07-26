package org.gtalent;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TwseServiceTest {

    private final StockUniverseRepository stockUniverseRepository = mock(StockUniverseRepository.class);
    private final StockDataRepository stockDataRepository = mock(StockDataRepository.class);

    @Test
    void shouldPersistFetchedMarketUniverseThroughRepository() {
        StockUniverseEntry entry = new StockUniverseEntry("2330", "台積電", "TWSE", "STOCK", false, true);
        TwseService service = spy(new TwseService(stockUniverseRepository, stockDataRepository));
        when(service.fetchMarketUniverse()).thenReturn(List.of(entry));
        when(stockUniverseRepository.saveStockUniverse(List.of(entry))).thenReturn(1);

        assertEquals(1, service.syncMarketUniverse());

        verify(stockUniverseRepository).saveStockUniverse(List.of(entry));
    }

    @Test
    void shouldConvertMonthlyRowsAndPersistValidPricesThroughRepository() {
        TwseService service = new TwseService(stockUniverseRepository, stockDataRepository);
        when(stockDataRepository.saveSimpleData("2330", "2026-07-23", "1125.5")).thenReturn(true);

        int saved = ReflectionTestUtils.invokeMethod(
                service,
                "saveAllToDatabase",
                "2330",
                List.of(
                        new String[]{"115/07/23", "1,000", "0", "0", "0", "0", "1,125.5"},
                        new String[]{"invalid", "999"}
                )
        );

        assertEquals(1, saved);
        verify(stockDataRepository).saveSimpleData("2330", "2026-07-23", "1125.5");
    }
}
