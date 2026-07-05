package org.gtalent;

import org.gtalent.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SymbolProfileService {

    private final AIPredictionService aiPredictionService;
    private final EnhancedInstitutionalService enhancedInstitutionalService;
    private final VolumeAnalysisService volumeAnalysisService;
    private final DivergenceService divergenceService;
    private final FinMindClient finMindClient;
    private final TwseService twseService;

    @Autowired
    public SymbolProfileService(AIPredictionService aiPredictionService,
                                EnhancedInstitutionalService enhancedInstitutionalService,
                                VolumeAnalysisService volumeAnalysisService,
                                DivergenceService divergenceService,
                                FinMindClient finMindClient,
                                TwseService twseService) {
        this.aiPredictionService = aiPredictionService;
        this.enhancedInstitutionalService = enhancedInstitutionalService;
        this.volumeAnalysisService = volumeAnalysisService;
        this.divergenceService = divergenceService;
        this.finMindClient = finMindClient;
        this.twseService = twseService;
    }

    public SymbolProfileResult getProfile(String symbol) {
        SymbolProfileResult result = new SymbolProfileResult();
        result.setSymbol(symbol);
        result.setName(twseService.fetchStockName(symbol));

        // 1. 判斷資產類型
        StockUniverseEntry entry = DatabaseManager.getStockUniverseEntry(symbol);
        boolean isEtf = resolveIsEtf(symbol, entry);
        result.setEtf(isEtf);
        result.setAssetType(entry != null ? entry.getAssetType() : (isEtf ? "ETF" : "STOCK"));

        // 2. 即時價格資訊
        double currentPrice = DatabaseManager.getLatestPrice(symbol);
        result.setCurrentPrice(currentPrice);
        
        // 3. AI 預測 (Phase 4)
        result.setPrediction(aiPredictionService.predict(symbol));

        // 4. 高級技術指標 (Phase 1)
        result.setBollinger(IndicatorCalculator.calculateBollinger(symbol));
        result.setIchimoku(IndicatorCalculator.calculateIchimoku(symbol));
        result.setVwap(IndicatorCalculator.calculateVWAP(symbol));
        result.setRsi(IndicatorCalculator.calculateRSI(symbol, 14));
        
        // KD
        List<KDResult> kdList = IndicatorCalculator.calculateKD(symbol, 1);
        if (!kdList.isEmpty()) {
            Map<String, Double> kdMap = new HashMap<>();
            kdMap.put("k", kdList.get(0).getK());
            kdMap.put("d", kdList.get(0).getD());
            result.setKd(kdMap);
        }

        // MACD
        List<MACDResult> macdList = IndicatorCalculator.calculateMACDSeries(symbol, 1);
        if (!macdList.isEmpty()) {
            Map<String, Double> macdMap = new HashMap<>();
            macdMap.put("dif", macdList.get(0).dif);
            macdMap.put("dea", macdList.get(0).dea);
            macdMap.put("hist", macdList.get(0).histogram);
            result.setMacd(macdMap);
        }

        // 5. 籌碼與量能 (Phase 2 & 3)
        result.setInstitutionalSync(enhancedInstitutionalService.calculateInstitutionalSync(symbol, 10));
        result.setVolumeAnomaly(volumeAnalysisService.detectVolumeAnomaly(symbol));
        result.setRsiDivergence(divergenceService.detectRSIDivergence(symbol));
        result.setMacdDivergence(divergenceService.detectMACDDivergence(symbol));

        // 6. ETF 專屬數據
        if (isEtf) {
            try {
                FinMindClient.EtfPremiumResult etfRes = finMindClient.fetchEtfDiscountPremium(symbol);
                if (etfRes != null && etfRes.isNavAvailable()) {
                    // 如果 etfRes 沒提供淨值數值，我們根據市價與比例反算
                    double ratio = etfRes.getRatio(); // 這裡是比例，例如 0.01 表示溢價 1%
                    result.setPremiumDiscountPct(ratio * 100.0);
                    result.setNetAssetValue(currentPrice / (1 + ratio));
                    result.setNavAvailable(true);
                    result.setNavSource(etfRes.getSource());
                } else {
                    List<FinMindNavData> navRows = finMindClient.fetchNavData(symbol, LocalDate.now().minusDays(45).toString());
                    FinMindNavData latestNav = navRows.stream()
                            .filter(row -> row != null && row.getNav() > 0)
                            .max(Comparator.comparing(FinMindNavData::getDate, Comparator.nullsLast(Comparator.naturalOrder())))
                            .orElse(null);
                    if (latestNav != null) {
                        double nav = latestNav.getNav();
                        result.setNetAssetValue(nav);
                        result.setPremiumDiscountPct(nav > 0 ? ((currentPrice - nav) / nav) * 100.0 : 0.0);
                        result.setNavAvailable(true);
                        result.setNavSource("FinMind_NAV");
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
        }

        // 7. 綜合結論
        result.setOverallConclusion(result.getPrediction().getRecommendation());

        return result;
    }

    private boolean resolveIsEtf(String symbol, StockUniverseEntry entry) {
        if (entry != null) {
            return entry.isEtf()
                    || "BOND_ETF".equalsIgnoreCase(entry.getAssetType())
                    || "ETN".equalsIgnoreCase(entry.getAssetType());
        }
        return symbol.matches("^00[0-9A-Z]{2,5}$") || symbol.matches("^02\\d{3,5}$");
    }
}
