package org.gtalent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockControllerCharacterizationTest {
    @Mock
    private StockHistoryService stockHistoryService;

    @Mock
    private InstitutionalService institutionalService;

    @Mock
    private KdAdvancedService kdAdvancedService;

    @Mock
    private StockDiagnosisService stockDiagnosisService;

    @Mock
    private StockDataRepository stockDataRepository;

    @Mock
    private IndicatorCalculator indicatorCalculator;

    @Test
    void shouldRejectBlankSymbolForKdAnalysis() {
        StockKdController controller = new StockKdController(
                stockHistoryService,
                new StockKdAnalysisService(kdAdvancedService, stockDataRepository, indicatorCalculator));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.getKdDataEndpoint(" ", 120));

        assertEquals("symbol 不可為空白。", exception.getMessage());
    }

    @Test
    void shouldPreserveBullishDiagnosisContract() {
        StockDiagnosisService service = new StockDiagnosisService(
                stockHistoryService,
                stockDataRepository,
                indicatorCalculator);

        try (MockedStatic<IndicatorCalculator> indicators = mockStatic(IndicatorCalculator.class)) {
            when(stockDataRepository.getLatestPrice("2330")).thenReturn(105.126);
            when(stockDataRepository.calculateMA("2330", 5)).thenReturn(102.345);
            when(stockDataRepository.calculateMA("2330", 20)).thenReturn(98.765);
            when(indicatorCalculator.calculateRSI("2330", 14)).thenReturn(55.555);

            Map<String, Object> result = service.diagnose(" 2330 ");

            assertEquals(Map.ofEntries(
                    Map.entry("symbol", "2330"),
                    Map.entry("price", 105.13),
                    Map.entry("ma5", 102.35),
                    Map.entry("ma20", 98.77),
                    Map.entry("rsi", 55.56),
                    Map.entry("rsiStatus", "RSI 數值正常。"),
                    Map.entry("status", "多頭穩健"),
                    Map.entry("color", "#ff4d4d"),
                    Map.entry("suggestion", "趨勢與動能配合良好，建議續抱。"),
                    Map.entry("riskLevel", "低"),
                    Map.entry("icon", "📊"),
                    Map.entry("action", "趨勢與動能配合良好，建議續抱。")), result);
            verify(stockHistoryService).ensureRecentHistory("2330", 120);
        }
    }

    @Test
    void shouldPreserveOversoldBearishDiagnosisContract() {
        StockDiagnosisService service = new StockDiagnosisService(
                stockHistoryService,
                stockDataRepository,
                indicatorCalculator);

        try (MockedStatic<IndicatorCalculator> indicators = mockStatic(IndicatorCalculator.class)) {
            when(stockDataRepository.getLatestPrice("2330")).thenReturn(80.0);
            when(stockDataRepository.calculateMA("2330", 5)).thenReturn(85.0);
            when(stockDataRepository.calculateMA("2330", 20)).thenReturn(90.0);
            when(indicatorCalculator.calculateRSI("2330", 14)).thenReturn(20.0);

            Map<String, Object> result = service.diagnose("2330");

            assertEquals("空頭超買 (超賣)", result.get("status"));
            assertEquals("中", result.get("riskLevel"));
            assertEquals("💎 RSI 低檔 (超賣)，具備反彈潛力。", result.get("rsiStatus"));
            assertEquals("股價處於空頭，但 RSI 僅 20，已進入極度超賣區，隨時可能出現強烈反彈。",
                    result.get("suggestion"));
        }
    }

    @Test
    void shouldPreserveKdScoreSignalsIndicatorsAndDiagnosis() {
        StockKdAnalysisService service = new StockKdAnalysisService(
                kdAdvancedService,
                stockDataRepository,
                indicatorCalculator);
        StockKdController controller = new StockKdController(stockHistoryService, service);
        List<KDResult> kdSeries = List.of(
                new KDResult(42, 44),
                new KDResult(44, 45),
                new KDResult(46, 46),
                new KDResult(48, 47),
                new KDResult(50, 48));
        List<StockDataPoint> prices = List.of(
                new StockDataPoint("1", 96),
                new StockDataPoint("2", 97),
                new StockDataPoint("3", 98),
                new StockDataPoint("4", 99),
                new StockDataPoint("5", 100));
        when(kdAdvancedService.analyze(anyList()))
                .thenReturn(new KdAdvancedService.AnalysisResult(0, List.of("📊 KD 訊號中性，持續觀察")));

        try (MockedStatic<IndicatorCalculator> indicators = mockStatic(IndicatorCalculator.class)) {
            when(stockDataRepository.getRecentHistory("2330", 5)).thenReturn(prices);
            when(stockDataRepository.getLatestPrice("2330")).thenReturn(100.0);
            when(stockHistoryService.getLatestPrice("2330")).thenReturn(100.0);
            when(indicatorCalculator.calculateKD("2330", 5)).thenReturn(kdSeries);
            when(indicatorCalculator.calculateRSI("2330", 14)).thenReturn(50.0);
            when(indicatorCalculator.calculateMACD("2330")).thenReturn(new MACDResult(2, 1, 1));
            when(indicatorCalculator.calculateATR("2330", 14)).thenReturn(2.0);
            when(indicatorCalculator.calculateOBV("2330", 20)).thenReturn(0.15);
            when(indicatorCalculator.calculateMFI("2330", 14)).thenReturn(50.0);
            when(indicatorCalculator.calculateCMF("2330", 20)).thenReturn(0.1);
            when(indicatorCalculator.calculateCCI("2330", 20)).thenReturn(120.0);
            when(indicatorCalculator.calculateWilliamsR("2330", 14)).thenReturn(-50.0);
            when(indicatorCalculator.calculateAroonOscillator("2330", 25)).thenReturn(40.0);
            when(indicatorCalculator.calculateSuperTrendDirection("2330", 10, 3.0)).thenReturn(1);
            when(indicatorCalculator.calculateDonchianPosition("2330", 20)).thenReturn(0.95);

            ResponseEntity<KDInfo> response = controller.getKdDataEndpoint(" 2330 ", 5);
            KDInfo result = response.getBody();

            assertEquals(200, response.getStatusCode().value());
            assertNotNull(result);
            assertEquals(21, result.getAdvancedScore());
            assertEquals(9, result.getAdvancedSignals().size());
            assertFalse(result.isLowPassivation());
            assertFalse(result.isBottomDivergence());
            assertEquals(100.0, result.getLatestClosePrice());
            assertEquals(Map.ofEntries(
                    Map.entry("rsi", 50.0),
                    Map.entry("macdDif", 2.0),
                    Map.entry("macdDea", 1.0),
                    Map.entry("macdHistogram", 1.0),
                    Map.entry("atr", 2.0),
                    Map.entry("atrPct", 2.0),
                    Map.entry("obvStrength", 15.0),
                    Map.entry("mfi", 50.0),
                    Map.entry("cmf", 0.1),
                    Map.entry("cci", 120.0),
                    Map.entry("williamsR", -50.0),
                    Map.entry("aroonOscillator", 40.0),
                    Map.entry("superTrendDirection", 1),
                    Map.entry("superTrendLabel", "多頭"),
                    Map.entry("donchianPositionPct", 95.0)), result.getExtendedIndicators());
            assertEquals(
                    "KD 結構仍偏多，K 值站在 D 值之上，趨勢尚未被破壞。 RSI 50.00、MFI 50.00，動能位階落在中性區。 "
                            + "MACD 維持多方結構，柱狀體偏正，顯示趨勢仍在整理確認中。 "
                            + "SuperTrend 與資金流仍偏多，整理較像蓄勢而不是出貨。 "
                            + "ATR/收盤約 2.00%，波動相對收斂，適合耐心等確認。 "
                            + "OBV 強度約 15.00%，量價結構偏多。 "
                            + "Aroon Oscillator 約 40.00，趨勢動能仍在多方。 "
                            + "操作上，建議以箱體上緣突破或下緣失守作為確認點，不宜在盤整內提前重押。",
                    result.getDiagnosis());
            verify(stockHistoryService).ensureRecentHistory("2330", 60);
        }
    }

}
