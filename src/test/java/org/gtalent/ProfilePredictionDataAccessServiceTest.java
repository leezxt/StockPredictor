package org.gtalent;

import org.gtalent.dto.BollingerResult;
import org.gtalent.dto.IchimokuResult;
import org.gtalent.dto.PredictionResult;
import org.gtalent.dto.SymbolProfileResult;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfilePredictionDataAccessServiceTest {

    @Test
    void shouldBuildStockProfileFromRepositoryMetadataAndPrice() {
        AIPredictionService aiPredictionService = mock(AIPredictionService.class);
        EnhancedInstitutionalService institutionalService = mock(EnhancedInstitutionalService.class);
        VolumeAnalysisService volumeAnalysisService = mock(VolumeAnalysisService.class);
        DivergenceService divergenceService = mock(DivergenceService.class);
        FinMindClient finMindClient = mock(FinMindClient.class);
        TwseService twseService = mock(TwseService.class);
        StockUniverseRepository universeRepository = mock(StockUniverseRepository.class);
        StockDataRepository stockDataRepository = mock(StockDataRepository.class);
        IndicatorCalculator indicatorCalculator = mock(IndicatorCalculator.class);
        SymbolProfileService service = new SymbolProfileService(
                aiPredictionService,
                institutionalService,
                volumeAnalysisService,
                divergenceService,
                finMindClient,
                twseService,
                universeRepository,
                stockDataRepository,
                indicatorCalculator
        );
        StockUniverseEntry entry =
                new StockUniverseEntry("2330", "台積電", "TWSE", "STOCK", false, true);
        PredictionResult prediction = new PredictionResult();
        prediction.setRecommendation("持有");
        when(twseService.fetchStockName("2330")).thenReturn("台積電");
        when(universeRepository.getStockUniverseEntry("2330")).thenReturn(entry);
        when(stockDataRepository.getLatestPrice("2330")).thenReturn(1_125.0);
        when(aiPredictionService.predict("2330")).thenReturn(prediction);
        when(indicatorCalculator.calculateMACDSeries("2330", 1)).thenReturn(List.of());
        when(indicatorCalculator.calculateKD("2330", 1)).thenReturn(List.of());

        SymbolProfileResult result = service.getProfile("2330");

        assertEquals("STOCK", result.getAssetType());
        assertFalse(result.isEtf());
        assertEquals(1_125.0, result.getCurrentPrice());

        verify(universeRepository).getStockUniverseEntry("2330");
        verify(stockDataRepository).getLatestPrice("2330");
    }

    @Test
    void shouldUseRepositoryPriceWhenCalculatingTechnicalScore() {
        EnhancedInstitutionalService institutionalService = mock(EnhancedInstitutionalService.class);
        VolumeAnalysisService volumeAnalysisService = mock(VolumeAnalysisService.class);
        DivergenceService divergenceService = mock(DivergenceService.class);
        StockDataRepository stockDataRepository = mock(StockDataRepository.class);
        IndicatorCalculator indicatorCalculator = mock(IndicatorCalculator.class);
        AIPredictionService service = new AIPredictionService(
                institutionalService,
                volumeAnalysisService,
                divergenceService,
                stockDataRepository,
                indicatorCalculator
        );
        BollingerResult bollinger = mock(BollingerResult.class);
        IchimokuResult ichimoku = mock(IchimokuResult.class);
        when(stockDataRepository.getLatestPrice("2330")).thenReturn(100.0);
        when(indicatorCalculator.calculateBollinger("2330")).thenReturn(bollinger);
        when(indicatorCalculator.calculateIchimoku("2330")).thenReturn(ichimoku);

        double score = ReflectionTestUtils.invokeMethod(
                service,
                "calculateTechnicalScore",
                "2330",
                new ArrayList<String>()
        );

        assertEquals(50.0, score);
        verify(stockDataRepository).getLatestPrice("2330");
    }
}
