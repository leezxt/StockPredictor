package org.gtalent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

@Service
public class NewsReactionService {
    private static final Logger logger = Logger.getLogger(NewsReactionService.class.getName());

    @Value("${news.sentiment.keywords.positive:創高,成長,突破,上修,利多,擴產,訂單,大增,買超,看好,獲利,增資,調升,回購,轉盈,強勁}")
    private String positiveKeywordsConfig = "創高,成長,突破,上修,利多,擴產,訂單,大增,買超,看好,獲利,增資,調升,回購,轉盈,強勁";

    @Value("${news.sentiment.keywords.negative:下修,虧損,利空,衰退,違約,裁員,賣超,減產,下滑,爆雷,訴訟,警示,跌停,調降,大跌,風險}")
    private String negativeKeywordsConfig = "下修,虧損,利空,衰退,違約,裁員,賣超,減產,下滑,爆雷,訴訟,警示,跌停,調降,大跌,風險";

    @Value("${news.sentiment.keyword-weight.title:1.20}")
    private double titleKeywordWeight = 1.20;

    @Value("${news.sentiment.keyword-weight.content:1.00}")
    private double contentKeywordWeight = 1.00;

    @Value("${news.reaction.trap.sentiment-threshold:0.50}")
    private double trapSentimentThreshold = 0.50;

    @Value("${news.reaction.trap.return-threshold:-0.01}")
    private double trapReturnThreshold = -0.01;

    @Value("${news.reaction.trap.day-trading-threshold:0.65}")
    private double trapDayTradingThreshold = 0.65;

    @Value("${news.reaction.trap.score:-15}")
    private int trapScore = -15;

    @Value("${news.reaction.breakout.sentiment-threshold:0.40}")
    private double breakoutSentimentThreshold = 0.40;

    @Value("${news.reaction.breakout.return-threshold:0.04}")
    private double breakoutReturnThreshold = 0.04;

    @Value("${news.reaction.breakout.day-trading-threshold:0.35}")
    private double breakoutDayTradingThreshold = 0.35;

    @Value("${news.reaction.breakout.score:15}")
    private int breakoutScore = 15;

    @Value("${news.reaction.resilience.sentiment-threshold:-0.40}")
    private double resilienceSentimentThreshold = -0.40;

    @Value("${news.reaction.resilience.return-threshold:-0.005}")
    private double resilienceReturnThreshold = -0.005;

    @Value("${news.reaction.resilience.score:12}")
    private int resilienceScore = 12;

    private final FinMindClient finMindClient;
    private final DayTradingService dayTradingService;
    private final StockDataRepository stockDataRepository;

    public NewsReactionService(
            FinMindClient finMindClient,
            DayTradingService dayTradingService,
            StockDataRepository stockDataRepository
    ) {
        this.finMindClient = finMindClient;
        this.dayTradingService = dayTradingService;
        this.stockDataRepository = stockDataRepository;
    }

    /**
     * 針對單一股票計算當日消息面回應結果。
     */
    public NewsReactionResult evaluateTodayReaction(String symbol) {
        String cleanSymbol = symbol == null ? "" : symbol.trim();
        if (cleanSymbol.isBlank()) {
            return NewsReactionResult.empty();
        }

        String today = LocalDate.now().toString();
        List<NewsData> newsList = finMindClient != null
                ? finMindClient.fetchStockNews(cleanSymbol, today)
                : List.of();
        for (NewsData news : newsList) {
            if (news == null) {
                continue;
            }
            news.setSentimentPolarity(calculateSentiment(news));
        }

        double todaySentiment = calculateAverageSentiment(newsList);
        double todayReturn = resolveTodayReturn(cleanSymbol);
        double dayTradingRate = resolveLatestDayTradingRate(cleanSymbol);
        int score = calculateNewsReactionScore(todaySentiment, todayReturn, dayTradingRate);

        logger.info(String.format(
                "[NewsReaction] %s sentiment=%.4f, return=%.4f, dayTradingRate=%.4f, score=%d, news=%d",
                cleanSymbol,
                todaySentiment,
                todayReturn,
                dayTradingRate,
                score,
                newsList.size()
        ));

        return new NewsReactionResult(todaySentiment, todayReturn, dayTradingRate, score, newsList.size());
    }

