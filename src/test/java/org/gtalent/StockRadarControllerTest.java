package org.gtalent;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockRadarControllerTest {
    @Test
    void shouldRejectBlankSymbol() {
        StockRadarController controller =
                new StockRadarController(mock(StockHistoryService.class), mock(RadarService.class));

        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> controller.getRadarScores(" "));

        assertEquals("symbol 不可為空白。", exception.getMessage());
    }

    @Test
    void shouldPrepareHistoryAndReturnRadarResult() {
        StockHistoryService historyService = mock(StockHistoryService.class);
        RadarService radarService = mock(RadarService.class);
        RadarScoreResult expected = new RadarScoreResult();
        when(radarService.calculateRadarScores("2330")).thenReturn(expected);
        StockRadarController controller = new StockRadarController(historyService, radarService);

        ResponseEntity<RadarScoreResult> response = controller.getRadarScores(" 2330 ");

        assertEquals(200, response.getStatusCode().value());
        assertSame(expected, response.getBody());
        verify(historyService).ensureRecentHistory("2330", 120);
    }

    @Test
    void shouldPreserveFriendlyErrorResponse() {
        StockHistoryService historyService = mock(StockHistoryService.class);
        RadarService radarService = mock(RadarService.class);
        when(radarService.calculateRadarScores("2330")).thenThrow(new IllegalStateException("測試錯誤"));
        StockRadarController controller = new StockRadarController(historyService, radarService);

        RadarScoreResult result = controller.getRadarScores("2330").getBody();

        assertEquals("ERROR", result.label);
        assertEquals("雷達資料暫時無法取得，請稍後再試：測試錯誤", result.conclusion);
    }
}
