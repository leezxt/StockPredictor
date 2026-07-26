package org.gtalent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class RadarService {

    private static final long MONEY_PERCENTILE_CACHE_MS = 10 * 60 * 1000L;

    private final ScoreEngine scoreEngine;
    private final InstitutionalService institutionalService;
    private final MarketBreadthService marketBreadthService;
    private final FundamentalService fundamentalService;
    private final AdvancedFundamentalService advancedFundamentalService;
    private final DayTradingService dayTradingService;
    private final NoiseFilterService noiseFilterService;
    private final MarginAnalysisService marginAnalysisService;
    private final NewsReactionService newsReactionService;
    private final FinMindClient finMindClient;
    private final StockUniverseRepository stockUniverseRepository;
    private final StockDataRepository stockDataRepository;
    private final InstitutionalDataRepository institutionalDataRepository;
    private final IndicatorCalculator indicatorCalculator;
    private final Object moneyPercentileLock = new Object();
    private volatile long moneyPercentileCachedAtMs = 0L;
    private volatile MoneyDistributionSnapshot cachedMoneyDistribution = new MoneyDistributionSnapshot(List.of());

    @Value("${radar.strategy.stock.trend-weight:0.22}")
    private double stockTrendWeight;
    @Value("${radar.strategy.stock.momentum-weight:0.22}")
    private double stockMomentumWeight;
    @Value("${radar.strategy.stock.money-weight:0.20}")
    private double stockMoneyWeight;
    @Value("${radar.strategy.stock.volatility-weight:0.08}")
    private double stockVolatilityWeight;
    @Value("${radar.strategy.stock.context-weight:0.14}")
    private double stockContextWeight;
    @Value("${radar.strategy.stock.fundamental-weight:0.14}")
    private double stockFundamentalWeight;
    @Value("${radar.strategy.stock.news-weight:0.10}")
    private double stockNewsWeight;

    @Value("${radar.strategy.etf.trend-weight:0.28}")
    private double etfTrendWeight;
    @Value("${radar.strategy.etf.momentum-weight:0.16}")
    private double etfMomentumWeight;
    @Value("${radar.strategy.etf.money-weight:0.22}")
    private double etfMoneyWeight;
    @Value("${radar.strategy.etf.volatility-weight:0.12}")
    private double etfVolatilityWeight;
    @Value("${radar.strategy.etf.context-weight:0.22}")
    private double etfContextWeight;
    @Value("${radar.strategy.etf.fundamental-weight:0.00}")
    private double etfFundamentalWeight;
    @Value("${radar.strategy.etf.news-weight:0.12}")
    private double etfNewsWeight;

    @Value("${radar.strategy.bond-etf.trend-weight:0.24}")
    private double bondEtfTrendWeight;
    @Value("${radar.strategy.bond-etf.momentum-weight:0.10}")
    private double bondEtfMomentumWeight;
    @Value("${radar.strategy.bond-etf.money-weight:0.18}")
    private double bondEtfMoneyWeight;
    @Value("${radar.strategy.bond-etf.volatility-weight:0.22}")
    private double bondEtfVolatilityWeight;
    @Value("${radar.strategy.bond-etf.context-weight:0.26}")
    private double bondEtfContextWeight;
    @Value("${radar.strategy.bond-etf.fundamental-weight:0.00}")
    private double bondEtfFundamentalWeight;
    @Value("${radar.strategy.bond-etf.news-weight:0.08}")
    private double bondEtfNewsWeight;

    @Value("${radar.strategy.etn.trend-weight:0.18}")
    private double etnTrendWeight;
    @Value("${radar.strategy.etn.momentum-weight:0.18}")
    private double etnMomentumWeight;
    @Value("${radar.strategy.etn.money-weight:0.18}")
    private double etnMoneyWeight;
    @Value("${radar.strategy.etn.volatility-weight:0.26}")
    private double etnVolatilityWeight;
    @Value("${radar.strategy.etn.context-weight:0.20}")
    private double etnContextWeight;
    @Value("${radar.strategy.etn.fundamental-weight:0.00}")
    private double etnFundamentalWeight;
    @Value("${radar.strategy.etn.news-weight:0.10}")
    private double etnNewsWeight;

    public RadarService(ScoreEngine scoreEngine,
                        InstitutionalService institutionalService,
                        MarketBreadthService marketBreadthService,
                        FundamentalService fundamentalService,
                        AdvancedFundamentalService advancedFundamentalService,
                        DayTradingService dayTradingService,
                        NoiseFilterService noiseFilterService,
                        MarginAnalysisService marginAnalysisService,
                        NewsReactionService newsReactionService,
                        FinMindClient finMindClient,
                        StockUniverseRepository stockUniverseRepository,
                        StockDataRepository stockDataRepository,
                        InstitutionalDataRepository institutionalDataRepository,
                        IndicatorCalculator indicatorCalculator) {
        this.scoreEngine = scoreEngine;
        this.institutionalService = institutionalService;
        this.marketBreadthService = marketBreadthService;
        this.fundamentalService = fundamentalService;
        this.advancedFundamentalService = advancedFundamentalService;
        this.dayTradingService = dayTradingService;
        this.noiseFilterService = noiseFilterService;
        this.marginAnalysisService = marginAnalysisService;
        this.newsReactionService = newsReactionService;
        this.finMindClient = finMindClient;
        this.stockUniverseRepository = stockUniverseRepository;
        this.stockDataRepository = stockDataRepository;
        this.institutionalDataRepository = institutionalDataRepository;
        this.indicatorCalculator = indicatorCalculator;
    }

    public RadarScoreResult calculateRadarScores(String symbol) {
        RadarScoreResult result = new RadarScoreResult();
        if (symbol == null || symbol.isBlank()) {
            result.conclusion = "請輸入股票代碼";
            result.label = "--";
            return result;
        }

        String cleanSymbol = symbol.trim();
        StockUniverseEntry universeEntry = stockUniverseRepository.getStockUniverseEntry(cleanSymbol);
        result.assetType = resolveAssetType(universeEntry);
        result.market = universeEntry == null ? "" : universeEntry.getMarket();
        result.etfMode = isEtfSymbol(cleanSymbol);
        AssetWeightProfile baseProfile = resolveAssetWeightProfile(result.assetType, result.etfMode);
        result.strategyMode = baseProfile.strategyMode();
        result.strategyDescription = baseProfile.strategyDescription();

        double price = stockDataRepository.getLatestPrice(cleanSymbol);
        double ma5 = stockDataRepository.calculateMA(cleanSymbol, 5);
        double ma10 = stockDataRepository.calculateMA(cleanSymbol, 10);
        double ma20 = stockDataRepository.calculateMA(cleanSymbol, 20);
        double ma60 = stockDataRepository.calculateMA(cleanSymbol, 60);
        result.trend = scoreEngine.scoreTrend(price, ma5, ma10, ma20, ma60);

        double rsi = indicatorCalculator.calculateRSI(cleanSymbol, 14);
        List<MACDResult> macdSeries = indicatorCalculator.calculateMACDSeries(cleanSymbol, 3);
        double macdHist = macdSeries.isEmpty() ? 0.0 : macdSeries.get(macdSeries.size() - 1).histogram;
        double macdHistPrev = macdSeries.size() >= 2 ? macdSeries.get(macdSeries.size() - 2).histogram : macdHist;

        List<KDResult> kdSeries = indicatorCalculator.calculateKD(cleanSymbol, 3);
        KDResult lastKd = kdSeries.isEmpty() ? null : kdSeries.get(kdSeries.size() - 1);
        KDResult prevKd = kdSeries.size() >= 2 ? kdSeries.get(kdSeries.size() - 2) : lastKd;
        double k = lastKd == null ? 50.0 : lastKd.getK();
        double d = lastKd == null ? 50.0 : lastKd.getD();
        double prevK = prevKd == null ? k : prevKd.getK();
        double prevD = prevKd == null ? d : prevKd.getD();

        result.rawMomentum = scoreEngine.scoreMomentum(rsi, macdHist, macdHistPrev, k, d, prevK, prevD);
        double rawDayTradingRate = resolveLatestDayTradingRate(cleanSymbol);
        result.dayTradingRate = noiseFilterService.normalizeDayTradingRate(rawDayTradingRate);

        // 改用四段制信任度權重：>= 60% → 0.40 / >= 45% → 0.65 / >= 30% → 0.85 / 其他 → 1.0
        // 相較原本三段制 resolveMomentumWeight，更能精準捕捉「極度投機（60%+）」分級
        double confidenceWeight = noiseFilterService.calculateConfidenceWeight(result.dayTradingRate);
        result.confidenceWeight = confidenceWeight;
        result.momentumTrustWeight = confidenceWeight;
        result.momentum = clampToScore(result.rawMomentum * confidenceWeight);

        int trustDays = institutionalService.getContinuousBuyDays(cleanSymbol);
        double lockRatioPct = institutionalService.getLockRatio(cleanSymbol);

        List<InstitutionalTrade> latestTrades = institutionalService.getRecentInstitutionalTrades(cleanSymbol, 5);
        long latestNetBuy = 0;
        List<Long> netBuys = new ArrayList<>();
        for (InstitutionalTrade trade : latestTrades) {
            long net = trade.getTotalNetBuy();
            netBuys.add(net);
        }
        if (!netBuys.isEmpty()) {
            latestNetBuy = netBuys.get(netBuys.size() - 1);
        }
        double avgNetBuy5 = netBuys.isEmpty() ? 0.0 : netBuys.stream().mapToLong(v -> v).average().orElse(0.0);

        result.bigHolderScore = institutionalService.calculateBigHolderScore(cleanSymbol);
        int institutionalMoneyScore = scoreEngine.scoreMoney(trustDays, lockRatioPct, latestNetBuy, avgNetBuy5);
        int bigHolderNormalizedScore = (int) Math.round(Math.max(0.0, Math.min(20.0, result.bigHolderScore)) * 5.0);
        result.money = scoreEngine.scoreMoney(trustDays, lockRatioPct, latestNetBuy, avgNetBuy5, result.bigHolderScore);

        // ── 高噪音補償機制 ──────────────────────────────────────────────────────
        // 當沖率 >= 60%（confidenceWeight <= 0.40）時，KD/RSI/MACD 等短線指標嚴重失真；
        // 此時「千張大戶籌碼」等抗噪性強的維度應獲更高的話語權，直接對 money 分數加成 ×1.2。
        if (confidenceWeight <= 0.40) {
            result.money = Math.min(100, (int) Math.round(result.money * 1.2));
            result.noiseCompensationApplied = true;
        }
        // ────────────────────────────────────────────────────────────────────────

        // ── 融資融券風控整合（信用交易子維度）──────────────────────────────────────
        // 從 FinMind 取得融資融券歷史，計算「籌碼沉澱」與「軋空潛力」兩大指標；
        // 評分直接疊加至 money 維度，正分拉高（主力易拉抬），負分強烈向內縮（散戶套牢）。
        try {
            List<FinMindMarginData> marginHistory =
                    finMindClient.fetchMarginHistory(cleanSymbol, 10);
            if (marginHistory != null && marginHistory.size() >= 5) {
                int marginScore = marginAnalysisService.calculateMarginScore(marginHistory);

                // 疊加至 money 分（限制在 0-100 範圍）
                result.money = Math.max(0, Math.min(100, result.money + marginScore));

                // 填充前端 AI 診斷用的細節物件
                FinMindMarginData today = marginHistory.get(marginHistory.size() - 1);
                FinMindMarginData p5    = marginHistory.get(marginHistory.size() - 5);
                RadarScoreResult.MarginDetail md = new RadarScoreResult.MarginDetail();
                md.marginScore            = marginScore;
                md.marginPurchaseLimit    = today.getMarginPurchaseLimit();
                md.shortSaleLimit         = today.getShortSaleLimit();
                md.shortToMarginRatioPct  = today.getMarginPurchaseLimit() > 0
                        ? today.getShortSaleLimit() * 100.0 / today.getMarginPurchaseLimit()
                        : 0.0;
                md.marginLimitP5          = p5.getMarginPurchaseLimit();
                md.shortSqueezeAlert      = marginScore > 0 && md.shortToMarginRatioPct >= 30.0;
                md.retailTrapWarning      = marginScore < 0;
                if (md.retailTrapWarning) {
                    md.marginWeekTrend = "SURGING";
                } else if (marginScore > 0 && !md.shortSqueezeAlert) {
                    md.marginWeekTrend = "DECREASING";
                } else {
                    md.marginWeekTrend = "STABLE";
                }
                result.marginDetail = md;
            }
        } catch (Exception e) {
            // FinMind 取得失敗不影響整體評分，僅記錄 log
            java.util.logging.Logger.getLogger(RadarService.class.getName())
                    .fine("⚠️  融資融券資料取得失敗（" + cleanSymbol + "）: " + e.getMessage());
        }
        // ────────────────────────────────────────────────────────────────────────

        result.moneySource = new RadarScoreResult.MoneySource();
        result.moneySource.trustDays = trustDays;
        result.moneySource.lockRatioPct = lockRatioPct;
        result.moneySource.latestNetBuy = latestNetBuy;
        result.moneySource.avgNetBuy5 = avgNetBuy5;
        result.moneySource.institutionalMoneyScore = institutionalMoneyScore;
        result.moneySource.bigHolderNormalizedScore = bigHolderNormalizedScore;
        result.moneySource.institutionalWeight = 0.70;
        result.moneySource.bigHolderWeight = 0.30;
        result.moneySource.noiseCompensationApplied = result.noiseCompensationApplied;
        result.moneySource.noiseConfidenceWeight = confidenceWeight;
        MoneyDistributionSnapshot snapshot = getMoneyDistributionSnapshot();
        result.moneySource.moneyPercentile = snapshot.percentileRank(result.money);
        result.moneySource.moneyPercentileSampleSize = snapshot.size();

        List<Double> bbwSeries = indicatorCalculator.calculateBBWSeries(cleanSymbol, 60);
        double bbw = bbwSeries.isEmpty() ? 0.0 : bbwSeries.get(bbwSeries.size() - 1);
        double bbwPrev = bbwSeries.size() >= 2 ? bbwSeries.get(bbwSeries.size() - 2) : bbw;
        double bbwMin = bbwSeries.isEmpty() ? 0.0 : bbwSeries.stream().mapToDouble(v -> v).min().orElse(0.0);
        double bbwMax = bbwSeries.isEmpty() ? 0.0 : bbwSeries.stream().mapToDouble(v -> v).max().orElse(0.0);
        boolean opening = bbw > bbwPrev;

        result.volatility = scoreEngine.scoreVolatility(bbw, bbwMin, bbwMax, opening);

        MarketBreadthResult breadth = marketBreadthService.calculateMarketBreadth();
        result.context = scoreEngine.scoreContext(breadth.getBreadth());

        if (result.etfMode) {
            result.fundamental = 0;
        } else {
            List<RevenueData> revenueHistory = fundamentalService.getRevenueHistoryForScoring(cleanSymbol, 6);
            // 精細化算分：月營收(8分) + 三率三升(6分) + 庫存/合約負債(6分) = 20分上限
            List<FinancialQuarterData> quarterHistory = advancedFundamentalService.getQuarterHistory(cleanSymbol, 4);
            int refinedScore = advancedFundamentalService.calculateRefinedFundamentalScore(revenueHistory, quarterHistory);
            RevenueData latestRevenue = revenueHistory.isEmpty() ? null : revenueHistory.get(revenueHistory.size() - 1);
            double latestYoy = latestRevenue == null ? 0.0 : latestRevenue.getYoy();
            double latestMom = latestRevenue == null ? 0.0 : latestRevenue.getMom();
            result.fundamental = scoreEngine.scoreFundamental(refinedScore, latestYoy, latestMom);
            // 填充基本面細部旗標（供前端 AI 診斷報告使用）
            result.fundamentalDetail = advancedFundamentalService.buildFundamentalDetail(
                    revenueHistory, quarterHistory, refinedScore);
        }

        // 消息面：raw(-15~15) 正規化為 0~100，並附上情緒/報酬/新聞數細節
        result.news = 50;
        RadarScoreResult.NewsDetail newsDetail = new RadarScoreResult.NewsDetail();
        newsDetail.rawScore = 0;
        newsDetail.sentiment = 0.0;
        newsDetail.todayReturn = 0.0;
        newsDetail.dayTradingRate = result.dayTradingRate;
        newsDetail.newsCount = 0;
        try {
            NewsReactionService.NewsReactionResult reaction = newsReactionService.evaluateTodayReaction(cleanSymbol);
            if (reaction != null) {
                newsDetail.rawScore = reaction.score();
                newsDetail.sentiment = reaction.todaySentiment();
                newsDetail.todayReturn = reaction.todayReturn();
                newsDetail.dayTradingRate = reaction.dayTradingRate();
                newsDetail.newsCount = reaction.newsCount();
                result.news = scoreEngine.scoreNewsReaction(reaction.score());
            }
        } catch (Exception e) {
            java.util.logging.Logger.getLogger(RadarService.class.getName())
                    .fine("⚠️  消息面資料取得失敗（" + cleanSymbol + "）: " + e.getMessage());
        }
        result.newsDetail = newsDetail;

        // 計算加權綜合評分（加入當沖降噪：高噪音時降低動能權重、提高趨勢/籌碼權重）
        NoiseFilterService.RadarWeightProfile weightProfile = noiseFilterService.resolveRadarWeightProfile(result.dayTradingRate);
        double trendWeight = baseProfile.trendWeight() * weightProfile.trendFactor();
        double momentumWeight = baseProfile.momentumWeight() * weightProfile.momentumFactor();
        double moneyWeight = baseProfile.moneyWeight() * weightProfile.moneyFactor();
        double volatilityWeight = baseProfile.volatilityWeight();
        double contextWeight = baseProfile.contextWeight();
        double fundamentalWeight = result.etfMode ? 0.0 : baseProfile.fundamentalWeight();
        double newsWeight = baseProfile.newsWeight();
        double weightSum = trendWeight + momentumWeight + moneyWeight + volatilityWeight + contextWeight + fundamentalWeight + newsWeight;
        if (weightSum <= 0.0) {
            weightSum = 1.0;
        }

        trendWeight /= weightSum;
        momentumWeight /= weightSum;
        moneyWeight /= weightSum;
        volatilityWeight /= weightSum;
        contextWeight /= weightSum;
        fundamentalWeight /= weightSum;
        newsWeight /= weightSum;

        result.totalScore = (int) Math.round(
                result.trend      * trendWeight +
                result.momentum   * momentumWeight +
                result.money      * moneyWeight +
                result.volatility * volatilityWeight +
                result.context    * contextWeight +
                result.fundamental* fundamentalWeight +
                result.news       * newsWeight
        );
        result.totalScore = Math.max(0, Math.min(100, result.totalScore));

        // 標的類型標籤
        result.label = resolveLabel(result);

        // 綜合結論
        result.conclusion = resolveConclusion(result, rsi, trustDays);

        // 戰術分析：根據六軸分佈形狀判定機構級幾何圖形與操盤策略
        RadarTacticalAnalyzer tacticalAnalyzer = new RadarTacticalAnalyzer();

        // 填充額外上下文（供 ETF 路由、殭屍股否決、利多陷阱等細部判斷）
        RadarTacticalAnalyzer.TacticalContext tacticalCtx = new RadarTacticalAnalyzer.TacticalContext();
        tacticalCtx.etfMode                  = result.etfMode;
        tacticalCtx.dayTradingRate           = result.dayTradingRate;
        tacticalCtx.newsCount                = newsDetail.newsCount;
        tacticalCtx.noiseCompensationApplied = result.noiseCompensationApplied;
        tacticalCtx.shortSqueezeAlert        = result.marginDetail != null && result.marginDetail.shortSqueezeAlert;
        tacticalCtx.retailTrapWarning        = result.marginDetail != null && result.marginDetail.retailTrapWarning;
        // 52週相對位置：從 context(D軸) 反算；context 分數越高=大盤越好，priceLocation 取互補
        tacticalCtx.priceLocation            = Math.max(0.0, Math.min(1.0, 1.0 - result.context / 100.0));

        result.tacticalAnalysis = tacticalAnalyzer.analyzeTactical(
                result.fundamental,   // A: 基本面
                result.momentum,      // B: 技術面
                result.volatility,    // C: 波動爆發
                result.context,       // D: 風控基期
                result.money,         // E: 籌碼結構
                result.news,          // F: 消息輿情
                tacticalCtx
        );

        return result;
    }

    private MoneyDistributionSnapshot getMoneyDistributionSnapshot() {
        long now = System.currentTimeMillis();
        if (now - moneyPercentileCachedAtMs < MONEY_PERCENTILE_CACHE_MS && cachedMoneyDistribution.size() > 0) {
            return cachedMoneyDistribution;
        }

        synchronized (moneyPercentileLock) {
            now = System.currentTimeMillis();
            if (now - moneyPercentileCachedAtMs < MONEY_PERCENTILE_CACHE_MS && cachedMoneyDistribution.size() > 0) {
                return cachedMoneyDistribution;
            }
            cachedMoneyDistribution = buildMoneyDistributionSnapshot();
            moneyPercentileCachedAtMs = now;
            return cachedMoneyDistribution;
        }
    }

    private MoneyDistributionSnapshot buildMoneyDistributionSnapshot() {
        List<String> symbols = stockUniverseRepository.getAllSymbols();
        if (symbols == null || symbols.isEmpty()) {
            return new MoneyDistributionSnapshot(List.of());
        }

        List<Integer> fusedScores = new ArrayList<>();
        for (String symbol : symbols) {
            if (symbol == null || symbol.isBlank()) {
                continue;
            }
            int moneyScore = calculateLocalFusedMoneyScore(symbol);
            if (moneyScore >= 0) {
                fusedScores.add(moneyScore);
            }
        }

        return new MoneyDistributionSnapshot(fusedScores);
    }

    private int calculateLocalFusedMoneyScore(String symbol) {
        List<InstitutionalTrade> trades =
                institutionalDataRepository.getRecentInstitutionalTrades(symbol, 10);
        if (trades.isEmpty()) {
            return -1;
        }

        int trustDays = 0;
        double trustVolumeSum = 0.0;
        double volumeSum = 0.0;
        for (int i = trades.size() - 1; i >= 0; i--) {
            InstitutionalTrade trade = trades.get(i);
            long trustBuy = trade.getTrustBuy();
            if (trustBuy <= 0) {
                break;
            }
            trustDays++;
            trustVolumeSum += trustBuy;
            volumeSum += Math.max(0L, trade.getDailyVolume());
        }

        double lockRatioPct = volumeSum > 0 ? (trustVolumeSum / volumeSum) * 100.0 : 0.0;
        long latestNetBuy = trades.get(trades.size() - 1).getTotalNetBuy();
        double avgNetBuy5 = trades.stream()
                .skip(Math.max(0, trades.size() - 5L))
                .mapToLong(InstitutionalTrade::getTotalNetBuy)
                .average()
                .orElse(0.0);

        List<FinMindShareholdingData> holderHistory =
                institutionalDataRepository.getLargeHolderShareholdingHistory(symbol, 8);
        int bigHolderScore = institutionalService.calculateBigHolderScore(holderHistory);

        return scoreEngine.scoreMoney(trustDays, lockRatioPct, latestNetBuy, avgNetBuy5, bigHolderScore);
    }

    private static final class MoneyDistributionSnapshot {
        private final List<Integer> sortedScores;

        private MoneyDistributionSnapshot(List<Integer> scores) {
            List<Integer> copied = new ArrayList<>(scores);
            Collections.sort(copied);
            this.sortedScores = copied;
        }

        private int size() {
            return sortedScores.size();
        }

        private double percentileRank(int score) {
            if (sortedScores.isEmpty()) {
                return 50.0;
            }
            int upperBound = upperBound(score);
            return upperBound * 100.0 / sortedScores.size();
        }

        private int upperBound(int score) {
            int left = 0;
            int right = sortedScores.size();
            while (left < right) {
                int mid = (left + right) >>> 1;
                if (sortedScores.get(mid) <= score) {
                    left = mid + 1;
                } else {
                    right = mid;
                }
            }
            return left;
        }
    }

    private String resolveLabel(RadarScoreResult r) {
        int[] values = { r.trend, r.momentum, r.money, r.volatility, r.context, r.fundamental, r.news };
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, sum = 0;
        for (int v : values) { if (v < min) min = v; if (v > max) max = v; sum += v; }
        double avg = sum / (double) values.length;

        if (min >= 65) return "全才型";
        if (max - min >= 40) return "偏科型";
        if (avg >= 60) return "均衡強勢";
        if (avg >= 40) return "均衡中性";
        return "均衡弱勢";
    }

    private String resolveConclusion(RadarScoreResult r, double rsi, int trustDays) {
        if (r.totalScore >= 82) {
            return "💎 完美共振：六維全面轉強，大數法則顯示為極高勝率標的！";
        }
        if (r.money > 80 && r.trend < 45) {
            return "⚠️ 潛伏佈局：投信正瘋狂鎖碼（連買 " + trustDays + " 天），股價尚未發動，適合領先佈局。";
        }
        if (r.fundamental > 75 && r.trend > 65) {
            return "📈 基本面共振：月營收動能與技術趨勢同步轉強，屬於基本面加速區。";
        }
        if (r.momentum > 85 && r.volatility > 75) {
            return "🚀 爆發訊號：動能與波動率同步噴發，屬於強勢攻擊區，注意停利位置。";
        }
        if (r.context < 30) {
            return "❄️ 寒冬警示：個股雖有亮點，但市場環境極差，建議降至 1/4 倉位。";
        }
        if (r.trend > 70 && r.momentum > 70) {
            return "✅ 多頭格局：趨勢與動能同步轉強，可順勢持有並設停利。";
        }
        if (r.totalScore >= 55) {
            return "👀 值得追蹤：多數面向向好，持續觀察量能突破確認。";
        }
        if (r.totalScore >= 35) {
            return "⏸️ 中性觀望：訊號尚未明確，等待方向定型後再介入。";
        }
        if (rsi < 28 && r.trend < 35) {
            return "💡 超賣反彈：技術面極度超賣，可能出現短線反彈，不宜重倉。";
        }
        return "⛔ 偏空格局：多數指標偏弱，建議空手等待趨勢扭轉再操作。";
    }

    private double resolveLatestDayTradingRate(String symbol) {
        List<FinMindDayTradingData> history = dayTradingService.getDayTradingHistory(symbol, 5);
        if (history.isEmpty()) {
            return 0.0;
        }
        FinMindDayTradingData latest = history.get(history.size() - 1);
        return latest == null ? 0.0 : latest.getDayTradingRate();
    }

    private int clampToScore(double value) {
        return (int) Math.round(Math.max(0.0, Math.min(100.0, value)));
    }

    private boolean isEtfSymbol(String symbol) {
        if (symbol == null) {
            return false;
        }
        String clean = symbol.trim();
        StockUniverseEntry entry = stockUniverseRepository.getStockUniverseEntry(clean);
        if (entry != null) {
            return entry.isEtf() || "BOND_ETF".equalsIgnoreCase(entry.getAssetType()) || "ETN".equalsIgnoreCase(entry.getAssetType());
        }
        return clean.matches("^00[0-9A-Z]{2,5}$") || clean.matches("^02\\d{3,5}$");
    }

    private String resolveAssetType(StockUniverseEntry entry) {
        if (entry == null || entry.getAssetType() == null || entry.getAssetType().isBlank()) {
            return "STOCK";
        }
        return entry.getAssetType().trim().toUpperCase();
    }

    private AssetWeightProfile resolveAssetWeightProfile(String assetType, boolean etfMode) {
        String normalized = assetType == null ? "STOCK" : assetType.trim().toUpperCase();
        return switch (normalized) {
            case "ETF" -> new AssetWeightProfile(etfTrendWeight, etfMomentumWeight, etfMoneyWeight,
                    etfVolatilityWeight, etfContextWeight, etfMode ? 0.0 : etfFundamentalWeight, etfNewsWeight,
                    "ETF 趨勢雷達",
                    "強化趨勢、環境與消息面，降低短線動能噪音，營收維度預設排除。"
            );
            case "BOND_ETF" -> new AssetWeightProfile(bondEtfTrendWeight, bondEtfMomentumWeight, bondEtfMoneyWeight,
                    bondEtfVolatilityWeight, bondEtfContextWeight, bondEtfFundamentalWeight, bondEtfNewsWeight,
                    "債券 ETF 防守雷達",
                    "降低動能權重，提升波動、環境與消息面解讀，偏向穩定防守配置。"
            );
            case "ETN" -> new AssetWeightProfile(etnTrendWeight, etnMomentumWeight, etnMoneyWeight,
                    etnVolatilityWeight, etnContextWeight, etnFundamentalWeight, etnNewsWeight,
                    "ETN 波動雷達",
                    "提高波動構面占比，並納入消息面佐證，適合高波動結構商品。"
            );
            default -> new AssetWeightProfile(stockTrendWeight, stockMomentumWeight, stockMoneyWeight,
                    stockVolatilityWeight, stockContextWeight, etfMode ? 0.0 : stockFundamentalWeight, stockNewsWeight,
                    "標準股票雷達",
                    "七維均衡評估趨勢、動能、籌碼、波動、環境、營收與消息面，適用一般股票。"
            );
        };
    }

    private record AssetWeightProfile(double trendWeight,
                                      double momentumWeight,
                                      double moneyWeight,
                                      double volatilityWeight,
                                      double contextWeight,
                                      double fundamentalWeight,
                                      double newsWeight,
                                      String strategyMode,
                                      String strategyDescription) {}
}
