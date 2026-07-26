package org.gtalent;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduledServiceTest {
    private final ScannerService scannerService = mock(ScannerService.class);
    private final MarketBreadthService marketBreadthService = mock(MarketBreadthService.class);
    private final FundamentalService fundamentalService = mock(FundamentalService.class);
    private final InstitutionalService institutionalService = mock(InstitutionalService.class);
    private final BulkFetchService bulkFetchService = mock(BulkFetchService.class);
    private final TwseService twseService = mock(TwseService.class);
    private final StockUniverseRepository stockUniverseRepository =
            mock(StockUniverseRepository.class);
    private final AppMetaRepository appMetaRepository = mock(AppMetaRepository.class);

    @Test
    void shouldSkipStartupBackupWhenSchedulingIsDisabled() {
        ScheduledService service = createService();
        ReflectionTestUtils.setField(service, "schedulingEnabled", false);

        service.onStartup();

        verify(appMetaRepository, never()).isMetaFlagSet("initial_backup_done");
        verify(twseService, never()).syncMarketUniverse();
    }

    @Test
    void shouldSkipStartupBackupWhenMetaFlagIsAlreadySet() {
        when(appMetaRepository.isMetaFlagSet("initial_backup_done")).thenReturn(true);

        ScheduledService service = createService();
        ReflectionTestUtils.setField(service, "schedulingEnabled", true);
        service.onStartup();

        verify(twseService, never()).syncMarketUniverse();
        verify(twseService, never()).fetchYearlyData(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void shouldCompleteStartupBackupAndPersistMetaFlag() {
        when(appMetaRepository.isMetaFlagSet("initial_backup_done")).thenReturn(false);
        when(twseService.syncMarketUniverse()).thenReturn(10);
        when(stockUniverseRepository.getStockUniverseCount()).thenReturn(20);

        ScheduledService service = createService();
        ReflectionTestUtils.setField(service, "schedulingEnabled", true);
        service.onStartup();

        verify(twseService).fetchYearlyData("2330", 2026, 1);
        verify(marketBreadthService).calculateMarketBreadth();
        verify(fundamentalService).refreshLatestRevenueData();
        verify(appMetaRepository).setMetaFlag("initial_backup_done", "true");
        verify(stockUniverseRepository).getStockUniverseCount();
    }

    private ScheduledService createService() {
        return new ScheduledService(
                scannerService,
                marketBreadthService,
                fundamentalService,
                institutionalService,
                bulkFetchService,
                twseService,
                stockUniverseRepository,
                appMetaRepository);
    }
}