    /**
     * 計算消息面回應指數。
     *
     * @param todaySentiment 平均新聞情緒 (1為極好，-1為極壞)
     * @param todayReturn 當日股價漲跌幅 (例如 0.05 代表漲 5%)
     * @param dayTradingRate 當日當沖率 (用來輔助判斷是否為虛胖)
     * @return 消息面最終得分 (最高 15 分，地雷情境可到 -15)
     */
    public int calculateNewsReactionScore(double todaySentiment, double todayReturn, double dayTradingRate) {
        int score = 0;

        if (todaySentiment > trapSentimentThreshold
                && (todayReturn < trapReturnThreshold || dayTradingRate > trapDayTradingThreshold)) {
            return trapScore;
        }

        if (todaySentiment > breakoutSentimentThreshold
                && todayReturn > breakoutReturnThreshold
                && dayTradingRate < breakoutDayTradingThreshold) {
            score += breakoutScore;
        }

        if (todaySentiment < resilienceSentimentThreshold && todayReturn >= resilienceReturnThreshold) {
            score += resilienceScore;
        }

        return score;
    }

    public double calculateAverageSentiment(List<NewsData> newsList) {
        if (newsList == null || newsList.isEmpty()) {
            return 0.0;
        }

        double sum = 0.0;
        int count = 0;
        for (NewsData item : newsList) {
            if (item == null) {
                continue;
            }
            sum += item.getSentimentPolarity();
            count++;
        }
        if (count == 0) {
            return 0.0;
        }

        double avg = sum / count;
        return Math.max(-1.0, Math.min(1.0, avg));
    }

    private double calculateSentiment(NewsData news) {
        if (news == null) {
            return 0.0;
        }

        String title = normalizeText(news.getTitle());
        String content = normalizeText(news.getContent());
        if (title.isBlank() && content.isBlank()) {
            return 0.0;
        }

        Set<String> positiveKeywords = parseKeywords(positiveKeywordsConfig);
        Set<String> negativeKeywords = parseKeywords(negativeKeywordsConfig);

        double positive = countKeywordHits(title, positiveKeywords) * sanitizeWeight(titleKeywordWeight)
                + countKeywordHits(content, positiveKeywords) * sanitizeWeight(contentKeywordWeight);
        double negative = countKeywordHits(title, negativeKeywords) * sanitizeWeight(titleKeywordWeight)
                + countKeywordHits(content, negativeKeywords) * sanitizeWeight(contentKeywordWeight);

        if (positive <= 0.0 && negative <= 0.0) {
            return 0.0;
        }

        double score = (positive - negative) / (positive + negative);
        return Math.max(-1.0, Math.min(1.0, score));
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.toLowerCase();
    }

    private Set<String> parseKeywords(String rawConfig) {
        if (rawConfig == null || rawConfig.isBlank()) {
            return Set.of();
        }
        Set<String> parsed = new LinkedHashSet<>();
        Arrays.stream(rawConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(String::toLowerCase)
                .forEach(parsed::add);
        return parsed;
    }

    private double sanitizeWeight(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0.0) {
            return 1.0;
        }
        return value;
    }

    private int countKeywordHits(String text, Set<String> keywords) {
        if (text == null || text.isBlank() || keywords == null || keywords.isEmpty()) {
            return 0;
        }
        int hits = 0;
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase())) {
                hits++;
            }
        }
        return hits;
    }

    private double resolveTodayReturn(String symbol) {
        List<StockDataPoint> history = stockDataRepository.getFullHistory(symbol, 2);
        if (history.isEmpty()) {
            return 0.0;
        }

        StockDataPoint latest = history.get(history.size() - 1);
        if (latest == null || latest.c <= 0.0) {
            return 0.0;
        }

        if (latest.o > 0.0) {
            return (latest.c - latest.o) / latest.o;
        }

        if (history.size() >= 2) {
            StockDataPoint previous = history.get(history.size() - 2);
            if (previous != null && previous.c > 0.0) {
                return (latest.c - previous.c) / previous.c;
            }
        }

        return 0.0;
    }

    private double resolveLatestDayTradingRate(String symbol) {
        if (dayTradingService == null) {
            return 0.0;
        }

        List<FinMindDayTradingData> history = dayTradingService.getDayTradingHistory(symbol, 3);
        if (history.isEmpty()) {
            return 0.0;
        }

        FinMindDayTradingData latest = history.get(history.size() - 1);
        if (latest == null) {
            return 0.0;
        }

        double rawRate = latest.getDayTradingRate();
        if (rawRate > 1.0) {
            return rawRate / 100.0;
        }
        if (rawRate < 0.0) {
            return 0.0;
        }
        return rawRate;
    }

    public record NewsReactionResult(
            double todaySentiment,
            double todayReturn,
            double dayTradingRate,
            int score,
            int newsCount
    ) {
        public static NewsReactionResult empty() {
            return new NewsReactionResult(0.0, 0.0, 0.0, 0, 0);
        }
    }
}

