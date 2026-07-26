package org.gtalent;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockAdviceControllerTest {
    @Test
    void shouldReadDivergencePricesThroughHistoryService() {
        InstitutionalService institutionalService = mock(InstitutionalService.class);
        StockDiagnosisService diagnosisService = mock(StockDiagnosisService.class);
        StockKdAnalysisService kdAnalysisService = mock(StockKdAnalysisService.class);
        StockHistoryService historyService = mock(StockHistoryService.class);
        IndicatorCalculator indicatorCalculator = mock(IndicatorCalculator.class);
        StockAdviceController controller = new StockAdviceController(
                institutionalService,
                diagnosisService,
                kdAnalysisService,
                historyService,
                indicatorCalculator);

        when(diagnosisService.diagnose("2330"))
                .thenReturn(Map.of("suggestion", "維持觀察。"));
        when(institutionalService.getRecentInstitutionalTrades("2330", 5))
                .thenReturn(List.of());
        KDInfo kdInfo = new KDInfo();
        kdInfo.setKdSeries(List.of());
        when(kdAnalysisService.analyze("2330", 60)).thenReturn(kdInfo);
        when(historyService.getRecentHistory("2330", 60))
                .thenReturn(List.of(new StockDataPoint("2026-07-22", 100, 1_000)));
        when(indicatorCalculator.calculateMACDSeries("2330", 60)).thenReturn(List.of());

        List<String> result = controller.getStockAdvices(" 2330 ");

        assertEquals(List.of(
                "維持觀察。",
                "MACD 死亡交叉，需提防回檔風險。"), result);
        verify(historyService).getRecentHistory("2330", 60);
    }
}
