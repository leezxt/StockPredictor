package org.gtalent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsReactionServiceTest {

    private final StockDataRepository stockDataRepository = mock(StockDataRepository.class);
    private final NewsReactionService service = new NewsReactionService(null, null, stockDataRepository);

    @Test
    void shouldReturnMinus15WhenBullishNewsButPriceDropsOrDayTradingOverheated() {
        int score = service.calculateNewsReactionScore(0.7, -0.02, 0.30);
        assertEquals(-15, score);

        int scoreWithOverheat = service.calculateNewsReactionScore(0.8, 0.01, 0.70);
        assertEquals(-15, scoreWithOverheat);
    }

    @Test
    void shouldReturn15WhenBullishBreakoutConfirmed() {
        int score = service.calculateNewsReactionScore(0.6, 0.05, 0.20);
        assertEquals(15, score);
    }

    @Test
    void shouldReturn12WhenBearishNewsButPriceHolds() {
        int score = service.calculateNewsReactionScore(-0.6, -0.003, 0.20);
        assertEquals(12, score);
    }

    @Test
    void shouldResolveTodayReturnFromRepositoryOpenAndClosePrice() {
        StockDataPoint latest = new StockDataPoint("2026-07-24", 100.0, 106.0, 99.0, 105.0, 1_000L);
        when(stockDataRepository.getFullHistory("2330", 2)).thenReturn(List.of(latest));

        NewsReactionService.NewsReactionResult result = service.evaluateTodayReaction("2330");

        assertEquals(0.05, result.todayReturn(), 0.000001);
        verify(stockDataRepository).getFullHistory("2330", 2);
    }
}

