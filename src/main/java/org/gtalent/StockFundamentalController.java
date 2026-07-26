package org.gtalent;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stocks")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"})
public class StockFundamentalController {
    private final FundamentalService fundamentalService;
    private final InstitutionalService institutionalService;
    private final DayTradingService dayTradingService;
    private final StockHistoryService stockHistoryService;
    private final ScoreEngine scoreEngine;

    public StockFundamentalController(FundamentalService fundamentalService,
                                      InstitutionalService institutionalService,
                                      DayTradingService dayTradingService,
                                      StockHistoryService stockHistoryService,
                                      ScoreEngine scoreEngine) {
        this.fundamentalService = fundamentalService;
        this.institutionalService = institutionalService;
        this.dayTradingService = dayTradingService;
        this.stockHistoryService = stockHistoryService;
        this.scoreEngine = scoreEngine;
    }

    @GetMapping("/{symbol}/revenue")
    public Map<String, Object> getRevenueHistory(@PathVariable("symbol") String symbol,
                                                 @RequestParam(name = "months", defaultValue = "12") int months) {
        String clean = normalizeSymbol(symbol);
        int safeMonths = Math.max(1, Math.min(months, 120));
        int backfilled = fundamentalService.backfillRevenueHistory(clean, safeMonths);
        List<RevenueData> history = fundamentalService.getRevenueHistory(clean, safeMonths);
        int revenueScore = fundamentalService.calculateRevenueScore(history);
        RevenueData latest = history.isEmpty() ? null : history.get(history.size() - 1);

        Map<String, Object> result = new HashMap<>();
        result.put("symbol", clean);
        result.put("months", safeMonths);
        result.put("history", history);
        result.put("revenueScore", revenueScore);
        result.put("fundamentalScore", scoreFundamental(history, revenueScore));
        result.put("sampleSize", history.size());
        result.put("backfilledCount", backfilled);
        result.put("latest", latest);
        return result;
    }

    @GetMapping("/{symbol}/revenue/history")
    public Map<String, Object> getRevenueHistoryApi(@PathVariable("symbol") String symbol,
                                                    @RequestParam(name = "months", defaultValue = "24") int months) {
        return getRevenueHistory(symbol, months);
    }

    @GetMapping("/{symbol}/institutional")
    public List<InstitutionalTrade> getInstitutional(@PathVariable("symbol") String symbol,
                                                     @RequestParam(name = "limit", defaultValue = "60") int limit) {
        String clean = normalizeSymbol(symbol);
        int safeLimit = limit > 0 ? limit : 60;
        return institutionalService.getRecentInstitutionalTrades(clean, safeLimit);
    }

    @GetMapping("/{symbol}/day-trading")
    public Map<String, Object> getDayTrading(@PathVariable("symbol") String symbol,
                                             @RequestParam(name = "days", defaultValue = "60") int days) {
        String clean = normalizeSymbol(symbol);
        int safeDays = Math.max(1, Math.min(days, 180));
        List<FinMindDayTradingData> history = dayTradingService.getDayTradingHistory(clean, safeDays);

        FinMindDayTradingData latest = history.isEmpty() ? null : history.get(history.size() - 1);
        Map<String, Object> result = new HashMap<>();
        result.put("symbol", clean);
        result.put("days", safeDays);
        result.put("history", history);
        result.put("sampleSize", history.size());
        result.put("latest", latest);
        result.put("dataset", FinMindDataset.DAY_TRADE.getDatasetName());
        return result;
    }

    @GetMapping("/{symbol}/big-holders")
    public List<FinMindShareholdingData> getBigHolders(@PathVariable("symbol") String symbol,
                                                       @RequestParam(name = "weeks", defaultValue = "16") int weeks) {
        String clean = normalizeSymbol(symbol);
        int safeWeeks = Math.max(4, Math.min(weeks, 52));
        return institutionalService.getLargeHolderShareholdingHistory(clean, safeWeeks);
    }

    @GetMapping("/{symbol}/big-holder-diagnosis")
    public Map<String, Object> getBigHolderDiagnosis(@PathVariable("symbol") String symbol,
                                                     @RequestParam(name = "weeks", defaultValue = "16") int weeks) {
        String clean = normalizeSymbol(symbol);
        int safeWeeks = Math.max(4, Math.min(weeks, 52));

        List<FinMindShareholdingData> history =
                institutionalService.getLargeHolderShareholdingHistory(clean, safeWeeks);
        int score = institutionalService.calculateBigHolderScore(history);

        Map<String, Object> response = new HashMap<>();
        response.put("symbol", clean);
        response.put("score", score);
        response.put("sampleSize", history.size());

        if (history.size() < 3) {
            response.put("signal", "insufficient");
            response.put("conclusion", "大戶週資料不足，暫無法判定集中趨勢。");
            return response;
        }

        FinMindShareholdingData w0 = history.get(history.size() - 1);
        FinMindShareholdingData w1 = history.get(history.size() - 2);
        FinMindShareholdingData w2 = history.get(history.size() - 3);
        boolean holdersUpTrend =
                w0.getPercentage() > w1.getPercentage() && w1.getPercentage() > w2.getPercentage();

        List<StockDataPoint> prices = stockHistoryService.getRecentHistory(clean, 35);
        boolean priceWeak = false;
        if (prices.size() >= 10) {
            double latest = prices.get(prices.size() - 1).price;
            double baseline = prices.get(Math.max(0, prices.size() - 10)).price;
            priceWeak = latest <= baseline;
        }

        boolean advancedDivergence = holdersUpTrend && priceWeak;
        response.put("holdersUpTrend", holdersUpTrend);
        response.put("priceWeak", priceWeak);
        response.put("advancedDivergence", advancedDivergence);
        response.put("latestPercentage", w0.getPercentage());
        response.put("previousPercentage", w1.getPercentage());

        String conclusion;
        if (advancedDivergence) {
            conclusion = "🔥 籌碼進階背離：股價盤整/走弱，但千張大戶持股比連續走高，留意噴發前兆。";
        } else if (holdersUpTrend) {
            conclusion = "📈 大戶持股連續上升，籌碼仍在集中，偏多觀察。";
        } else if (w0.getPercentage() > w1.getPercentage()) {
            conclusion = "🟡 本週大戶持股回升，但趨勢仍需更多週資料確認。";
        } else {
            conclusion = "⚖️ 大戶持股集中趨勢未明，建議搭配價量與法人資料確認。";
        }

        response.put("signal", advancedDivergence ? "burst-warning" : (holdersUpTrend ? "concentrating" : "neutral"));
        response.put("conclusion", conclusion);
        return response;
    }

    private int scoreFundamental(List<RevenueData> history, int revenueScore) {
        RevenueData latest = history.isEmpty() ? null : history.get(history.size() - 1);
        double latestYoy = latest == null ? 0.0 : latest.getYoy();
        double latestMom = latest == null ? 0.0 : latest.getMom();
        return scoreEngine.scoreFundamental(revenueScore, latestYoy, latestMom);
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim();
    }
}
