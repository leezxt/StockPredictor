package org.gtalent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NewsReactionServiceTest {

    private final NewsReactionService service = new NewsReactionService(null, null);

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
}

