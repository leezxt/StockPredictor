package org.gtalent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstitutionalDataAccessServiceTest {
    @Mock
    private FinMindClient finMindClient;

    @Mock
    private TwseService twseService;

    @Mock
    private InstitutionalDataRepository institutionalDataRepository;

    @Test
    void shouldReturnCachedDayTradingHistoryWithoutCallingFinMind() {
        List<FinMindDayTradingData> cached = java.util.stream.IntStream.range(0, 10)
                .mapToObj(index -> new FinMindDayTradingData())
                .toList();
        when(institutionalDataRepository.getDayTradingHistory("2330", 30)).thenReturn(cached);

        List<FinMindDayTradingData> result =
                new DayTradingService(finMindClient, institutionalDataRepository)
                        .getDayTradingHistory(" 2330 ", 30);

        assertSame(cached, result);
        verify(finMindClient, never()).fetchFinMindDayTradingData(anyString(), anyString());
    }

    @Test
    void shouldPersistAndReloadDayTradingFallback() {
        List<FinMindDayTradingData> fetched = List.of(new FinMindDayTradingData());
        List<FinMindDayTradingData> reloaded = List.of(new FinMindDayTradingData());
        when(institutionalDataRepository.getDayTradingHistory("2330", 30))
                .thenReturn(List.of(), reloaded);
        when(finMindClient.fetchFinMindDayTradingData(
                org.mockito.ArgumentMatchers.eq("2330"), anyString())).thenReturn(fetched);

        List<FinMindDayTradingData> result =
                new DayTradingService(finMindClient, institutionalDataRepository)
                        .getDayTradingHistory("2330", 30);

        assertSame(reloaded, result);
        verify(institutionalDataRepository).saveDayTradingData("2330", fetched);
    }

    @Test
    void shouldReturnCachedInstitutionalTradesWithoutCallingExternalSources() {
        List<InstitutionalTrade> cached = List.of(new InstitutionalTrade());
        when(institutionalDataRepository.getRecentInstitutionalTrades("2330", 10))
                .thenReturn(cached);

        List<InstitutionalTrade> result =
                new EnhancedInstitutionalService(
                        twseService,
                        finMindClient,
                        institutionalDataRepository)
                        .getInstitutionalDataWithFallback("2330", 10);

        assertSame(cached, result);
        verify(twseService, never()).fetchRecentInstitutionalData(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt());
        verify(finMindClient, never()).fetchChipDataByDateRange(
                anyString(), anyString(), anyString());
    }
}
