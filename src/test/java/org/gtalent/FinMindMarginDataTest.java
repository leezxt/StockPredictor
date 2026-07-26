package org.gtalent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinMindMarginDataTest {

    @Test
    void shouldCalculateNetChangesRatioAndDayChanges() {
        FinMindMarginData data = new FinMindMarginData(
                "2026-07-25", "2330",
                120, 45, 1_500,
                70, 30, 500);

        assertEquals(75, data.getMarginPurchaseNetChange());
        assertEquals(40, data.getShortSaleNetChange());
        assertEquals(3.0, data.getMarginShortRatio());
        assertEquals(100, data.getMarginDayChange(1_400));
        assertEquals(-25, data.getShortDayChange(525));
    }

    @Test
    void shouldReturnNaNAndSummaryNAWhenShortBalanceIsZero() {
        FinMindMarginData data = new FinMindMarginData(
                "2026-07-25", "2330",
                0, 0, 1_500,
                0, 0, 0);

        assertTrue(Double.isNaN(data.getMarginShortRatio()));
        assertTrue(data.toSummaryString().contains("資券比: N/A"));
    }
}
