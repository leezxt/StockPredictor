package org.gtalent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundamentalServiceTest {
    @Mock
    private MarketDataRepository marketDataRepository;

    @Test
    void shouldReturnCachedRevenueHistoryForScoring() {
        List<RevenueData> cached = List.of(
                new RevenueData("2026-04", 80, 1, 5),
                new RevenueData("2026-05", 90, 2, 6),
                new RevenueData("2026-06", 100, 3, 7));
        when(marketDataRepository.getRevenueHistory("2330", 12)).thenReturn(cached);

        List<RevenueData> result =
                new FundamentalService(marketDataRepository).getRevenueHistoryForScoring(" 2330 ", 12);

        assertSame(cached, result);
        verify(marketDataRepository).getRevenueHistory("2330", 12);
    }

    @Test
    void shouldSkipBackfillWhenRequestedHistoryIsComplete() {
        List<RevenueData> cached = List.of(
                new RevenueData("2026-05", 90, 2, 6),
                new RevenueData("2026-06", 100, 3, 7));
        when(marketDataRepository.getRevenueHistory("2330", 2)).thenReturn(cached);

        int saved = new FundamentalService(marketDataRepository).backfillRevenueHistory(" 2330 ", 2);

        assertEquals(0, saved);
        verify(marketDataRepository, never()).saveRevenueData(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(RevenueData.class));
    }

    @Test
    void shouldParseAndPersistMatchingRevenueCsv() {
        String csv = String.join("\n",
                "資料年月,公司代號,營業收入-當月營收,營業收入-上月比較增減(%),營業收入-去年同月增減(%)",
                "11506,2330,\"100,000\",3.5,12.5",
                "11506,2317,\"90,000\",2.0,8.0");
        FundamentalService service = new FundamentalService(marketDataRepository);

        int saved = service.parseAndSaveRevenueCsv(csv, "2330");

        assertEquals(1, saved);
        verify(marketDataRepository).saveRevenueData(
                org.mockito.ArgumentMatchers.eq("2330"),
                org.mockito.ArgumentMatchers.argThat(data ->
                        "2026-06".equals(data.getYearMonth())
                                && data.getRevenue() == 100_000_000.0
                                && data.getMom() == 3.5
                                && data.getYoy() == 12.5));
        verify(marketDataRepository, never()).saveRevenueData(
                org.mockito.ArgumentMatchers.eq("2317"),
                org.mockito.ArgumentMatchers.any(RevenueData.class));
    }
}
