package org.gtalent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockScanControllerTest {
    @Mock
    private ScannerService scannerService;

    @Mock
    private ScanTaskService scanTaskService;

    @InjectMocks
    private StockScanController controller;

    @Test
    void shouldNormalizeSynchronousScanRequest() {
        when(scannerService.scanAllStocks(2000, "ETF", "TWSE"))
                .thenReturn(Collections.emptyList());

        List<ScannedResult> result = controller.scanMarket(2500, " etf ", " twse ");

        assertEquals(Collections.emptyList(), result);
        verify(scannerService).scanAllStocks(2000, "ETF", "TWSE");
    }

    @Test
    void shouldStartAsynchronousScanWithExistingResponseContract() {
        when(scanTaskService.startScan(1, null, null)).thenReturn("task-1");

        Map<String, Object> response = controller.startScanTask(0, " ", null);

        assertEquals("task-1", response.get("taskId"));
        assertEquals("QUEUED", response.get("status"));
        assertEquals("掃描工作已排入佇列", response.get("message"));
        verify(scanTaskService).startScan(1, null, null);
    }

    @Test
    void shouldReturnNotFoundForUnknownScanTask() {
        when(scanTaskService.getTask("missing")).thenReturn(null);

        ResponseEntity<ScanTaskService.ScanTaskSnapshot> response = controller.getScanTask("missing");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void shouldDelegateMaPatternScan() {
        when(scannerService.scanMAPatterns("BULLISH")).thenReturn(Collections.emptyList());

        assertEquals(Collections.emptyList(), controller.scanMAPatterns("BULLISH"));
        verify(scannerService).scanMAPatterns("BULLISH");
    }

    @Test
    void shouldKeepTrustLockedLimitContract() {
        when(scannerService.getTopTrustStocks(20)).thenReturn(Collections.emptyList());

        assertEquals(Collections.emptyList(), controller.getTrustLocked(100));
        verify(scannerService).getTopTrustStocks(20);
    }
}
