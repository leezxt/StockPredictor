package org.gtalent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketServiceTest {

    private final StockUniverseRepository stockUniverseRepository = mock(StockUniverseRepository.class);
    private final MarketService service = new MarketService(stockUniverseRepository);

    @Test
    void shouldLoadStocksFromRepository() {
        when(stockUniverseRepository.getAllSymbols("STOCK", null))
                .thenReturn(List.of("2330", "2454"));

        assertEquals(List.of("2330", "2454"), service.getAllSymbols());
        verify(stockUniverseRepository).getAllSymbols("STOCK", null);
    }

    @Test
    void shouldCombineEquityAndBondEtfs() {
        when(stockUniverseRepository.getAllSymbols("ETF", null))
                .thenReturn(List.of("00878", "0050"));
        when(stockUniverseRepository.getAllSymbols("BOND_ETF", null))
                .thenReturn(List.of("00679B", "0050"));

        assertEquals(List.of("0050", "00679B", "00878"), service.getSymbolsByType(" etf "));
    }

    @Test
    void shouldLoadAllActiveAssetsForAllType() {
        when(stockUniverseRepository.getAllSymbols()).thenReturn(List.of("0050", "2330"));

        assertEquals(List.of("0050", "2330"), service.getSymbolsByType("ALL"));
        verify(stockUniverseRepository).getAllSymbols();
    }

    @Test
    void shouldReturnEmptyListForUnsupportedType() {
        assertEquals(List.of(), service.getSymbolsByType("OPTION"));
    }
}
