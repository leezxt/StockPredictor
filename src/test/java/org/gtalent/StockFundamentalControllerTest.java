package org.gtalent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockFundamentalControllerTest {
    @Mock
    private FundamentalService fundamentalService;

    @Mock
    private InstitutionalService institutionalService;

    @Mock
    private DayTradingService dayTradingService;

    @Mock
    private StockHistoryService stockHistoryService;

    @Mock
    private ScoreEngine scoreEngine;

    @Test
    void shouldNormalizeRevenueRequestAndCalculateScores() {
        RevenueData latest = new RevenueData("2026-06", 100, 3.5, 12.5);
        List<RevenueData> history = List.of(latest);
        when(fundamentalService.backfillRevenueHistory("2330", 120)).thenReturn(2);
        when(fundamentalService.getRevenueHistory("2330", 120)).thenReturn(history);
        when(fundamentalService.calculateRevenueScore(history)).thenReturn(80);
        when(scoreEngine.scoreFundamental(80, 12.5, 3.5)).thenReturn(85);

        Map<String, Object> result = createController().getRevenueHistory(" 2330 ", 999);

        assertEquals("2330", result.get("symbol"));
        assertEquals(120, result.get("months"));
        assertEquals(80, result.get("revenueScore"));
        assertEquals(85, result.get("fundamentalScore"));
        assertEquals(2, result.get("backfilledCount"));
        assertSame(latest, result.get("latest"));
    }

    @Test
    void shouldUseDefaultInstitutionalLimit() {
        List<InstitutionalTrade> expected = List.of(new InstitutionalTrade());
        when(institutionalService.getRecentInstitutionalTrades("2330", 60)).thenReturn(expected);

        assertSame(expected, createController().getInstitutional(" 2330 ", 0));
    }

    @Test
    void shouldClampDayTradingDays() {
        FinMindDayTradingData latest = new FinMindDayTradingData();
        List<FinMindDayTradingData> history = List.of(latest);
        when(dayTradingService.getDayTradingHistory("2330", 180)).thenReturn(history);

        Map<String, Object> result = createController().getDayTrading(" 2330 ", 999);

        assertEquals(180, result.get("days"));
        assertEquals(1, result.get("sampleSize"));
        assertSame(latest, result.get("latest"));
        assertEquals(FinMindDataset.DAY_TRADE.getDatasetName(), result.get("dataset"));
    }

    @Test
    void shouldClampBigHolderWeeks() {
        List<FinMindShareholdingData> expected = List.of(shareholding(10));
        when(institutionalService.getLargeHolderShareholdingHistory("2330", 4)).thenReturn(expected);

        assertSame(expected, createController().getBigHolders(" 2330 ", 1));
    }

    @Test
    void shouldDetectAdvancedBigHolderDivergence() {
        List<FinMindShareholdingData> holders =
                List.of(shareholding(10), shareholding(11), shareholding(12));
        when(institutionalService.getLargeHolderShareholdingHistory("2330", 16)).thenReturn(holders);
        when(institutionalService.calculateBigHolderScore(holders)).thenReturn(90);

        List<StockDataPoint> prices = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            prices.add(new StockDataPoint("2026-07-" + (i + 1), 100 - i, 1_000));
        }
        when(stockHistoryService.getRecentHistory("2330", 35)).thenReturn(prices);

        Map<String, Object> result = createController().getBigHolderDiagnosis(" 2330 ", 16);

        assertEquals("burst-warning", result.get("signal"));
        assertEquals(true, result.get("holdersUpTrend"));
        assertEquals(true, result.get("priceWeak"));
        assertEquals(true, result.get("advancedDivergence"));
        verify(stockHistoryService).getRecentHistory("2330", 35);
    }

    private StockFundamentalController createController() {
        return new StockFundamentalController(
                fundamentalService,
                institutionalService,
                dayTradingService,
                stockHistoryService,
                scoreEngine);
    }

    private FinMindShareholdingData shareholding(double percentage) {
        FinMindShareholdingData data = new FinMindShareholdingData();
        data.setPercentage(percentage);
        return data;
    }
}
