package org.gtalent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.gtalent.dto.RadarConfigDto;
import org.gtalent.dto.FinMindNavData;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 綜合交叉測試引擎：自動分流 ETF 與個股。
 *
 * <p>個股路徑：股價防禦濾網 → 一年區間相對基期 → 三率三升 × 融資 × KD 交叉。</p>
 * <p>ETF 路徑：折溢價（若有淨值資料）+ KD 低檔黃金交叉。</p>
 *
 * <p>提供兩個 entry point：
 * <ul>
 *   <li>{@link #crossTest(String, boolean, List, List, List, StockPriceInfo)} — 完整注入版（單元測試友善）</li>
 *   <li>{@link #crossTestBySymbol(String)} — 便捷版，從 DB / FinMind 自動取得所需資料</li>
 * </ul>
 */
@Service
public class MarketCrossScannerService {

    private static final Logger logger = Logger.getLogger(MarketCrossScannerService.class.getName());

    /** 高股價防禦濾網：大於此價位的個股直接跳過（中低價尋寶策略） */
    private static final double HIGH_PRICE_FILTER = 400.0;

    /** 基期上限：股價落在一年區間此分位數以上視為基期過高（個股） */
    private static final double BASE_POSITION_LIMIT = 0.40;

    /** 完美低基期分位：低於此分位才符合「隱藏版低基期珍珠股」 */
    private static final double PERFECT_BASE_POSITION = 0.25;

    /** ETF KD 黃金交叉判定門檻（K 值需低於此值才算低檔） */
    private static final double ETF_KD_LOW_THRESHOLD = 25.0;

    /** ETF 折價警示門檻（市價低於淨值 1% 以上視為撿便宜） */
    private static final double ETF_DISCOUNT_TRIGGER = -0.01;

    /** 財報查詢起始日期（涵蓋前季 Q4 與本季 Q1，與 StrategyController 一致） */
    private static final String FINANCIAL_QUERY_START_DATE = "2025-10-01";

    @Autowired
    private AdvancedFundamentalService advancedFundamentalService;

    @Autowired
    private MarginAnalysisService marginAnalysisService;

    @Autowired
    private KdAdvancedService kdAdvancedService;

    @Autowired
    private FinMindClient finMindClient;

    // ════════════════════════════════════════════════════════════
    //  主要 API
    // ════════════════════════════════════════════════════════════

    /**
     * 綜合交叉測試核心引擎。
     *
     * @param symbol         股票代碼
     * @param isEtf          是否為 ETF（由呼叫者依股票代碼長度或市場註記判定）
     * @param kdHistory      KD 歷史資料（升序，至少需 2 筆；KD 動能評分需 5 筆以上）
     * @param marginHistory  融資融券歷史（升序，至少需 5 筆才會計分）
     * @param q1Financials   Q1 財報原始資料（轉為 FinMindFinancialData 後評三率三升）
     * @param priceInfo      價格摘要：當前價、年高、年低、（ETF 用的）折溢價
     * @return 診斷結果，含跳過原因 / 完美匹配旗標 / 策略標籤 / 綜合分數
     */
    public StockDiagnosticResult crossTest(String symbol,
                                           boolean isEtf,
                                           List<KdData> kdHistory,
                                           List<FinMindMarginData> marginHistory,
                                           List<FinMindFinancialData> q1Financials,
                                           StockPriceInfo priceInfo) {

        StockDiagnosticResult result = new StockDiagnosticResult(symbol);

        if (priceInfo == null) {
            result.setSkip(true);
            result.setReason("缺少價格資訊");
            return result;
        }

        // ==== 分流邏輯 A：ETF 專屬路徑 ====
        if (isEtf) {
            return analyzeEtfStrategy(symbol, kdHistory, marginHistory, priceInfo);
        }

        // ==== 分流邏輯 B：一般個股 → 低基期財報黑馬篩選 ====

        // 1. 股價防禦濾網：避開絕對高股價（中低價尋寶策略）
        if (priceInfo.getCurrentPrice() > HIGH_PRICE_FILTER) {
            result.setSkip(true);
            result.setReason(String.format("股價過高 (>%.0f)，不符合中低價尋寶策略", HIGH_PRICE_FILTER));
            return result;
        }

        // 2. 空間基期檢驗（股價在過去一年的相對位置）
        double priceRange = priceInfo.getYearHigh() - priceInfo.getYearLow();
        if (priceRange <= 0) {
            result.setSkip(true);
            result.setReason("年度價格區間異常（high <= low），跳過基期判定");
            return result;
        }
        double currentPosition = (priceInfo.getCurrentPrice() - priceInfo.getYearLow()) / priceRange;
        result.setCurrentPosition(currentPosition);

        if (currentPosition > BASE_POSITION_LIMIT) {
            result.setSkip(true);
            result.setReason(String.format("相對基期過高 (%.0f%%)，股價已脫離底部區間", currentPosition * 100.0));
            return result;
        }

        // 3. 交叉比對：三率三升 × 融資籌碼 × KD 強度
        int fundamentalScore = advancedFundamentalService.checkTripleRiseScore(q1Financials);
        int marginScore = marginAnalysisService.calculateMarginScore(marginHistory);
        int technicalScore = safeAnalyzeKdStrength(kdHistory);

        // 低基期黑馬精髓：基本面拿分，且基期 <= 25%
        if (fundamentalScore > 0 && currentPosition <= PERFECT_BASE_POSITION) {
            result.setPerfectMatch(true);
            result.setStrategyTag("💎 隱藏版低基期財報珍珠股");
        } else if (fundamentalScore > 0) {
            result.setStrategyTag("📈 三率三升候選（基期略高）");
        } else if (marginScore > 0 || technicalScore > 0) {
            result.setStrategyTag("👀 籌碼/技術面亮點，等待基本面確認");
        } else {
            result.setStrategyTag("➖ 條件未達低基期珍珠股門檻");
        }
        result.setFinalScore(fundamentalScore + marginScore + technicalScore);

        return result;
    }

    /**
     * 便捷版：由 symbol 自動從 DB / FinMind 取得所有所需資料後執行交叉測試。
     */
    public StockDiagnosticResult crossTestBySymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            StockDiagnosticResult empty = new StockDiagnosticResult();
            empty.setSkip(true);
            empty.setReason("股票代碼為空");
            return empty;
        }
        String clean = symbol.trim();
        boolean isEtf = resolveIsEtf(clean);

        StockPriceInfo priceInfo = buildPriceInfo(clean, isEtf);
        List<KdData> kdHistory = buildKdHistory(clean, 60);
        List<FinMindMarginData> marginHistory = safeFetchMarginHistory(clean);
        List<FinMindFinancialData> q1Financials =
                isEtf ? Collections.emptyList() : safeFetchFinancials(clean);

        return crossTest(clean, isEtf, kdHistory, marginHistory, q1Financials, priceInfo);
    }

    // ════════════════════════════════════════════════════════════
    //  ETF 專屬路徑
    // ════════════════════════════════════════════════════════════

    /**
     * ETF 評級：不看三率三升（因為是一籃子標的），改看折溢價 + KD 低檔黃金交叉。
     */
    private StockDiagnosticResult analyzeEtfStrategy(String symbol,
                                                    List<KdData> kdHistory,
                                                    List<FinMindMarginData> marginHistory,
                                                    StockPriceInfo priceInfo) {
        StockDiagnosticResult result = new StockDiagnosticResult(symbol);
        result.setIsEtf(true);

        double discountPremiumRatio = priceInfo.getDiscountPremiumRatio();
        result.setPremium(discountPremiumRatio * 100.0);
        result.setNavAvailable(priceInfo.isNavAvailable());
        result.setNavSource(priceInfo.getNavSource());

        boolean isKdGoldenCross = false;
        double todayK = Double.NaN;
        if (kdHistory != null && kdHistory.size() >= 2) {
            KdData today = kdHistory.get(kdHistory.size() - 1);
            KdData yesterday = kdHistory.get(kdHistory.size() - 2);
            isKdGoldenCross =
                    yesterday.getKValue() < yesterday.getDValue()
                    && today.getKValue() > today.getDValue();
            todayK = today.getKValue();
        }

        if (!Double.isNaN(todayK) && todayK < ETF_KD_LOW_THRESHOLD && isKdGoldenCross) {
            result.setStrategyTag("🎯 ETF 點心時間：低檔超賣區黃金交叉");
            result.setPerfectMatch(true);
        } else if (discountPremiumRatio < ETF_DISCOUNT_TRIGGER) {
            result.setStrategyTag("🛍️ ETF 撿便宜：市價嚴重低於淨值（折價）");
            result.setPerfectMatch(true);
        } else {
            result.setStrategyTag("隨大盤震盪中，定期定額區間");
        }

        result.setFinalScore(
                safeAnalyzeKdStrength(kdHistory)
                + safeCalculateMarginScore(marginHistory)
        );

        return result;
    }

    // ════════════════════════════════════════════════════════════
    //  資料組裝 helpers（便捷版使用）
    // ════════════════════════════════════════════════════════════

    private boolean resolveIsEtf(String symbol) {
        StockUniverseEntry entry = DatabaseManager.getStockUniverseEntry(symbol);
        if (entry != null) {
            return entry.isEtf()
                    || "BOND_ETF".equalsIgnoreCase(entry.getAssetType())
                    || "ETN".equalsIgnoreCase(entry.getAssetType());
        }
        return symbol.matches("^00[0-9A-Z]{2,5}$") || symbol.matches("^02\\d{3,5}$");
    }

    private StockPriceInfo buildPriceInfo(String symbol, boolean isEtf) {
        double currentPrice = DatabaseManager.getLatestPrice(symbol);

        List<StockDataPoint> history = DatabaseManager.getFullHistory(symbol, 252);
        double yearHigh = 0.0;
        double yearLow = Double.MAX_VALUE;
        for (StockDataPoint p : history) {
            double high = p.h > 0 ? p.h : p.c;
            double low  = p.l > 0 ? p.l : p.c;
            if (high > yearHigh) yearHigh = high;
            if (low > 0 && low < yearLow) yearLow = low;
        }
        if (yearLow == Double.MAX_VALUE) yearLow = 0.0;

        if (!isEtf) {
            return new StockPriceInfo(currentPrice, yearHigh, yearLow, 0.0, false, "NOT_ETF");
        }

        FinMindClient.EtfPremiumResult premiumResult = finMindClient.fetchEtfDiscountPremium(symbol);
        if (premiumResult != null && premiumResult.isNavAvailable() && premiumResult.getRatio() != null) {
            return new StockPriceInfo(
                    currentPrice,
                    yearHigh,
                    yearLow,
                    premiumResult.getRatio(),
                    true,
                    premiumResult.getSource()
            );
        }

        // Fallback：若折溢價 dataset 不可用，改用 ETF NAV 與最新市價自行推估折溢價
        Double fallbackPremiumRatio = calculatePremiumRatioFromNav(symbol, currentPrice);
        if (fallbackPremiumRatio != null) {
            return new StockPriceInfo(currentPrice, yearHigh, yearLow, fallbackPremiumRatio, true, "FinMind_NAV");
        }

        return new StockPriceInfo(currentPrice, yearHigh, yearLow, 0.0, false, "NOT_AVAILABLE");
    }

    private Double calculatePremiumRatioFromNav(String symbol, double currentPrice) {
        if (currentPrice <= 0) {
            return null;
        }
        try {
            String startDate = LocalDate.now().minusDays(45).toString();
            List<FinMindNavData> navList = finMindClient.fetchNavData(symbol, startDate);
            if (navList == null || navList.isEmpty()) {
                return null;
            }

            Double latestNav = null;
            for (int i = navList.size() - 1; i >= 0; i--) {
                double nav = navList.get(i).getNav();
                if (nav > 0) {
                    latestNav = nav;
                    break;
                }
            }
            if (latestNav == null || latestNav <= 0) {
                return null;
            }
            return (currentPrice - latestNav) / latestNav;
        } catch (Exception e) {
            logger.fine("ETF NAV fallback 折溢價推估失敗(" + symbol + "): " + e.getMessage());
            return null;
        }
    }

    private List<KdData> buildKdHistory(String symbol, int limit) {
        List<KDResult> kdSeries = IndicatorCalculator.calculateKD(symbol, limit);
        if (kdSeries == null || kdSeries.isEmpty()) {
            return Collections.emptyList();
        }
        List<Double> closes = DatabaseManager.getRecentHistory(symbol, limit).stream()
                .map(dp -> dp.c > 0 ? dp.c : dp.price)
                .collect(Collectors.toList());

        int size = Math.min(kdSeries.size(), closes.size());
        int kdStart = kdSeries.size() - size;
        int closeStart = closes.size() - size;
        List<KdData> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            KDResult kd = kdSeries.get(kdStart + i);
            out.add(new KdData("", closes.get(closeStart + i), kd.getK(), kd.getD()));
        }
        return out;
    }

    private List<FinMindMarginData> safeFetchMarginHistory(String symbol) {
        try {
            return finMindClient.fetchMarginHistory(symbol, 10);
        } catch (Exception e) {
            logger.fine("⚠️ 融資融券資料取得失敗 " + symbol + ": " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<FinMindFinancialData> safeFetchFinancials(String symbol) {
        try {
            List<FinMindRawFinancialRow> rawRows =
                    finMindClient.fetchFinancialStatements(symbol, FINANCIAL_QUERY_START_DATE);
            return convertToFinancialData(rawRows);
        } catch (Exception e) {
            logger.fine("⚠️ 財報資料取得失敗 " + symbol + ": " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private static List<FinMindFinancialData> convertToFinancialData(List<FinMindRawFinancialRow> rawRows) {
        if (rawRows == null) return Collections.emptyList();
        return rawRows.stream()
                .map(row -> new FinMindFinancialData(
                        row.getDate(), row.getStockId(), row.getType(), row.getValue()))
                .collect(Collectors.toList());
    }

    private int safeAnalyzeKdStrength(List<KdData> kdHistory) {
        if (kdHistory == null || kdHistory.size() < 5) return 0;
        try {
            return kdAdvancedService.analyzeKdStrength(kdHistory);
        } catch (Exception e) {
            logger.fine("⚠️ KD 強度分析失敗: " + e.getMessage());
            return 0;
        }
    }

    private int safeCalculateMarginScore(List<FinMindMarginData> marginHistory) {
        if (marginHistory == null || marginHistory.size() < 5) return 0;
        try {
            return marginAnalysisService.calculateMarginScore(marginHistory);
        } catch (Exception e) {
            logger.fine("⚠️ 融資評分失敗: " + e.getMessage());
            return 0;
        }
    }

    /**
     * ETF 折溢價計算方法
     *
     * @param symbol 股票代碼 (ETF 代碼，如 0050、0056)
     * @param currentPrice 當前市價
     * @return 含淨值、折溢價的 RadarConfigDto 物件
     */
    public RadarConfigDto calculateEtfMetrics(String symbol, double currentPrice) {
        RadarConfigDto dto = new RadarConfigDto();
        dto.setSymbol(symbol);
        dto.setEtf(true);

        // 1. 從 FinMind API 獲取最新的 ETF 淨值資料 (TaiwanETFNavigation Dataset)
        //    使用前一日日期確保有有效淨值數據
        String navQueryDate = "2026-05-27"; // 應該用 LocalDate.now().minusDays(1)
        List<FinMindNavData> navList = finMindClient.fetchNavData(symbol, navQueryDate);

        if (navList != null && !navList.isEmpty()) {
            // 取得最新一日的淨值
            double latestNav = navList.get(navList.size() - 1).getNav();

            // 2. 核心量化公式：計算折溢價 (Premium / Discount)
            // 折溢價比例 = (市價 - 淨值) / 淨值 * 100
            // 負值表折價，正值表溢價
            double premiumRatio = ((currentPrice - latestNav) / latestNav) * 100;

            // 3. 填充 DTO 回傳給前端 dashboard.html
            dto.setNetAssetValue(latestNav);
            dto.setPremium(premiumRatio);
            dto.setNetAssetValueSource("FinMind / 臺灣證券交易所");

            logger.fine(String.format("ETF %s - Market: %.2f | NAV: %.2f | Premium: %.2f%%",
                    symbol, currentPrice, latestNav, premiumRatio));
        } else {
            // 防禦降級處理：淨值資料不可得
            dto.setNetAssetValue(0);
            dto.setPremium(0);
            dto.setNetAssetValueSource("NOT_AVAILABLE");
            logger.warning("⚠️ ETF " + symbol + " 無法取得淨值資料");
        }

        return dto;
    }
}
