package org.gtalent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockDataOperationsControllerTest {
    @Mock
    private BulkFetchService bulkFetchService;

    @Mock
    private ScheduledService scheduledService;

    @InjectMocks
    private StockDataOperationsController controller;

    @Test
    void shouldClampBulkFetchMonthsAndKeepStartedResponse() {
        when(bulkFetchService.startBulkBackfill(24)).thenReturn(true);

        Map<String, Object> response = controller.startBulkFetch(100);

        assertTrue((Boolean) response.get("started"));
        assertEquals(24, response.get("months"));
        assertEquals(
                "全市場批次補抓已啟動（背景執行），請定期查詢 /api/stocks/bulk-fetch/status 追蹤進度。",
                response.get("message"));
        verify(bulkFetchService).startBulkBackfill(24);
    }

    @Test
    void shouldKeepAlreadyRunningResponse() {
        when(bulkFetchService.startBulkBackfill(1)).thenReturn(false);

        Map<String, Object> response = controller.startBulkFetch(0);

        assertFalse((Boolean) response.get("started"));
        assertEquals(1, response.get("months"));
        assertEquals("已有補抓作業在執行中，請稍後再試。", response.get("message"));
    }

    @Test
    void shouldCalculateBulkFetchProgress() {
        when(bulkFetchService.getProgress())
                .thenReturn(new BulkFetchService.ProgressSnapshot("RUNNING", 3, 2, 25));

        Map<String, Object> response = controller.getBulkFetchStatus();

        assertEquals("RUNNING", response.get("status"));
        assertEquals(67, response.get("progressPct"));
        assertEquals(25, response.get("savedRows"));
    }

    @Test
    void shouldKeepTodayDataResponse() {
        when(bulkFetchService.bulkSaveTodayData()).thenReturn(123);

        Map<String, Object> response = controller.fetchTodayData();

        assertEquals(123, response.get("savedRows"));
        assertEquals("今日全市場資料更新完成，共寫入 123 筆。", response.get("message"));
    }

    @Test
    void shouldNormalizeSymbolForSingleStockBackup() {
        Map<String, Object> expected = Map.of("status", "QUEUED");
        when(scheduledService.startSingleStockBackup("2330")).thenReturn(expected);

        assertEquals(expected, controller.startSingleStockBackup(" 2330 "));
        verify(scheduledService).startSingleStockBackup("2330");
    }

    @Test
    void shouldDelegateBackupStatusesAndFullBackup() {
        Map<String, Object> singleStatus = Map.of("status", "RUNNING");
        Map<String, Object> fullStart = Map.of("status", "QUEUED");
        Map<String, Object> fullStatus = Map.of("status", "COMPLETED");
        when(scheduledService.getSingleStockBackupStatus("2330")).thenReturn(singleStatus);
        when(scheduledService.startFullBackup()).thenReturn(fullStart);
        when(scheduledService.getFullBackupStatus()).thenReturn(fullStatus);

        assertEquals(singleStatus, controller.getSingleStockBackupStatus(" 2330 "));
        assertEquals(fullStart, controller.startFullBackup());
        assertEquals(fullStatus, controller.getFullBackupStatus());
    }
}
