package org.gtalent;

import org.gtalent.dto.FinMindNavData;
import org.gtalent.dto.RadarConfigDto;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketCrossScannerServiceTest {

    private final AdvancedFundamentalService advancedFundamentalService = mock(AdvancedFundamentalService.class);
    private final MarginAnalysisService marginAnalysisService = mock(MarginAnalysisService.class);
    private final KdAdvancedService kdAdvancedService = mock(KdAdvancedService.class);
    private final FinMindClient finMindClient = mock(FinMindClient.class);
    private final StockUniverseRepository stockUniverseRepository = mock(StockUniverseRepository.class);
    private final StockDataRepository stockDataRepository = mock(StockDataRepository.class);
    private final IndicatorCalculator indicatorCalculator = mock(IndicatorCalculator.class);
    private final MarketCrossScannerService service = new MarketCrossScannerService(
            advancedFundamentalService,
            marginAnalysisService,
            kdAdvancedService,
            finMindClient,
            stockUniverseRepository,
            stockDataRepository,
            indicatorCalculator
    );

    @Test
    void shouldResolveEtfFromStoredUniverseMetadata() {
        StockUniverseEntry entry = new StockUniverseEntry("00679B", "元大美債20年", "TWSE", "BOND_ETF", true, true);
        when(stockUniverseRepository.getStockUniverseEntry("00679B")).thenReturn(entry);

        boolean isEtf = ReflectionTestUtils.invokeMethod(service, "resolveIsEtf", "00679B");

        assertTrue(isEtf);
        verify(stockUniverseRepository).getStockUniverseEntry("00679B");
    }

    @Test
    void shouldBuildPriceRangeFromRepositoryHistory() {
        when(stockDataRepository.getLatestPrice("2330")).thenReturn(95.0);
        when(stockDataRepository.getFullHistory("2330", 252)).thenReturn(List.of(
                new StockDataPoint("2026-07-21", 90.0, 105.0, 88.0, 100.0),
                new StockDataPoint("2026-07-22", 92.0, 110.0, 85.0, 95.0)
        ));

        StockPriceInfo priceInfo = ReflectionTestUtils.invokeMethod(service, "buildPriceInfo", "2330", false);

        assertEquals(95.0, priceInfo.getCurrentPrice());
        assertEquals(110.0, priceInfo.getYearHigh());
        assertEquals(85.0, priceInfo.getYearLow());
        assertFalse(priceInfo.isNavAvailable());
        assertEquals("NOT_ETF", priceInfo.getNavSource());
    }

    @Test
    void shouldPreferPrimaryEtfPremiumResult() {
        when(stockDataRepository.getLatestPrice("0050")).thenReturn(49.0);
        when(stockDataRepository.getFullHistory("0050", 252)).thenReturn(List.of());
        when(finMindClient.fetchEtfDiscountPremium("0050"))
                .thenReturn(new FinMindClient.EtfPremiumResult(-0.012, true, "TWSE_TPEX_PRIMARY"));

        StockPriceInfo priceInfo = ReflectionTestUtils.invokeMethod(service, "buildPriceInfo", "0050", true);

        assertEquals(-0.012, priceInfo.getDiscountPremiumRatio(), 0.0001);
        assertTrue(priceInfo.isNavAvailable());
        assertEquals("TWSE_TPEX_PRIMARY", priceInfo.getNavSource());
        verify(finMindClient, never()).fetchNavData("0050", LocalDate.now().minusDays(45).toString());
    }

    @Test
    void shouldCalculateEtfPremiumFromNavWhenPrimaryIsUnavailable() {
        String startDate = LocalDate.now().minusDays(45).toString();
        FinMindNavData nav = new FinMindNavData();
        nav.setNav(50.0);
        when(stockDataRepository.getLatestPrice("0050")).thenReturn(49.0);
        when(stockDataRepository.getFullHistory("0050", 252)).thenReturn(List.of());
        when(finMindClient.fetchEtfDiscountPremium("0050"))
                .thenReturn(new FinMindClient.EtfPremiumResult(null, false, "NOT_AVAILABLE"));
        when(finMindClient.fetchNavData("0050", startDate)).thenReturn(List.of(nav));

        StockPriceInfo priceInfo = ReflectionTestUtils.invokeMethod(service, "buildPriceInfo", "0050", true);

        assertEquals(-0.02, priceInfo.getDiscountPremiumRatio(), 0.0001);
        assertTrue(priceInfo.isNavAvailable());
        assertEquals("FinMind_NAV", priceInfo.getNavSource());
    }

    @Test
    void shouldMarkEtfNavUnavailableWhenAllSourcesFail() {
        String startDate = LocalDate.now().minusDays(45).toString();
        when(stockDataRepository.getLatestPrice("0050")).thenReturn(49.0);
        when(stockDataRepository.getFullHistory("0050", 252)).thenReturn(List.of());
        when(finMindClient.fetchEtfDiscountPremium("0050"))
                .thenReturn(new FinMindClient.EtfPremiumResult(null, false, "NOT_AVAILABLE"));
        when(finMindClient.fetchNavData("0050", startDate)).thenReturn(List.of());

        StockPriceInfo priceInfo = ReflectionTestUtils.invokeMethod(service, "buildPriceInfo", "0050", true);

        assertEquals(0.0, priceInfo.getDiscountPremiumRatio());
        assertFalse(priceInfo.isNavAvailable());
        assertEquals("NOT_AVAILABLE", priceInfo.getNavSource());
    }

    @Test
    void shouldIgnoreResidualDiscountWhenNavIsUnavailable() {
        StockPriceInfo unavailableNav = new StockPriceInfo(
                49.0, 55.0, 45.0, -0.02, false, "NOT_AVAILABLE");

        StockDiagnosticResult result = ReflectionTestUtils.invokeMethod(
                service, "analyzeEtfStrategy", "0050", List.of(), List.of(), unavailableNav);

        assertEquals(0.0, result.getPremium());
        assertFalse(result.isNavAvailable());
        assertFalse(result.isPerfectMatch());
        assertEquals("隨大盤震盪中，定期定額區間", result.getStrategyTag());
    }

    @Test
    void shouldMarkEtfAsDiscountOpportunityBelowThreshold() {
        StockPriceInfo discounted = new StockPriceInfo(
                49.0, 55.0, 45.0, -0.0101, true, "TWSE_TPEX_PRIMARY");

        StockDiagnosticResult result = ReflectionTestUtils.invokeMethod(
                service, "analyzeEtfStrategy", "0050", List.of(), List.of(), discounted);

        assertEquals(-1.01, result.getPremium(), 0.0001);
        assertTrue(result.isPerfectMatch());
        assertEquals("🛍️ ETF 撿便宜：市價嚴重低於淨值（折價）", result.getStrategyTag());
    }

    @Test
    void shouldPrioritizeLowZoneKdGoldenCross() {
        List<KdData> kdHistory = List.of(
                new KdData("2026-07-24", 49.0, 10.0, 15.0),
                new KdData("2026-07-25", 50.0, 20.0, 18.0)
        );
        StockPriceInfo unavailableNav = new StockPriceInfo(
                50.0, 55.0, 45.0, 0.0, false, "NOT_AVAILABLE");

        StockDiagnosticResult result = ReflectionTestUtils.invokeMethod(
                service, "analyzeEtfStrategy", "0050", kdHistory, List.of(), unavailableNav);

        assertTrue(result.isPerfectMatch());
        assertEquals("🎯 ETF 點心時間：低檔超賣區黃金交叉", result.getStrategyTag());
    }

    @Test
    void shouldCalculateEtfMetricsFromRecentLatestValidNav() {
        String startDate = LocalDate.now().minusDays(45).toString();
        FinMindNavData older = new FinMindNavData();
        older.setNav(40.0);
        FinMindNavData latestValid = new FinMindNavData();
        latestValid.setNav(50.0);
        FinMindNavData invalid = new FinMindNavData();
        invalid.setNav(0.0);
        when(finMindClient.fetchNavData("0050", startDate))
                .thenReturn(List.of(older, latestValid, invalid));

        RadarConfigDto result = service.calculateEtfMetrics("0050", 51.0);

        assertEquals("0050", result.getSymbol());
        assertTrue(result.isEtf());
        assertEquals(50.0, result.getNetAssetValue());
        assertEquals(2.0, result.getPremium(), 0.0001);
        assertEquals("FinMind / 臺灣證券交易所", result.getNetAssetValueSource());
        verify(finMindClient).fetchNavData("0050", startDate);
    }

    @Test
    void shouldReturnNotAvailableWhenEtfNavIsInvalid() {
        String startDate = LocalDate.now().minusDays(45).toString();
        FinMindNavData invalid = new FinMindNavData();
        invalid.setNav(0.0);
        when(finMindClient.fetchNavData("0050", startDate)).thenReturn(List.of(invalid));

        RadarConfigDto result = service.calculateEtfMetrics("0050", 51.0);

        assertEquals(0.0, result.getNetAssetValue());
        assertEquals(0.0, result.getPremium());
        assertEquals("NOT_AVAILABLE", result.getNetAssetValueSource());
    }
}
