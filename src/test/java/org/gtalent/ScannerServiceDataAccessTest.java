package org.gtalent;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScannerServiceDataAccessTest {

    private final StockDataRepository stockDataRepository = mock(StockDataRepository.class);
    private final InstitutionalDataRepository institutionalDataRepository =
            mock(InstitutionalDataRepository.class);
    private final ScanHistoryRepository historyRepository = mock(ScanHistoryRepository.class);
    private final InstitutionalService institutionalService = mock(InstitutionalService.class);
    private final NewsReactionService newsReactionService = mock(NewsReactionService.class);
    private final ScoreEngine scoreEngine = mock(ScoreEngine.class);
    private final FinMindClient finMindClient = mock(FinMindClient.class);
    private final ScannerService service = new ScannerService(
            mock(TwseService.class),
            mock(IndicatorCalculator.class),
            mock(StockUniverseRepository.class),
            stockDataRepository,
            institutionalDataRepository,
            historyRepository,
            institutionalService,
            newsReactionService,
            scoreEngine,
            finMindClient);

    @Test
    void shouldCalculateMovingAverageThroughStockDataRepository() {
        when(stockDataRepository.calculateMA("2330", 20)).thenReturn(101.25);

        double result = ReflectionTestUtils.invokeMethod(service, "calculateMA", "2330", 20);

        assertEquals(101.25, result);
        verify(stockDataRepository).calculateMA("2330", 20);
    }

    @Test
    void shouldCalculateInstitutionalBonusThroughRepository() {
        InstitutionalTrade latest = new InstitutionalTrade(
                "2026-07-25", 100L, 200L, 50L, 1_000L);
        when(institutionalDataRepository.getRecentInstitutionalTrades("2330", 10))
                .thenReturn(List.of(latest));
        when(institutionalDataRepository.getAverageInstitutionalNetBuy("2330", 5))
                .thenReturn(100.0);

        int result = ReflectionTestUtils.invokeMethod(
                service, "calculateInstitutionalBonus", "2330");

        assertEquals(35, result);
        verify(institutionalDataRepository).getRecentInstitutionalTrades("2330", 10);
        verify(institutionalDataRepository).getAverageInstitutionalNetBuy("2330", 5);
    }

    @Test
    void shouldLoadDayTradingHistoryThroughRepository() {
        when(stockDataRepository.getLatestVolume("2330")).thenReturn(1_000L);
        when(institutionalDataRepository.getDayTradingHistory("2330", 5)).thenReturn(List.of());

        Object result = ReflectionTestUtils.invokeMethod(service, "calculateVolumeQuality", "2330");

        org.junit.jupiter.api.Assertions.assertNotNull(result);
        verify(institutionalDataRepository).getDayTradingHistory("2330", 5);
    }

    @Test
    void shouldCalculateEtfDiscountPremiumScoreThroughFinMindClient() {
        when(finMindClient.fetchEtfDiscountPremium("0050"))
                .thenReturn(new FinMindClient.EtfPremiumResult(-0.012, true, "TEST"));

        int result = ReflectionTestUtils.invokeMethod(
                service, "calculateEtfDiscountPremiumScore", "0050");

        assertEquals(7, result);
        verify(finMindClient).fetchEtfDiscountPremium("0050");
    }
}
