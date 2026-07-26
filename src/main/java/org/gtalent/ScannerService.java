package org.gtalent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ScannerService {
    private final IndicatorCalculator indicatorCalculator;
    private final StockUniverseRepository stockUniverseRepository;
    private final StockDataRepository stockDataRepository;
    private final InstitutionalDataRepository institutionalDataRepository;

    @Value("${scanner.volume-churn.ratio-threshold:0.45}")
    private double volumeChurnRatioThreshold;

    @Value("${scanner.volume-churn.score-penalty:8}")
    private int volumeChurnScorePenalty;

    @Value("${scanner.news-reaction.enabled:true}")
    private boolean newsReactionEnabled;

    @Value("${scanner.strategy.news-reaction-multiplier:0.10}")
    private double newsReactionMultiplier;

    @Value("${scanner.news-reaction.cache-ms:600000}")
    private long newsReactionCacheMs;

    @Value("${scanner.adx.period:14}")
    private int adxPeriod;

    @Value("${scanner.adx.threshold.moderate:20}")
    private double adxModerateThreshold;

    @Value("${scanner.adx.threshold.strong:28}")
    private double adxStrongThreshold;

    @Value("${scanner.adx.bonus.moderate:6}")
    private int adxModerateBonus;

    @Value("${scanner.adx.bonus.strong:12}")
    private int adxStrongBonus;

    @Value("${scanner.atr.period:14}")
    private int atrPeriod;

    @Value("${scanner.atr.ratio.healthy:0.035}")
    private double atrHealthyRatio;

    @Value("${scanner.atr.ratio.overheat:0.075}")
    private double atrOverheatRatio;

    @Value("${scanner.atr.bonus.healthy:6}")
    private int atrHealthyBonus;

    @Value("${scanner.atr.penalty.overheat:8}")
    private int atrOverheatPenalty;

    @Value("${scanner.obv.lookback-days:15}")
    private int obvLookbackDays;

    @Value("${scanner.obv.threshold.moderate:0.08}")
    private double obvModerateThreshold;

    @Value("${scanner.obv.threshold.strong:0.18}")
    private double obvStrongThreshold;

    @Value("${scanner.obv.bonus.moderate:4}")
    private int obvModerateBonus;

    @Value("${scanner.obv.bonus.strong:8}")
    private int obvStrongBonus;

    @Value("${scanner.obv.penalty.weak:5}")
    private int obvWeakPenalty;

    @Value("${scanner.mfi.period:14}")
    private int mfiPeriod;

    @Value("${scanner.mfi.bonus.healthy:6}")
    private int mfiHealthyBonus;

    @Value("${scanner.mfi.penalty.overbought:7}")
    private int mfiOverboughtPenalty;

    @Value("${scanner.mfi.bonus.oversold:3}")
    private int mfiOversoldBonus;

    @Value("${scanner.supertrend.period:10}")
    private int superTrendPeriod;
    @Value("${scanner.supertrend.multiplier:3.0}")
    private double superTrendMultiplier;
    @Value("${scanner.supertrend.bonus.bullish:8}")
    private int superTrendBullishBonus;
    @Value("${scanner.supertrend.penalty.bearish:8}")
    private int superTrendBearishPenalty;

    @Value("${scanner.donchian.period:20}")
    private int donchianPeriod;
    @Value("${scanner.donchian.bonus.breakout:8}")
    private int donchianBreakoutBonus;
    @Value("${scanner.donchian.penalty.breakdown:8}")
    private int donchianBreakdownPenalty;

    @Value("${scanner.cmf.period:20}")
    private int cmfPeriod;
    @Value("${scanner.cmf.threshold.positive:0.08}")
    private double cmfPositiveThreshold;
    @Value("${scanner.cmf.threshold.negative:-0.08}")
    private double cmfNegativeThreshold;
    @Value("${scanner.cmf.bonus.positive:6}")
    private int cmfPositiveBonus;
    @Value("${scanner.cmf.penalty.negative:6}")
    private int cmfNegativePenalty;

    @Value("${scanner.cci.period:20}")
    private int cciPeriod;
    @Value("${scanner.cci.bonus.strong:6}")
    private int cciStrongBonus;
    @Value("${scanner.cci.penalty.weak:6}")
    private int cciWeakPenalty;

    @Value("${scanner.williamsr.period:14}")
    private int williamsRPeriod;
    @Value("${scanner.williamsr.bonus.rebound:4}")
    private int williamsRReboundBonus;
    @Value("${scanner.williamsr.penalty.extreme:5}")
    private int williamsRExtremePenalty;

    @Value("${scanner.aroon.period:25}")
    private int aroonPeriod;
    @Value("${scanner.aroon.bonus.strong:6}")
    private int aroonStrongBonus;
    @Value("${scanner.aroon.penalty.weak:6}")
    private int aroonWeakPenalty;

    @Value("${scanner.industry-rs.lookback-days:20}")
    private int industryRsLookbackDays;

    @Value("${scanner.industry-rs.min-group-size:6}")
    private int industryRsMinGroupSize;

    @Value("${scanner.industry-rs.bonus.top:10}")
    private int industryRsBonusTop;

    @Value("${scanner.industry-rs.bonus.upper-mid:6}")
    private int industryRsBonusUpperMid;

    @Value("${scanner.industry-rs.penalty.lower-mid:4}")
    private int industryRsPenaltyLowerMid;

    @Value("${scanner.industry-rs.penalty.bottom:8}")
    private int industryRsPenaltyBottom;

    @Value("${scanner.strategy.stock.trend-multiplier:1.00}")
    private double stockTrendMultiplier;
    @Value("${scanner.strategy.stock.momentum-multiplier:1.00}")
    private double stockMomentumMultiplier;
    @Value("${scanner.strategy.stock.volume-multiplier:1.00}")
    private double stockVolumeMultiplier;
    @Value("${scanner.strategy.stock.relative-strength-multiplier:1.00}")
    private double stockRelativeStrengthMultiplier;
    @Value("${scanner.strategy.stock.institutional-multiplier:1.00}")
    private double stockInstitutionalMultiplier;

    @Value("${scanner.strategy.etf.trend-multiplier:1.05}")
    private double etfTrendMultiplier;
    @Value("${scanner.strategy.etf.momentum-multiplier:0.85}")
    private double etfMomentumMultiplier;
    @Value("${scanner.strategy.etf.volume-multiplier:1.00}")
    private double etfVolumeMultiplier;
    @Value("${scanner.strategy.etf.relative-strength-multiplier:1.05}")
    private double etfRelativeStrengthMultiplier;
    @Value("${scanner.strategy.etf.institutional-multiplier:0.95}")
    private double etfInstitutionalMultiplier;
    @Value("${scanner.strategy.etf.liquidity-multiplier:1.10}")
    private double etfLiquidityMultiplier;
    @Value("${scanner.strategy.etf.premium-multiplier:1.15}")
    private double etfPremiumMultiplier;
    @Value("${scanner.strategy.etf.tracking-error-multiplier:1.10}")
    private double etfTrackingErrorMultiplier;
    @Value("${scanner.strategy.etf.fund-flow-multiplier:1.00}")
    private double etfFundFlowMultiplier;
    @Value("${scanner.strategy.etf.tracking-difference-multiplier:1.00}")
    private double etfTrackingDifferenceMultiplier;

    @Value("${scanner.strategy.bond-etf.trend-multiplier:1.10}")
    private double bondEtfTrendMultiplier;
    @Value("${scanner.strategy.bond-etf.momentum-multiplier:0.55}")
    private double bondEtfMomentumMultiplier;
    @Value("${scanner.strategy.bond-etf.volume-multiplier:0.85}")
    private double bondEtfVolumeMultiplier;
    @Value("${scanner.strategy.bond-etf.relative-strength-multiplier:1.10}")
    private double bondEtfRelativeStrengthMultiplier;
    @Value("${scanner.strategy.bond-etf.institutional-multiplier:0.75}")
    private double bondEtfInstitutionalMultiplier;

    @Value("${scanner.strategy.etn.trend-multiplier:0.90}")
    private double etnTrendMultiplier;
    @Value("${scanner.strategy.etn.momentum-multiplier:0.75}")
    private double etnMomentumMultiplier;
    @Value("${scanner.strategy.etn.volume-multiplier:1.10}")
    private double etnVolumeMultiplier;
    @Value("${scanner.strategy.etn.relative-strength-multiplier:0.90}")
    private double etnRelativeStrengthMultiplier;
    @Value("${scanner.strategy.etn.institutional-multiplier:0.80}")
    private double etnInstitutionalMultiplier;

    private final ScanHistoryRepository historyRepository;

    private final InstitutionalService institutionalService;

    private final NewsReactionService newsReactionService;

    private final ScoreEngine scoreEngine;

    private final FinMindClient finMindClient;

    private final Map<String, CachedNewsReactionScore> newsReactionCache = new ConcurrentHashMap<>();

    private final TwseService twseService;

    public ScannerService(TwseService twseService,
                          IndicatorCalculator indicatorCalculator,
                          StockUniverseRepository stockUniverseRepository,
                          StockDataRepository stockDataRepository,
                          InstitutionalDataRepository institutionalDataRepository,
                          ScanHistoryRepository historyRepository,
                          InstitutionalService institutionalService,
                          NewsReactionService newsReactionService,
                          ScoreEngine scoreEngine,
                          FinMindClient finMindClient) {
        this.twseService = twseService;
        this.indicatorCalculator = indicatorCalculator;
        this.stockUniverseRepository = stockUniverseRepository;
        this.stockDataRepository = stockDataRepository;
        this.institutionalDataRepository = institutionalDataRepository;
        this.historyRepository = historyRepository;
        this.institutionalService = institutionalService;
        this.newsReactionService = newsReactionService;
        this.scoreEngine = scoreEngine;
        this.finMindClient = finMindClient;
    }

    /** 掃描時要求的最低 STOCK_DATA 筆數（用於 MA20 計算 + 5 日前斜率） */
    private static final int SCAN_MIN_HISTORY_DAYS = 25;
    /** 主要評分門檻 */
    private static final int THRESHOLD_HIGH = 60;
    /** 市場偏弱時降低的次級門檻 */
    private static final int THRESHOLD_MED  = 45;
    /** 最低兜底門檻（保證不回傳空陣列） */
    private static final int THRESHOLD_LOW  = 30;

    /**
     * 掃描特定均線型態
     */
    public List<ScannedResult> scanMAPatterns(String type) {
        List<String> allSymbols = stockUniverseRepository.getAllSymbolsWithData(65, null, null);
        List<ScannedResult> results = new ArrayList<>();

        for (String symbol : allSymbols) {
            double price = stockDataRepository.getLatestPrice(symbol);
            double ma5 = stockDataRepository.calculateMA(symbol, 5);
            double ma10 = stockDataRepository.calculateMA(symbol, 10);
            double ma20 = stockDataRepository.calculateMA(symbol, 20);
            double ma60 = stockDataRepository.calculateMA(symbol, 60);

            if (ma5 <= 0 || ma10 <= 0 || ma20 <= 0 || ma60 <= 0) continue;

            boolean match = false;
            String note = "";

            if ("BULLISH".equalsIgnoreCase(type)) {
                // 多頭排列: 價格 > MA5 > MA10 > MA20 > MA60
                if (price > ma5 && ma5 > ma10 && ma10 > ma20 && ma20 > ma60) {
                    match = true;
                    note = "多頭排列";
                }
            } else if ("ENTANGLE".equalsIgnoreCase(type)) {
                // 均線糾結: MA5, MA10, MA20 差距在 3% 以內，且價格在 MA20 之上
                double maxMA = Math.max(ma5, Math.max(ma10, ma20));
                double minMA = Math.min(ma5, Math.min(ma10, ma20));
                double diff = (maxMA - minMA) / minMA;
                if (diff <= 0.03 && price > maxMA) {
                    match = true;
                    note = "均線糾結突破";
                }
            }

            if (match) {
                ScannedResult sr = new ScannedResult(
                        symbol,
                        0,
                        0,
                        note,
                        price,
                        1.0,
                        false,
                        "UNKNOWN",
                        "",
                        "均線型態掃描",
                        note
                );
                results.add(sr);
            }
        }

        return results;
    }

    public List<ScannedResult> scanAllStocks() {
        return scanAllStocks(10, null, null);
    }

    public List<ScannedResult> scanAllStocks(int topN) {
        return scanAllStocks(topN, null, null);
    }

    public List<ScannedResult> scanAllStocks(int topN, String assetType, String market) {
        return scanAllStocks(topN, assetType, market, (completed, total, symbol) -> {
        });
    }

    public List<ScannedResult> scanAllStocks(int topN,
                                             String assetType,
                                             String market,
                                             ScanProgressListener progressListener) {
        // ── 只掃描在 STOCK_DATA 中有足夠歷史資料的代碼 ──────────────────
        // getAllSymbols 可能回傳數千個 STOCK_UNIVERSE 成員，但大多數沒有 STOCK_DATA。
        // getAllSymbolsWithData 藉由 HAVING COUNT(*) >= 25 在 DB 層過濾，大幅減少無效計算。
        List<String> allSymbols = stockUniverseRepository.getAllSymbolsWithData(
                SCAN_MIN_HISTORY_DAYS, assetType, market);
        ScanProgressListener safeProgressListener = progressListener == null
                ? (completed, total, symbol) -> {
                }
                : progressListener;
        safeProgressListener.onProgress(0, allSymbols.size(), "");
        System.out.printf("[ScannerService] 掃描開始：有足夠歷史資料的股票池 = %d 支%n", allSymbols.size());

        // 三個等級的結果桶，用於自適應門檻合併
        List<ScannedResult> tierHigh = new ArrayList<>();  // score >= 60
        List<ScannedResult> tierMed  = new ArrayList<>();  // score >= 45
        List<ScannedResult> tierLow  = new ArrayList<>();  // score >= 30
        Map<String, Integer> industryRsBonusMap = buildIndustryRelativeStrengthBonusMap(allSymbols);
        int skipped = 0;

        for (int index = 0; index < allSymbols.size(); index++) {
            String symbol = allSymbols.get(index);
            try {
                if (symbol == null || symbol.isBlank()) {
                    continue;
                }

                int score = calculateDetailedScore(symbol, industryRsBonusMap, false);
                if (score < THRESHOLD_LOW) {
                    skipped++;
                    continue; // 低於最低門檻，完全略過（不建 ScannedResult）
                }

                double rsi = indicatorCalculator.calculateRSI(symbol, 14);
                StockUniverseEntry universeEntry = stockUniverseRepository.getStockUniverseEntry(symbol);
                AssetStrategyProfile assetProfile = resolveAssetStrategy(symbol);
                double price = stockDataRepository.getLatestPrice(symbol);
                double ma5  = stockDataRepository.calculateMA(symbol, 5);
                double ma20 = stockDataRepository.calculateMA(symbol, 20);
                double ma60 = stockDataRepository.calculateMA(symbol, 60);
                VolumeQuality volumeQuality = calculateVolumeQuality(symbol);
                boolean churnRisk = isVolumeChurnRisk(volumeQuality.realToRawRatio());

                ScannedResult sr = new ScannedResult(
                        symbol,
                        score,
                        rsi,
                        buildConclusion(score, rsi, price, ma5, ma20, ma60),
                        price,
                        volumeQuality.realToRawRatio(),
                        churnRisk,
                        universeEntry == null ? "UNKNOWN" : universeEntry.getAssetType(),
                        universeEntry == null ? ""        : universeEntry.getMarket(),
                        assetProfile.strategyMode(),
                        assetProfile.strategyDescription()
                );

                if      (score >= THRESHOLD_HIGH) tierHigh.add(sr);
                else if (score >= THRESHOLD_MED)  tierMed.add(sr);
                else                              tierLow.add(sr);
            } finally {
                safeProgressListener.onProgress(index + 1, allSymbols.size(), symbol == null ? "" : symbol);
            }
        }

        // ── 自適應門檻合併：優先回傳高分，不足時往下補 ─────────────────
        Comparator<ScannedResult> byScoreDesc = (a, b) -> Integer.compare(b.getScore(), a.getScore());
        tierHigh.sort(byScoreDesc);
        tierMed.sort(byScoreDesc);
        tierLow.sort(byScoreDesc);

        List<ScannedResult> results = new ArrayList<>(tierHigh);
        if (results.size() < topN) results.addAll(tierMed);
        if (results.size() < topN) results.addAll(tierLow);
        results.sort(byScoreDesc);

        int effectiveThreshold = !tierHigh.isEmpty() ? THRESHOLD_HIGH
                               : !tierMed.isEmpty()  ? THRESHOLD_MED
                               : THRESHOLD_LOW;

        System.out.printf("[ScannerService] 掃描完成：門檻=%d，高分=%d，次高=%d，兜底=%d，略過=%d，回傳=%d%n",
                effectiveThreshold, tierHigh.size(), tierMed.size(), tierLow.size(), skipped, Math.min(topN, results.size()));

        // 掃描完成後，將高分標的寫入 SCAN_HISTORY
        processDailyScan(results);

        int safeTopN = topN <= 0 ? 10 : Math.min(topN, results.size());
        return new ArrayList<>(results.subList(0, safeTopN));
    }

    @FunctionalInterface
    public interface ScanProgressListener {
        void onProgress(int completed, int total, String currentSymbol);
    }

    /**
     * 補抓所有已知股票的法人資料（供排程任務呼叫，不在掃描主流程執行）。
     * 避免掃描時對每支股票逐一發 HTTP 請求。
     */
    public void refreshInstitutionalData() {
        List<String> allSymbols = stockUniverseRepository.getAllSymbols();
        for (String symbol : allSymbols) {
            if (symbol == null || symbol.isBlank()) continue;
            try {
                List<InstitutionalTrade> recent = twseService.fetchRecentInstitutionalData(symbol, 20);
                if (!recent.isEmpty()) {
                    institutionalDataRepository.saveInstitutionalTrades(symbol, recent);
                }
            } catch (Exception e) {
                System.err.println("[ScannerService] 法人資料補抓失敗 " + symbol + ": " + e.getMessage());
            }
        }
    }

    public void processDailyScan(List<ScannedResult> topStocks) {
        if (topStocks == null || topStocks.isEmpty()) {
            return;
        }

        for (ScannedResult res : topStocks) {
            if (res.getScore() >= 85) { // 只存高分標的
                ScanHistory history = new ScanHistory(
                        res.getSymbol(),
                        res.getScore(),
                        res.getCurrentPrice(),
                        res.getRsi()
                );
                historyRepository.save(history);
            }
        }
    }

    public int calculateDetailedScore(String symbol) {
        return calculateDetailedScore(symbol, null, true);
    }

    public int calculateDetailedScore(String symbol, Map<String, Integer> precomputedIndustryRsBonusMap) {
        return calculateDetailedScore(symbol, precomputedIndustryRsBonusMap, true);
    }

    private int calculateDetailedScore(String symbol, Map<String, Integer> precomputedIndustryRsBonusMap, boolean includeExternalSignals) {
        if (symbol == null || symbol.isBlank()) {
            return 0;
        }

        AssetStrategyProfile assetProfile = resolveAssetStrategy(symbol);

        int totalScore = 0;

        // 1. 趨勢細部化 (40%)
        double ma20 = calculateMA(symbol, 20);
        double ma20Past = getHistoricalMA(symbol, 20, 5); // 5天前的MA20
        if (ma20 > 0 && ma20Past > 0) {
            double slope = (ma20 - ma20Past) / ma20Past;
            if (slope > 0.02) {
                totalScore += scaleScore(40, assetProfile.trendMultiplier());
            } else if (slope > 0) {
                totalScore += scaleScore(20, assetProfile.trendMultiplier());
            }
        }
        double adx = indicatorCalculator.calculateADX(symbol, Math.max(5, adxPeriod));
        if (adx >= adxStrongThreshold) {
            totalScore += scaleScore(adxStrongBonus, assetProfile.trendMultiplier());
        } else if (adx >= adxModerateThreshold) {
            totalScore += scaleScore(adxModerateBonus, assetProfile.trendMultiplier());
        }
        double latestPrice = stockDataRepository.getLatestPrice(symbol);
        double atr = indicatorCalculator.calculateATR(symbol, Math.max(5, atrPeriod));
        if (latestPrice > 0 && atr > 0) {
            double atrRatio = atr / latestPrice;
            if (atrRatio > 0 && atrRatio <= atrHealthyRatio) {
                totalScore += scaleScore(atrHealthyBonus, assetProfile.trendMultiplier());
            } else if (atrRatio >= atrOverheatRatio) {
                totalScore -= scaleScore(atrOverheatPenalty, assetProfile.trendMultiplier());
            }
        }
        int superTrendDirection = indicatorCalculator.calculateSuperTrendDirection(
                symbol, Math.max(5, superTrendPeriod), Math.max(1.0, superTrendMultiplier)
        );
        if (superTrendDirection > 0) {
            totalScore += scaleScore(superTrendBullishBonus, assetProfile.trendMultiplier());
        } else if (superTrendDirection < 0) {
            totalScore -= scaleScore(superTrendBearishPenalty, assetProfile.trendMultiplier());
        }
        double donchianPosition = indicatorCalculator.calculateDonchianPosition(symbol, Math.max(10, donchianPeriod));
        if (donchianPosition >= 0.90) {
            totalScore += scaleScore(donchianBreakoutBonus, assetProfile.trendMultiplier());
        } else if (donchianPosition <= 0.10) {
            totalScore -= scaleScore(donchianBreakdownPenalty, assetProfile.trendMultiplier());
        }

        // 2. 動能細部化 (20%)
        double rsi = calculateRSI(symbol, 14);
        if (rsi > 50 && rsi < 65) {
            totalScore += scaleScore(20, assetProfile.momentumMultiplier());
        } else if (rsi >= 65 && rsi < 75) {
            totalScore += scaleScore(10, assetProfile.momentumMultiplier());
        } else if (rsi >= 80) {
            totalScore -= scaleScore(10, assetProfile.momentumMultiplier()); // 扣分項目
        }
        double obvStrength = indicatorCalculator.calculateOBV(symbol, Math.max(5, obvLookbackDays));
        if (obvStrength >= obvStrongThreshold) {
            totalScore += scaleScore(obvStrongBonus, assetProfile.momentumMultiplier());
        } else if (obvStrength >= obvModerateThreshold) {
            totalScore += scaleScore(obvModerateBonus, assetProfile.momentumMultiplier());
        } else if (obvStrength <= -obvModerateThreshold) {
            totalScore -= scaleScore(obvWeakPenalty, assetProfile.momentumMultiplier());
        }
        double cci = indicatorCalculator.calculateCCI(symbol, Math.max(10, cciPeriod));
        if (cci >= 100) {
            totalScore += scaleScore(cciStrongBonus, assetProfile.momentumMultiplier());
        } else if (cci <= -100) {
            totalScore -= scaleScore(cciWeakPenalty, assetProfile.momentumMultiplier());
        }
        double williamsR = indicatorCalculator.calculateWilliamsR(symbol, Math.max(10, williamsRPeriod));
        if (williamsR > -30) {
            totalScore -= scaleScore(williamsRExtremePenalty, assetProfile.momentumMultiplier());
        } else if (williamsR >= -85 && williamsR <= -55) {
            totalScore += scaleScore(williamsRReboundBonus, assetProfile.momentumMultiplier());
        }
        double aroonOsc = indicatorCalculator.calculateAroonOscillator(symbol, Math.max(12, aroonPeriod));
        if (aroonOsc >= 35) {
            totalScore += scaleScore(aroonStrongBonus, assetProfile.momentumMultiplier());
        } else if (aroonOsc <= -35) {
            totalScore -= scaleScore(aroonWeakPenalty, assetProfile.momentumMultiplier());
        }

        // 3. 量能細部化 (25%)
        double volRatio = getVolumeRatio(symbol); // 今日量 / 5日均量
        if (volRatio > 2.0) {
            totalScore += scaleScore(25, assetProfile.volumeMultiplier());
        } else if (volRatio > 1.2) {
            totalScore += scaleScore(15, assetProfile.volumeMultiplier());
        }
        double mfi = indicatorCalculator.calculateMFI(symbol, Math.max(5, mfiPeriod));
        if (mfi >= 45 && mfi <= 75) {
            totalScore += scaleScore(mfiHealthyBonus, assetProfile.volumeMultiplier());
        } else if (mfi >= 85) {
            totalScore -= scaleScore(mfiOverboughtPenalty, assetProfile.volumeMultiplier());
        } else if (mfi <= 20) {
            totalScore += scaleScore(mfiOversoldBonus, assetProfile.volumeMultiplier());
        }
        double cmf = indicatorCalculator.calculateCMF(symbol, Math.max(10, cmfPeriod));
        if (cmf >= cmfPositiveThreshold) {
            totalScore += scaleScore(cmfPositiveBonus, assetProfile.volumeMultiplier());
        } else if (cmf <= cmfNegativeThreshold) {
            totalScore -= scaleScore(cmfNegativePenalty, assetProfile.volumeMultiplier());
        }

        // 3-1. 流水灌量扣分：真實量 / 原始量 過低代表當沖流水主導
        VolumeQuality volumeQuality = calculateVolumeQuality(symbol);
        if (isVolumeChurnRisk(volumeQuality.realToRawRatio())) {
            totalScore -= Math.max(0, volumeChurnScorePenalty);
        }

        // 4. 相對強度 (15%)
        if (isStrongerThanMarket(symbol)) {
            totalScore += scaleScore(15, assetProfile.relativeStrengthMultiplier());
        }
        int industryRsBonus = resolveIndustryRelativeStrengthBonus(symbol, precomputedIndustryRsBonusMap);
        totalScore += scaleScore(industryRsBonus, assetProfile.relativeStrengthMultiplier());

        // 5. 法人動向加分
        totalScore += scaleScore(calculateInstitutionalBonus(symbol), assetProfile.institutionalMultiplier());

        // 6. 消息面回應（以 50 為中性，轉換為正負分）
        if (includeExternalSignals && totalScore >= THRESHOLD_LOW) {
            totalScore += calculateNewsReactionContribution(symbol);
        }

        // 7. ETF 專屬：流動性 / 折溢價 / 追蹤誤差 / 追蹤差距 / 淨申贖資金流
        totalScore += includeExternalSignals
                ? calculateEtfSpecificBonus(symbol, assetProfile)
                : calculateLocalEtfSpecificBonus(symbol, assetProfile);

        return Math.min(100, Math.max(0, totalScore));
    }

    private int calculateNewsReactionContribution(String symbol) {
        if (!newsReactionEnabled || newsReactionService == null || scoreEngine == null || symbol == null || symbol.isBlank()) {
            return 0;
        }

        try {
            int rawNewsScore = resolveCachedNewsRawScore(symbol);
            int normalizedNewsScore = scoreEngine.scoreNewsReaction(rawNewsScore);
            int centeredNewsDelta = normalizedNewsScore - 50;
            return scaleScore(centeredNewsDelta, newsReactionMultiplier);
        } catch (Exception e) {
            System.err.println("[ScannerService] 消息面評分失敗 " + symbol + ": " + e.getMessage());
            return 0;
        }
    }

    private int resolveCachedNewsRawScore(String symbol) {
        String key = symbol.trim();
        long now = System.currentTimeMillis();
        CachedNewsReactionScore cached = newsReactionCache.get(key);
        if (cached != null && now - cached.cachedAtMs <= Math.max(0L, newsReactionCacheMs)) {
            return cached.rawScore;
        }

        NewsReactionService.NewsReactionResult result = newsReactionService.evaluateTodayReaction(key);
        int rawScore = result == null ? 0 : result.score();
        newsReactionCache.put(key, new CachedNewsReactionScore(rawScore, now));
        return rawScore;
    }

    private int calculateInstitutionalBonus(String symbol) {
        List<InstitutionalTrade> data = institutionalDataRepository.getRecentInstitutionalTrades(symbol, 10);
        if (data.isEmpty()) {
            return 0;
        }

        InstitutionalTrade latest = data.get(data.size() - 1);
        int bonus = 0;

        // 法人合買 (20分)
        if (latest.getForeignBuy() > 0 && latest.getTrustBuy() > 0) {
            bonus += 20;
        }

        // 買盤增溫 (15分)
        double avg5 = institutionalDataRepository.getAverageInstitutionalNetBuy(symbol, 5);
        if (latest.getTotalNetBuy() > avg5) {
            bonus += 15;
        }

        // 投信鎖碼 (10分以上)
        int trustLockedScore = institutionalService.calculateTrustLockScore(symbol);
        if (trustLockedScore >= 10) {
            bonus += 10;
        }
        bonus += institutionalService.calculateMainForceContinuousBuyStrength(symbol);

        return bonus;
    }

    private double calculateMA(String symbol, int days) {
        return stockDataRepository.calculateMA(symbol, days);
    }

    private double calculateRSI(String symbol, int period) {
        return indicatorCalculator.calculateRSI(symbol, period);
    }

    private double getHistoricalMA(String symbol, int period, int daysAgo) {
        int needed = period + daysAgo;
        List<StockDataPoint> points = stockDataRepository.getFullHistory(symbol, needed + 20);
        if (points.size() < needed) {
            return 0.0;
        }

        int end = points.size() - daysAgo - 1;
        int start = end - period + 1;
        if (start < 0 || end >= points.size()) {
            return 0.0;
        }

        double sum = 0.0;
        for (int i = start; i <= end; i++) {
            double close = points.get(i).c > 0 ? points.get(i).c : points.get(i).price;
            if (close <= 0) {
                return 0.0;
            }
            sum += close;
        }
        return sum / period;
    }

    private double getVolumeRatio(String symbol) {
        VolumeQuality quality = calculateVolumeQuality(symbol);
        if (quality.realVolumeRatioForScoring() > 0) {
            return quality.realVolumeRatioForScoring();
        }

        long volumeToday = stockDataRepository.getLatestVolume(symbol);
        long volumeMA5 = stockDataRepository.calculateVolumeMA(symbol, 5);
        if (volumeToday <= 0 || volumeMA5 <= 0) {
            return 0.0;
        }
        return (double) volumeToday / volumeMA5;
    }

    private VolumeQuality calculateVolumeQuality(String symbol) {
        long rawVolumeToday = Math.max(0L, stockDataRepository.getLatestVolume(symbol));
        List<FinMindDayTradingData> dayTradingRows = institutionalDataRepository.getDayTradingHistory(symbol, 5);
        if (dayTradingRows.isEmpty()) {
            return new VolumeQuality(0.0, 0.0, 1.0);
        }

        Map<String, FinMindDayTradingData> dayTradingByDate = dayTradingRows.stream()
                .filter(row -> row != null && row.getDate() != null)
                .collect(Collectors.toMap(FinMindDayTradingData::getDate, row -> row, (a, b) -> b));

        List<StockDataPoint> priceRows = stockDataRepository.getFullHistory(symbol, 5);
        if (priceRows.isEmpty()) {
            return new VolumeQuality(0.0, 0.0, 1.0);
        }

        List<Long> realVolumes = new ArrayList<>();
        for (StockDataPoint row : priceRows) {
            long rawVolume = Math.max(0L, row.volume);
            FinMindDayTradingData dayRow = dayTradingByDate.get(row.date);
            long dayTradingVolume = dayRow == null ? 0L : Math.max(0L, dayRow.getDayTradingVolume());
            long realVolume = Math.max(0L, rawVolume - dayTradingVolume * 2L);
            realVolumes.add(realVolume);
        }

        long realVolumeToday = realVolumes.get(realVolumes.size() - 1);
        double realVolumeMA5 = realVolumes.stream().mapToLong(v -> v).average().orElse(0.0);
        double realVolumeRatioForScoring = (realVolumeToday > 0 && realVolumeMA5 > 0) ? (realVolumeToday / realVolumeMA5) : 0.0;
        double realToRawRatio = rawVolumeToday > 0 ? (realVolumeToday / (double) rawVolumeToday) : 1.0;
        return new VolumeQuality(realVolumeRatioForScoring, realVolumeToday, realToRawRatio);
    }

    private boolean isVolumeChurnRisk(double realToRawRatio) {
        return realToRawRatio > 0 && realToRawRatio < volumeChurnRatioThreshold;
    }

    private AssetStrategyProfile resolveAssetStrategy(String symbol) {
        StockUniverseEntry entry = stockUniverseRepository.getStockUniverseEntry(symbol);
        String assetType = entry == null ? "STOCK" : normalizeAssetType(entry.getAssetType());
        return switch (assetType) {
            case "ETF" -> new AssetStrategyProfile(assetType, etfTrendMultiplier, etfMomentumMultiplier, etfVolumeMultiplier,
                    etfRelativeStrengthMultiplier, etfInstitutionalMultiplier,
                    etfLiquidityMultiplier, etfPremiumMultiplier, etfTrackingErrorMultiplier, etfTrackingDifferenceMultiplier, etfFundFlowMultiplier,
                    "ETF 趨勢配置",
                    "降低短線動能噪音，強化趨勢與相對強度，適合指數型商品。"
            );
            case "BOND_ETF" -> new AssetStrategyProfile(assetType, bondEtfTrendMultiplier, bondEtfMomentumMultiplier, bondEtfVolumeMultiplier,
                    bondEtfRelativeStrengthMultiplier, bondEtfInstitutionalMultiplier,
                    etfLiquidityMultiplier, etfPremiumMultiplier, etfTrackingErrorMultiplier, etfTrackingDifferenceMultiplier, etfFundFlowMultiplier,
                    "債券 ETF 防守配置",
                    "大幅降低動能權重，提高趨勢、波動穩定度與市場環境判讀，偏防守型策略。"
            );
            case "ETN" -> new AssetStrategyProfile(assetType, etnTrendMultiplier, etnMomentumMultiplier, etnVolumeMultiplier,
                    etnRelativeStrengthMultiplier, etnInstitutionalMultiplier,
                    0.0, 0.0, 0.0, 0.0, 0.0,
                    "ETN 波動配置",
                    "提高波動與量價敏感度，保守看待趨勢與法人訊號，適合高波動結構商品。"
            );
            default -> new AssetStrategyProfile(assetType, stockTrendMultiplier, stockMomentumMultiplier, stockVolumeMultiplier,
                    stockRelativeStrengthMultiplier, stockInstitutionalMultiplier,
                    0.0, 0.0, 0.0, 0.0, 0.0,
                    "標準股票策略",
                    "以趨勢、動能、量價與法人籌碼均衡評估，適用一般普通股。"
            );
        };
    }

    private String normalizeAssetType(String assetType) {
        return assetType == null || assetType.isBlank() ? "STOCK" : assetType.trim().toUpperCase();
    }

    private int scaleScore(int baseScore, double multiplier) {
        return (int) Math.round(baseScore * multiplier);
    }

    private int calculateEtfSpecificBonus(String symbol, AssetStrategyProfile assetProfile) {
        if (!isEtfLikeAsset(assetProfile.assetType())) {
            return 0;
        }

        int liquidityScore = calculateEtfLiquidityScore(symbol);
        int premiumScore = calculateEtfDiscountPremiumScore(symbol);
        int trackingErrorScore = calculateEtfTrackingErrorScore(symbol);
        int trackingDifferenceScore = calculateEtfTrackingDifferenceScore(symbol);
        int flowScore = calculateEtfFundFlowScore(symbol);

        return scaleScore(liquidityScore, assetProfile.etfLiquidityMultiplier())
                + scaleScore(premiumScore, assetProfile.etfPremiumMultiplier())
                + scaleScore(trackingErrorScore, assetProfile.etfTrackingErrorMultiplier())
                + scaleScore(trackingDifferenceScore, assetProfile.etfTrackingDifferenceMultiplier())
                + scaleScore(flowScore, assetProfile.etfFundFlowMultiplier());
    }

    private int calculateLocalEtfSpecificBonus(String symbol, AssetStrategyProfile assetProfile) {
        if (!isEtfLikeAsset(assetProfile.assetType())) {
            return 0;
        }
        return scaleScore(calculateEtfLiquidityScore(symbol), assetProfile.etfLiquidityMultiplier());
    }

    private boolean isEtfLikeAsset(String assetType) {
        return "ETF".equalsIgnoreCase(assetType) || "BOND_ETF".equalsIgnoreCase(assetType);
    }

    private int calculateEtfLiquidityScore(String symbol) {
        List<StockDataPoint> history = stockDataRepository.getFullHistory(symbol, 12);
        if (history.size() < 5) {
            return 0;
        }

        int count = 0;
        double volumeSum = 0.0;
        double spreadSum = 0.0;
        for (int i = Math.max(0, history.size() - 10); i < history.size(); i++) {
            StockDataPoint row = history.get(i);
            double close = closeAt(history, i);
            if (close <= 0 || row.h <= 0 || row.l <= 0) {
                continue;
            }
            volumeSum += Math.max(0L, row.volume);
            spreadSum += Math.max(0.0, (row.h - row.l) / close);
            count++;
        }
        if (count == 0) {
            return 0;
        }

        double avgVolume = volumeSum / count;
        double avgSpread = spreadSum / count;
        double latestPrice = closeAt(history, history.size() - 1);
        double avgTurnover = avgVolume * Math.max(0.0, latestPrice);

        int score;
        if (avgVolume >= 3_000_000 && avgSpread <= 0.012) score = 18;
        else if (avgVolume >= 1_000_000 && avgSpread <= 0.020) score = 12;
        else if (avgVolume >= 250_000 && avgSpread <= 0.030) score = 6;
        else if (avgSpread >= 0.060) score = -8;
        else score = 0;

        if (avgTurnover >= 400_000_000) score += 4;
        else if (avgTurnover <= 30_000_000) score -= 3;

        return Math.max(-10, Math.min(22, score));
    }

    private int calculateEtfDiscountPremiumScore(String symbol) {
        if (finMindClient == null) {
            return 0;
        }

        try {
            FinMindClient.EtfPremiumResult premiumResult = finMindClient.fetchEtfDiscountPremium(symbol);
            Double ratio = premiumResult == null ? null : premiumResult.getRatio();
            if (ratio == null) {
                return 0;
            }

            if (ratio <= -0.015) return 12;
            if (ratio <= -0.005) return 7;
            if (ratio <= 0.005) return 4;
            if (ratio >= 0.015) return -10;
            if (ratio >= 0.008) return -6;
            return 0;
        } catch (Exception e) {
            System.err.println("[ScannerService] ETF 折溢價評分失敗 " + symbol + ": " + e.getMessage());
            return 0;
        }
    }

    private int calculateEtfTrackingErrorScore(String symbol) {
        if (finMindClient == null) {
            return 0;
        }

        try {
            String startDate = LocalDate.now().minusDays(120).toString();
            List<org.gtalent.dto.FinMindNavData> navRows = finMindClient.fetchNavData(symbol, startDate);
            if (navRows.isEmpty()) {
                return 0;
            }

            Map<String, Double> navByDate = new HashMap<>();
            for (org.gtalent.dto.FinMindNavData row : navRows) {
                if (row == null || row.getDate() == null || row.getDate().isBlank() || row.getNav() <= 0) {
                    continue;
                }
                navByDate.put(row.getDate(), row.getNav());
            }

            List<StockDataPoint> priceRows = stockDataRepository.getFullHistory(symbol, 80);
            List<Double> errors = new ArrayList<>();
            for (StockDataPoint row : priceRows) {
                Double nav = navByDate.get(row.date);
                double close = row.c > 0 ? row.c : row.price;
                if (nav == null || nav <= 0 || close <= 0) {
                    continue;
                }
                errors.add((close / nav) - 1.0);
            }

            if (errors.size() < 8) {
                return 0;
            }

            double mse = errors.stream().mapToDouble(v -> v * v).average().orElse(0.0);
            double rmse = Math.sqrt(Math.max(0.0, mse));

            if (rmse <= 0.003) return 12;
            if (rmse <= 0.006) return 8;
            if (rmse <= 0.010) return 4;
            if (rmse <= 0.018) return -4;
            return -8;
        } catch (Exception e) {
            System.err.println("[ScannerService] ETF 追蹤誤差評分失敗 " + symbol + ": " + e.getMessage());
            return 0;
        }
    }

    private int calculateEtfFundFlowScore(String symbol) {
        FinMindClient.EtfBeneficiaryFlowResult flow = finMindClient.fetchEtfBeneficiaryFlow(symbol, 10);
        if (flow == null || !flow.isAvailable()) {
            return 0;
        }

        Double ratio = flow.getFlowRatio();
        Double netUnits = flow.getNetUnits();

        if (ratio != null) {
            if (ratio >= 0.010) return 10;
            if (ratio >= 0.004) return 6;
            if (ratio <= -0.010) return -10;
            if (ratio <= -0.004) return -6;
        }

        if (netUnits != null) {
            if (netUnits >= 150_000) return 6;
            if (netUnits >= 50_000) return 3;
            if (netUnits <= -150_000) return -6;
            if (netUnits <= -50_000) return -3;
        }
        return 0;
    }

    private int calculateEtfTrackingDifferenceScore(String symbol) {
        if (finMindClient == null) {
            return 0;
        }
        try {
            String startDate = LocalDate.now().minusDays(180).toString();
            List<org.gtalent.dto.FinMindNavData> navRows = finMindClient.fetchNavData(symbol, startDate);
            if (navRows.isEmpty()) {
                return 0;
            }

            Map<String, Double> navByDate = new HashMap<>();
            for (org.gtalent.dto.FinMindNavData row : navRows) {
                if (row != null && row.getDate() != null && !row.getDate().isBlank() && row.getNav() > 0) {
                    navByDate.put(row.getDate(), row.getNav());
                }
            }
            List<StockDataPoint> priceRows = stockDataRepository.getFullHistory(symbol, 140);
            List<Double> alignedClose = new ArrayList<>();
            List<Double> alignedNav = new ArrayList<>();
            for (StockDataPoint row : priceRows) {
                if (row == null || row.date == null || row.date.isBlank()) {
                    continue;
                }
                Double nav = navByDate.get(row.date);
                double close = row.c > 0 ? row.c : row.price;
                if (nav == null || nav <= 0 || close <= 0) {
                    continue;
                }
                alignedClose.add(close);
                alignedNav.add(nav);
            }
            if (alignedClose.size() < 12) {
                return 0;
            }

            double firstClose = alignedClose.get(0);
            double lastClose = alignedClose.get(alignedClose.size() - 1);
            double firstNav = alignedNav.get(0);
            double lastNav = alignedNav.get(alignedNav.size() - 1);
            if (firstClose <= 0 || firstNav <= 0 || lastClose <= 0 || lastNav <= 0) {
                return 0;
            }
            double closeReturn = (lastClose - firstClose) / firstClose;
            double navReturn = (lastNav - firstNav) / firstNav;
            double trackingDifference = closeReturn - navReturn;
            double absTd = Math.abs(trackingDifference);

            if (absTd <= 0.005) return 10;
            if (absTd <= 0.012) return 6;
            if (absTd <= 0.025) return 2;
            if (absTd <= 0.040) return -4;
            return -8;
        } catch (Exception e) {
            System.err.println("[ScannerService] ETF 追蹤差距評分失敗 " + symbol + ": " + e.getMessage());
            return 0;
        }
    }

    private int resolveIndustryRelativeStrengthBonus(String symbol, Map<String, Integer> precomputedIndustryRsBonusMap) {
        if (symbol == null || symbol.isBlank()) {
            return 0;
        }
        if (precomputedIndustryRsBonusMap != null) {
            return precomputedIndustryRsBonusMap.getOrDefault(symbol, 0);
        }

        StockUniverseEntry entry = stockUniverseRepository.getStockUniverseEntry(symbol);
        List<String> peers = stockUniverseRepository.getAllSymbolsWithData(
                Math.max(SCAN_MIN_HISTORY_DAYS, Math.max(25, industryRsLookbackDays + 1)),
                entry == null ? null : entry.getAssetType(),
                entry == null ? null : entry.getMarket()
        );
        if (!peers.contains(symbol)) {
            peers = new ArrayList<>(peers);
            peers.add(symbol);
        }
        return buildIndustryRelativeStrengthBonusMap(peers).getOrDefault(symbol, 0);
    }

    private Map<String, Integer> buildIndustryRelativeStrengthBonusMap(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return Map.of();
        }

        int lookbackDays = Math.max(5, industryRsLookbackDays);
        Map<String, String> bucketBySymbol = new HashMap<>();
        Map<String, Double> returnBySymbol = new HashMap<>();
        Map<String, List<Double>> groupReturns = new HashMap<>();

        for (String symbol : symbols) {
            if (symbol == null || symbol.isBlank()) {
                continue;
            }
            StockUniverseEntry entry = stockUniverseRepository.getStockUniverseEntry(symbol);
            String bucket = resolveIndustryBucket(symbol, entry);
            double ret = calculateLookbackReturn(symbol, lookbackDays);
            if (Double.isNaN(ret)) {
                continue;
            }
            bucketBySymbol.put(symbol, bucket);
            returnBySymbol.put(symbol, ret);
            groupReturns.computeIfAbsent(bucket, key -> new ArrayList<>()).add(ret);
        }

        for (List<Double> group : groupReturns.values()) {
            group.sort(Double::compareTo);
        }

        Map<String, Integer> bonusMap = new HashMap<>();
        int minGroupSize = Math.max(3, industryRsMinGroupSize);
        for (Map.Entry<String, Double> entry : returnBySymbol.entrySet()) {
            String symbol = entry.getKey();
            String bucket = bucketBySymbol.get(symbol);
            List<Double> group = groupReturns.get(bucket);
            if (group == null || group.size() < minGroupSize) {
                continue;
            }
            double percentile = calculatePercentile(group, entry.getValue());
            int bonus = resolveIndustryRsBonusByPercentile(percentile);
            if (bonus != 0) {
                bonusMap.put(symbol, bonus);
            }
        }
        return bonusMap;
    }

    private String resolveIndustryBucket(String symbol, StockUniverseEntry entry) {
        String market = entry == null || entry.getMarket() == null ? "" : entry.getMarket().trim().toUpperCase();
        String assetType = entry == null ? "STOCK" : normalizeAssetType(entry.getAssetType());

        if (!"STOCK".equals(assetType)) {
            return "ASSET:" + assetType + ":" + market;
        }

        String code = symbol == null ? "" : symbol.trim();
        if (code.length() >= 2 && Character.isDigit(code.charAt(0)) && Character.isDigit(code.charAt(1))) {
            return "IND:" + code.substring(0, 2) + ":" + market;
        }
        return "IND:OTHER:" + market;
    }

    private double calculateLookbackReturn(String symbol, int lookbackDays) {
        List<StockDataPoint> history = stockDataRepository.getFullHistory(symbol, lookbackDays + 1);
        if (history.size() < lookbackDays + 1) {
            return Double.NaN;
        }
        double start = closeAt(history, history.size() - lookbackDays - 1);
        double end = closeAt(history, history.size() - 1);
        if (start <= 0 || end <= 0) {
            return Double.NaN;
        }
        return (end - start) / start;
    }

    private double calculatePercentile(List<Double> sortedValues, double value) {
        if (sortedValues == null || sortedValues.isEmpty()) {
            return 0.5;
        }
        int upperBound = Collections.binarySearch(sortedValues, value);
        if (upperBound < 0) {
            upperBound = -upperBound - 1;
        } else {
            while (upperBound < sortedValues.size() && sortedValues.get(upperBound) <= value) {
                upperBound++;
            }
        }
        return upperBound / (double) sortedValues.size();
    }

    private int resolveIndustryRsBonusByPercentile(double percentile) {
        if (percentile >= 0.80) {
            return Math.max(0, industryRsBonusTop);
        }
        if (percentile >= 0.65) {
            return Math.max(0, industryRsBonusUpperMid);
        }
        if (percentile <= 0.20) {
            return -Math.max(0, industryRsPenaltyBottom);
        }
        if (percentile <= 0.35) {
            return -Math.max(0, industryRsPenaltyLowerMid);
        }
        return 0;
    }

    private record VolumeQuality(double realVolumeRatioForScoring, double realVolumeToday, double realToRawRatio) {}

    private record CachedNewsReactionScore(int rawScore, long cachedAtMs) {}

    private record AssetStrategyProfile(String assetType,
                                        double trendMultiplier,
                                        double momentumMultiplier,
                                        double volumeMultiplier,
                                        double relativeStrengthMultiplier,
                                        double institutionalMultiplier,
                                        double etfLiquidityMultiplier,
                                        double etfPremiumMultiplier,
                                        double etfTrackingErrorMultiplier,
                                        double etfTrackingDifferenceMultiplier,
                                        double etfFundFlowMultiplier,
                                        String strategyMode,
                                        String strategyDescription) {}

    private boolean isStrongerThanMarket(String symbol) {
        List<StockDataPoint> stock = stockDataRepository.getFullHistory(symbol, 30);
        List<StockDataPoint> market = stockDataRepository.getFullHistory("0050", 30);
        if (stock.size() < 21 || market.size() < 21) {
            return false;
        }

        double stockNow = closeAt(stock, stock.size() - 1);
        double stockPrev = closeAt(stock, stock.size() - 21);
        double marketNow = closeAt(market, market.size() - 1);
        double marketPrev = closeAt(market, market.size() - 21);

        if (stockNow <= 0 || stockPrev <= 0 || marketNow <= 0 || marketPrev <= 0) {
            return false;
        }

        double stockReturn = (stockNow - stockPrev) / stockPrev;
        double marketReturn = (marketNow - marketPrev) / marketPrev;
        return stockReturn > marketReturn;
    }

    private double closeAt(List<StockDataPoint> data, int index) {
        StockDataPoint p = data.get(index);
        return p.c > 0 ? p.c : p.price;
    }

    private static String buildConclusion(int score, double rsi, double price, double ma5, double ma20, double ma60) {
        String trend = (price > ma5 && ma5 > ma20 && ma20 > ma60) ? "趨勢完整多頭" : "趨勢偏多";
        String momentum = (rsi >= 40 && rsi <= 65) ? "動能健康" : (rsi < 40 ? "動能偏弱但可能反彈" : "動能偏熱");
        return trend + "；" + momentum + "；綜合評分 " + score + " 分";
    }

    /**
     * 依「投信鎖碼評分」掃描所有已知股票，回傳前 topN 檔。
     * 評分 >= 30 代表有明顯鎖碼跡象。
     *
     * @param topN 要回傳的最大筆數
     */
    public List<Map<String, Object>> getTopTrustStocks(int topN) {
        List<String> allSymbols = stockUniverseRepository.getAllSymbols();
        List<Map<String, Object>> candidates = new ArrayList<>();
        Map<String, Integer> industryRsBonusMap = buildIndustryRelativeStrengthBonusMap(allSymbols);

        for (String symbol : allSymbols) {
            if (symbol == null || symbol.isBlank()) {
                continue;
            }

            // 預先過濾：本地無投信資料的股票直接跳過，避免 InstitutionalService 觸發遠端 TWSE/FinMind fetch
            List<InstitutionalTrade> localTrades =
                    institutionalDataRepository.getRecentInstitutionalTrades(symbol, 10);
            if (localTrades.isEmpty()) {
                continue;
            }

            try {
                int trustScore = institutionalService.calculateTrustLockScore(symbol);
                if (trustScore < 30) {
                    continue; // 低於門檻，略過
                }

                double price       = stockDataRepository.getLatestPrice(symbol);
                double rsi         = indicatorCalculator.calculateRSI(symbol, 14);
                int    totalScore  = calculateDetailedScore(symbol, industryRsBonusMap);
                int    consecutive = institutionalDataRepository.countConsecutiveTrustBuyDays(symbol, 10);
                double lockRatio   = institutionalService.getLockRatio(symbol);

                // 計算漲跌幅
                List<StockDataPoint> recent2 = stockDataRepository.getRecentHistory(symbol, 2);
                double changePercent = 0.0;
                if (recent2.size() >= 2) {
                    double todayPrice = recent2.get(1).c > 0 ? recent2.get(1).c : recent2.get(1).price;
                    double yestPrice  = recent2.get(0).c > 0 ? recent2.get(0).c : recent2.get(0).price;
                    if (yestPrice > 0) {
                        changePercent = (todayPrice - yestPrice) / yestPrice * 100.0;
                    }
                }

                // 取最新一筆法人資料
                List<InstitutionalTrade> trades = institutionalService.getRecentInstitutionalTrades(symbol, 1);
                long latestTrustBuy = trades.isEmpty() ? 0L : trades.get(0).getTrustBuy();

                Map<String, Object> row = new HashMap<>();
                row.put("symbol",          symbol);
                row.put("price",           Math.round(price * 100.0) / 100.0);
                row.put("change",          String.format("%.2f%%", changePercent));
                row.put("changeValue",     changePercent);
                row.put("rsi",             Math.round(rsi  * 100.0) / 100.0);
                row.put("trustScore",      trustScore);
                row.put("totalScore",      totalScore);
                row.put("consecutiveDays", consecutive);
                row.put("continuousDays",  consecutive);   // 前端 alias
                row.put("lockRatio",       Math.round(lockRatio * 10.0) / 10.0);
                row.put("latestTrustBuy",  latestTrustBuy);
                row.put("conclusion",      consecutive >= 5 ? "🔒 強力鎖碼" : consecutive >= 3 ? "📈 持續買超" : "⚠️ 初步觀察");
                candidates.add(row);
            } catch (Exception e) {
                System.err.println("[getTopTrustStocks] 跳過 " + symbol + ": " + e.getMessage());
            }
        }

        // 依投信鎖碼評分 → 總分 → 連買天數 排序，取前 topN
        candidates.sort(Comparator
                .<Map<String, Object>>comparingInt(m -> -(int) m.get("trustScore"))
                .thenComparingInt(m -> -(int) m.get("totalScore"))
                .thenComparingInt(m -> -(int) m.get("consecutiveDays")));

        return candidates.subList(0, Math.min(topN, candidates.size()));
    }
}
