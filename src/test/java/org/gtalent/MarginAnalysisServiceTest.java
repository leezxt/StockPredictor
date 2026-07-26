package org.gtalent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarginAnalysisServiceTest {

    private final MarginAnalysisService service = new MarginAnalysisService();

    @Test
    void shouldReturnZeroWhenHistoryIsNullOrInsufficient() {
        assertEquals(0, service.calculateMarginScore(null));
        assertEquals(0, service.calculateMarginScore(List.of(
                margin("2026-07-21", 1_000, 100),
                margin("2026-07-22", 1_000, 100),
                margin("2026-07-23", 1_000, 100),
                margin("2026-07-24", 1_000, 100))));
    }

    @Test
    void shouldRewardWeeklyMarginDecrease() {
        assertEquals(10, service.calculateMarginScore(history(
                1_000, 100,
                900, 100,
                800, 100)));
    }

    @Test
    void shouldPenalizeWeeklyMarginSurge() {
        assertEquals(-15, service.calculateMarginScore(history(
                1_000, 100,
                1_000, 100,
                1_151, 100)));
    }

    @Test
    void shouldRewardShortSqueezeCondition() {
        assertEquals(10, service.calculateMarginScore(history(
                1_000, 100,
                1_000, 250,
                1_000, 300)));
    }

    @Test
    void shouldStackMarginSurgePenaltyAndShortSqueezeReward() {
        assertEquals(-5, service.calculateMarginScore(history(
                1_000, 100,
                1_000, 300,
                1_200, 400)));
    }

    private List<FinMindMarginData> history(
            long firstMargin, long firstShort,
            long previousMargin, long previousShort,
            long todayMargin, long todayShort) {
        return List.of(
                margin("2026-07-21", firstMargin, firstShort),
                margin("2026-07-22", firstMargin, firstShort),
                margin("2026-07-23", firstMargin, firstShort),
                margin("2026-07-24", previousMargin, previousShort),
                margin("2026-07-25", todayMargin, todayShort));
    }

    private FinMindMarginData margin(String date, long marginLimit, long shortLimit) {
        return new FinMindMarginData(date, "2330", 0, 0, marginLimit, 0, 0, shortLimit);
    }
}
