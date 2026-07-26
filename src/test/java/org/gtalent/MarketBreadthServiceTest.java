package org.gtalent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketBreadthServiceTest {
    private StockUniverseRepository stockUniverseRepository;
    private StockDataRepository stockDataRepository;
    private InstitutionalDataRepository institutionalDataRepository;
    private MarketDataRepository marketDataRepository;
    private MarketBreadthService service;

    @BeforeEach
    void setUp() {
        stockUniverseRepository = mock(StockUniverseRepository.class);
        stockDataRepository = mock(StockDataRepository.class);
        institutionalDataRepository = mock(InstitutionalDataRepository.class);
        marketDataRepository = mock(MarketDataRepository.class);
        service = new MarketBreadthService(
                stockUniverseRepository,
                stockDataRepository,
                institutionalDataRepository,
                marketDataRepository
        );
    }

    @Test
    void shouldSaveEmptyBreadthWhenUniverseIsEmpty() {
        when(stockUniverseRepository.getAllSymbols()).thenReturn(List.of());

        MarketBreadthResult result = service.calculateMarketBreadth();

        assertEquals(0.0, result.getBreadth());
        assertEquals(0, result.getEligibleCount());
        verify(marketDataRepository).saveDailyMarketBreadth(any(MarketBreadthResult.class));
    }

    @Test
    void shouldCalculateBullishBreadthFromRepositoryData() {
        when(stockUniverseRepository.getAllSymbols()).thenReturn(List.of("2330"));
        when(stockDataRepository.calculateMA("2330", 5)).thenReturn(120.0);
        when(stockDataRepository.calculateMA("2330", 20)).thenReturn(110.0);
        when(stockDataRepository.calculateMA("2330", 60)).thenReturn(100.0);
        when(stockDataRepository.getLatestVolume("2330")).thenReturn(1_000L);
        when(stockDataRepository.calculateVolumeMA("2330", 5)).thenReturn(900L);
        when(institutionalDataRepository.getDayTradingHistory("2330", 1)).thenReturn(List.of());
        when(institutionalDataRepository.getDayTradingHistory("2330", 5)).thenReturn(List.of());

        MarketBreadthResult result = service.calculateMarketBreadth();

        assertEquals(100.0, result.getBreadth());
        assertEquals(1, result.getEligibleCount());
        assertEquals(1, result.getBullishCount());
        verify(marketDataRepository).saveDailyMarketBreadth(result);
    }
}
