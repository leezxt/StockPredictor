package org.gtalent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InstitutionalServiceFallbackTest {
    private TwseService twseService;
    private FinMindClient finMindClient;
    private InstitutionalDataRepository institutionalDataRepository;
    private StockUniverseRepository stockUniverseRepository;
    private InstitutionalService institutionalService;

    @BeforeEach
    void setUp() {
        twseService = mock(TwseService.class);
        finMindClient = mock(FinMindClient.class);
        institutionalDataRepository = mock(InstitutionalDataRepository.class);
        stockUniverseRepository = mock(StockUniverseRepository.class);
        institutionalService = new InstitutionalService(
                twseService,
                finMindClient,
                institutionalDataRepository,
                stockUniverseRepository);
    }

    @Test
    void shouldUseTwseResultWithoutCallingFallback() {
        InstitutionalTrade twseTrade =
                new InstitutionalTrade("2026-07-22", 100, 200, 300, 1_000);
        when(twseService.fetchInstitutionalDataByDate("2330", LocalDate.of(2026, 7, 22)))
                .thenReturn(twseTrade);

        List<InstitutionalTrade> result =
                institutionalService.getDailyChipDataWithFallback("2330", "2026-07-22");

        assertEquals(List.of(twseTrade), result);
        verify(finMindClient, never()).fetchChipDataBackup(anyString(), anyString());
    }

    @Test
    void shouldFallbackToFinMindWhenTwseHasNoData() {
        when(twseService.fetchInstitutionalDataByDate("2330", LocalDate.of(2026, 7, 22)))
                .thenReturn(null);
        when(finMindClient.fetchChipDataBackup("2330", "2026-07-22"))
                .thenReturn(List.of(chip("2026-07-22", 700, 200)));

        List<InstitutionalTrade> result =
                institutionalService.getDailyChipDataWithFallback("2330", "2026-07-22");

        assertEquals(1, result.size());
        assertEquals("2026-07-22", result.get(0).getDate());
        assertEquals(0, result.get(0).getForeignBuy());
        assertEquals(700, result.get(0).getTrustBuy());
        assertEquals(0, result.get(0).getDealerBuy());
        assertEquals(900, result.get(0).getDailyVolume());
    }

    @Test
    void shouldReturnEmptyListWhenBothSourcesFail() {
        when(twseService.fetchInstitutionalDataByDate("2330", LocalDate.of(2026, 7, 22)))
                .thenThrow(new IllegalStateException("TWSE unavailable"));
        when(finMindClient.fetchChipDataBackup("2330", "2026-07-22"))
                .thenThrow(new IllegalStateException("FinMind unavailable"));

        List<InstitutionalTrade> result =
                institutionalService.getDailyChipDataWithFallback("2330", "2026-07-22");

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldSkipTwseWhenForceFinMindIsEnabled() {
        institutionalService.setForceFinMind(true);
        when(finMindClient.fetchChipDataBackup("2330", "2026-07-22"))
                .thenReturn(List.of(chip("2026-07-22", 500, 100)));

        List<InstitutionalTrade> result =
                institutionalService.getDailyChipDataWithFallback("2330", "2026-07-22");

        assertEquals(1, result.size());
        assertEquals(500, result.get(0).getTrustBuy());
        verify(twseService, never()).fetchInstitutionalDataByDate(
                anyString(),
                org.mockito.ArgumentMatchers.any(LocalDate.class));
    }

    @Test
    void shouldPersistAndReturnRecentFinMindFallbackData() {
        InstitutionalTrade converted =
                new InstitutionalTrade("2026-07-22", 0, 800, 0, 1_000);
        when(twseService.fetchRecentInstitutionalData("2330", 10))
                .thenThrow(new IllegalStateException("TWSE unavailable"));
        when(finMindClient.fetchChipDataByDateRange(
                org.mockito.ArgumentMatchers.eq("2330"),
                anyString(),
                anyString()))
                .thenReturn(List.of(chip("2026-07-22", 800, 200)));

        when(institutionalDataRepository.getRecentInstitutionalTrades("2330", 10))
                .thenReturn(List.of(), List.of(converted));

        List<InstitutionalTrade> result =
                institutionalService.getRecentInstitutionalTrades("2330", 10);

        assertEquals(List.of(converted), result);
        verify(institutionalDataRepository).saveInstitutionalTrades(
                eq("2330"),
                org.mockito.ArgumentMatchers.<List<InstitutionalTrade>>argThat(trades ->
                        trades.size() == 1
                                && trades.get(0).getTrustBuy() == 800
                                && trades.get(0).getDailyVolume() == 1_000));
    }

    @Test
    void shouldReadLargeHolderHistoryFromRepositoryWithoutFetching() {
        List<FinMindShareholdingData> cached = List.of(
                shareholding("2026-07-01"),
                shareholding("2026-07-08"),
                shareholding("2026-07-15"),
                shareholding("2026-07-22"));
        when(institutionalDataRepository.getLargeHolderShareholdingHistory("2330", 8))
                .thenReturn(cached);

        List<FinMindShareholdingData> result =
                institutionalService.getLargeHolderShareholdingHistory("2330", 8);

        assertEquals(cached, result);
        verify(finMindClient, never()).fetchLargeHolderShareholding(anyString(), anyString());
    }

    @Test
    void shouldRefreshLargeHolderDataForRepositorySymbols() {
        when(stockUniverseRepository.getAllSymbols()).thenReturn(List.of("2330", " ", "2454"));
        List<FinMindShareholdingData> rows = List.of(shareholding("2026-07-22"));
        when(finMindClient.fetchLargeHolderShareholding(eq("2330"), anyString())).thenReturn(rows);
        when(finMindClient.fetchLargeHolderShareholding(eq("2454"), anyString())).thenReturn(rows);
        when(institutionalDataRepository.saveLargeHolderShareholding("2330", rows)).thenReturn(1);
        when(institutionalDataRepository.saveLargeHolderShareholding("2454", rows)).thenReturn(1);

        int savedRows = institutionalService.refreshWeeklyLargeHolderShareholding();

        assertEquals(2, savedRows);
        verify(institutionalDataRepository).saveLargeHolderShareholding("2330", rows);
        verify(institutionalDataRepository).saveLargeHolderShareholding("2454", rows);
    }

    private FinMindChipData chip(String date, long buy, long sell) {
        FinMindChipData data = new FinMindChipData();
        data.setDate(date);
        data.setBuy(buy);
        data.setSell(sell);
        return data;
    }

    private FinMindShareholdingData shareholding(String date) {
        FinMindShareholdingData data = new FinMindShareholdingData();
        data.setDate(date);
        data.setHoldingFactor(15);
        return data;
    }
}
