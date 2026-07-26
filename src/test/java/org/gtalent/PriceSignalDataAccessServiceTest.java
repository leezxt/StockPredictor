package org.gtalent;

import org.gtalent.dto.DivergenceResult;
import org.gtalent.dto.VolumeAnomalyResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PriceSignalDataAccessServiceTest {

    private final StockDataRepository stockDataRepository = mock(StockDataRepository.class);

    @Test
    void shouldReturnInsufficientRsiResultWhenRepositoryHistoryIsTooShort() {
        when(stockDataRepository.getRecentHistory("2330", 44))
                .thenReturn(List.of(new StockDataPoint("2026-07-25", 100.0)));
        DivergenceService service = new DivergenceService(
                stockDataRepository,
                mock(IndicatorCalculator.class));

        DivergenceResult result = service.detectRSIDivergence("2330");

        assertEquals("RSI", result.getIndicatorType());
        assertEquals("無", result.getDivergenceType());
        assertFalse(result.isConfirmed());
        assertEquals("資料不足", result.getDescription());
        verify(stockDataRepository).getRecentHistory("2330", 44);
    }

    @Test
    void shouldDetectPriceRiseWithVolumeExpansionFromRepositoryHistory() {
        List<StockDataPoint> history = List.of(
                point("2026-07-20", 98.0, 100),
                point("2026-07-21", 99.0, 100),
                point("2026-07-22", 99.0, 100),
                point("2026-07-23", 100.0, 100),
                point("2026-07-24", 100.0, 100),
                point("2026-07-25", 103.0, 200)
        );
        when(stockDataRepository.getRecentHistory("2330", 6)).thenReturn(history);
        VolumeAnalysisService service = new VolumeAnalysisService(stockDataRepository);

        VolumeAnomalyResult result = service.detectVolumeAnomaly("2330");

        assertEquals("量增價漲", result.getSignalType());
        assertEquals(2.0, result.getVolumeRatio());
        assertEquals(3.0, result.getPriceChangePct());
        verify(stockDataRepository).getRecentHistory("2330", 6);
    }

    private StockDataPoint point(String date, double close, long volume) {
        return new StockDataPoint(date, close, volume);
    }
}
