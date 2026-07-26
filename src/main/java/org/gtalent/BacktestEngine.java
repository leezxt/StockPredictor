package org.gtalent;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 簡易回測引擎：
 * - long-only（只做多）
 * - 黃金交叉買進 / 死亡交叉賣出
 * - 訊號於當日收盤確認，於次日開盤成交
 * - 若最後仍持有部位，使用期末收盤價強制結算
 */
@Service
public class BacktestEngine {
    private static final double DEFAULT_INITIAL_CAPITAL = 100_000.0;
    private final StockDataRepository stockDataRepository;

    public BacktestEngine(StockDataRepository stockDataRepository) {
        this.stockDataRepository = stockDataRepository;
    }

    public BacktestResult runMovingAverageCrossover(String symbol,
                                                    int shortWindow,
                                                    int longWindow,
                                                    int lookbackDays) {
        return runMovingAverageCrossover(symbol, shortWindow, longWindow, lookbackDays, DEFAULT_INITIAL_CAPITAL);
    }

    public void runBacktest(String symbol) {
        BacktestResult result = runDiagnosisBacktest(symbol, 250, DEFAULT_INITIAL_CAPITAL);

        System.out.println("--- 回測報告 ---");
        System.out.println("股票代碼: " + result.symbol);
        System.out.println("策略: " + result.strategy);
        System.out.println("回測區間: " + result.startDate + " ~ " + result.endDate);
        System.out.println("最終資產: " + result.finalCapital);
        System.out.println("總報酬率: " + result.totalReturnPct + "%");
        System.out.println("總交易次數: " + result.tradeCount);
        System.out.println("勝率: " + result.winRate + "%");
        System.out.println("Sortino: " + result.sortinoRatio);
        System.out.println("Sharpe: " + result.sharpeRatio);
        System.out.println("Calmar: " + result.calmarRatio);
        System.out.println("Ulcer: " + result.ulcerIndex);
        System.out.println("Treynor: " + result.treynorRatio);
        System.out.println("Information Ratio: " + result.informationRatio);
        System.out.println("最後訊號: " + result.lastSignal);
        if (result.note != null && !result.note.isBlank()) {
            System.out.println("備註: " + result.note);
        }
    }

    public BacktestResult runDiagnosisBacktest(String symbol, int lookbackDays, double initialCapital) {
        String cleanSymbol = symbol == null ? "" : symbol.trim();
        int fetchDays = Math.max(lookbackDays, 250) + 60;
        List<StockDataPoint> fullHistory = stockDataRepository.getFullHistory(cleanSymbol, fetchDays);

        BacktestResult result = baseResult(cleanSymbol, 5, 60, lookbackDays, initialCapital);
        result.strategy = "趨勢診斷策略";

        List<StockDataPoint> history = normalizeHistory(fullHistory);
        if (history.size() < 60) {
            result.note = "歷史資料不足，至少需要 60 筆資料才能執行診斷式回測。";
            return result;
        }

        if (lookbackDays > 0 && history.size() > lookbackDays) {
            history = new ArrayList<>(history.subList(history.size() - lookbackDays, history.size()));
        }
        if (history.size() < 60) {
            result.note = "指定區間資料不足，至少需要 60 筆資料才能執行診斷式回測。";
            return result;
        }

        double balance = initialCapital;
        int shares = 0;
        int totalTrades = 0;
        int winCount = 0;
        double lastBuyCost = 0.0;
        String lastSignal = "持觀";
        double totalProfit = 0.0;
        double totalLoss = 0.0;
        int lossCount = 0;
        List<Double> equityCurve = new ArrayList<>();
        equityCurve.add(initialCapital);

        for (int i = 59; i < history.size(); i++) {
            double currentPrice = getClosePrice(history.get(i));
            if (currentPrice <= 0) {
                continue;
            }

            String status = diagnoseForBacktest(history, i);
            double ma20 = calculateMA(history, i, 20);

            if (status.contains("多頭") && shares == 0) {
                shares = (int) (balance / currentPrice);
                if (shares <= 0) {
                    continue;
                }
                lastBuyCost = shares * currentPrice;
                balance -= lastBuyCost;
                lastSignal = "買進";
            } else if (shares > 0 && ma20 > 0 && currentPrice < ma20) {
                double sellRevenue = shares * currentPrice;
                double pnl = sellRevenue - lastBuyCost;
                if (pnl > 0) {
                    winCount++;
                    totalProfit += pnl;
                } else {
                    lossCount++;
                    totalLoss += Math.abs(pnl);
                }
                balance += sellRevenue;
                shares = 0;
                lastBuyCost = 0.0;
                totalTrades++;
                lastSignal = "賣出";
            }
            double equity = balance + (shares > 0 ? shares * currentPrice : 0.0);
            if (equity > 0) {
                equityCurve.add(equity);
            }
        }

        double endingPrice = getClosePrice(history.get(history.size() - 1));
        double finalCapital = balance;
        if (shares > 0 && endingPrice > 0) {
            double liquidationValue = shares * endingPrice;
            double pnl = liquidationValue - lastBuyCost;
            if (pnl > 0) {
                winCount++;
                totalProfit += pnl;
            } else {
                lossCount++;
                totalLoss += Math.abs(pnl);
            }
            finalCapital += liquidationValue;
            totalTrades++;
            lastSignal = "強制出場";
        }

        double benchmarkStart = getClosePrice(history.get(59));
        result.startDate = history.get(59).date;
        result.endDate = history.get(history.size() - 1).date;
        result.finalCapital = round2(finalCapital);
        result.totalReturnPct = round2(percentageChange(initialCapital, finalCapital));
        result.buyAndHoldReturnPct = round2(percentageChange(benchmarkStart, endingPrice));
        result.excessReturnPct = round2(result.totalReturnPct - result.buyAndHoldReturnPct);
        result.tradeCount = totalTrades;
        result.winCount = winCount;
        result.winRate = totalTrades > 0 ? round2((winCount * 100.0) / totalTrades) : 0.0;
        result.avgProfit = winCount > 0 ? round2(totalProfit / winCount) : 0.0;
        result.avgLoss = lossCount > 0 ? round2(totalLoss / lossCount) : 0.0;
        List<Double> benchmarkReturns = calculateBenchmarkReturnsFromHistory(history, 59);
        RiskMetrics metrics = calculateRiskMetrics(equityCurve, benchmarkReturns);
        result.sortinoRatio = round2(metrics.sortino());
        result.sharpeRatio = round2(metrics.sharpe());
        result.calmarRatio = round2(metrics.calmar());
        result.ulcerIndex = round2(metrics.ulcer());
        result.beta = round2(metrics.beta());
        result.treynorRatio = round2(metrics.treynor());
        result.informationRatio = round2(metrics.information());
        result.kellyPercent = KellyCalculator.fromBacktestResult(result);
        result.fullKellyPercent = round2(KellyCalculator.calculateFullKelly(
                result.winRate / 100.0, result.avgProfit, result.avgLoss) * 100.0);
        result.lastSignal = lastSignal;
        result.note = "策略：多頭診斷進場，跌破 MA20 出場；成交以當日收盤價模擬。";
        return result;
    }

    public BacktestResult runMovingAverageCrossover(String symbol,
                                                    int shortWindow,
                                                    int longWindow,
                                                    int lookbackDays,
                                                    double initialCapital) {
        validateParams(shortWindow, longWindow, initialCapital);

        String cleanSymbol = symbol == null ? "" : symbol.trim();
        int effectiveLookbackDays = Math.max(longWindow + 2, lookbackDays);
        int fetchDays = effectiveLookbackDays + longWindow + 5;

        List<StockDataPoint> history = stockDataRepository.getFullHistory(cleanSymbol, fetchDays);
        return runMovingAverageCrossover(cleanSymbol, history, shortWindow, longWindow, lookbackDays, initialCapital);
    }

    public static BacktestResult runMovingAverageCrossover(String symbol,
                                                           List<StockDataPoint> history,
                                                           int shortWindow,
                                                           int longWindow,
                                                           int lookbackDays,
                                                           double initialCapital) {
        validateParams(shortWindow, longWindow, initialCapital);

        BacktestResult result = baseResult(symbol, shortWindow, longWindow, lookbackDays, initialCapital);
        List<StockDataPoint> normalized = normalizeHistory(history);
        if (normalized.size() < longWindow + 2) {
            result.note = "歷史資料不足，至少需要 " + (longWindow + 2) + " 筆資料才能回測。";
            result.finalCapital = round2(initialCapital);
            result.lastSignal = "資料不足";
            return result;
        }

        int subsetStart = 0;
        if (lookbackDays > 0) {
            subsetStart = Math.max(0, normalized.size() - (lookbackDays + longWindow));
        }
        List<StockDataPoint> data = new ArrayList<>(normalized.subList(subsetStart, normalized.size()));
        if (data.size() < longWindow + 2) {
            result.note = "指定區間資料不足，無法完成回測。";
            result.finalCapital = round2(initialCapital);
            result.lastSignal = "資料不足";
            return result;
        }

        double[] closes = new double[data.size()];
        double[] opens = new double[data.size()];
        for (int i = 0; i < data.size(); i++) {
            closes[i] = getClosePrice(data.get(i));
            opens[i] = getOpenPrice(data.get(i));
        }

        double[] shortMa = calculateSmaSeries(closes, shortWindow);
        double[] longMa = calculateSmaSeries(closes, longWindow);

        double cash = initialCapital;
        double shares = 0.0;
        double entryCapital = 0.0;
        int tradeCount = 0;
        int winCount = 0;
        String lastSignal = "持觀";
        List<Double> equityCurve = new ArrayList<>();
        equityCurve.add(initialCapital);

        for (int i = longWindow; i < data.size() - 1; i++) {
            if (Double.isNaN(shortMa[i - 1]) || Double.isNaN(longMa[i - 1])
                    || Double.isNaN(shortMa[i]) || Double.isNaN(longMa[i])) {
                continue;
            }

            double nextOpen = opens[i + 1];
            if (nextOpen <= 0) {
                nextOpen = closes[i + 1];
            }
            if (nextOpen <= 0) {
                continue;
            }

            boolean goldenCross = shares == 0.0 && shortMa[i - 1] <= longMa[i - 1] && shortMa[i] > longMa[i];
            boolean deathCross = shares > 0.0 && shortMa[i - 1] >= longMa[i - 1] && shortMa[i] < longMa[i];

            if (goldenCross) {
                shares = cash / nextOpen;
                entryCapital = cash;
                cash = 0.0;
                lastSignal = "買進";
            } else if (deathCross) {
                cash = shares * nextOpen;
                if (cash > entryCapital) {
                    winCount++;
                }
                tradeCount++;
                shares = 0.0;
                entryCapital = 0.0;
                lastSignal = "賣出";
            }
            double equity = shares > 0.0 ? shares * closes[i + 1] : cash;
            if (equity > 0) {
                equityCurve.add(equity);
            }
        }

        double finalClose = closes[data.size() - 1];
        if (shares > 0.0) {
            cash = shares * finalClose;
            if (cash > entryCapital) {
                winCount++;
            }
            tradeCount++;
            lastSignal = "強制出場";
        }

        int benchmarkIndex = Math.min(data.size() - 1, longWindow - 1);
        double buyHoldStartPrice = getOpenPrice(data.get(benchmarkIndex));
        if (buyHoldStartPrice <= 0) {
            buyHoldStartPrice = getClosePrice(data.get(benchmarkIndex));
        }
        double buyAndHoldReturnPct = buyHoldStartPrice > 0
                ? percentageChange(buyHoldStartPrice, finalClose)
                : 0.0;

        result.startDate = data.get(benchmarkIndex).date;
        result.endDate = data.get(data.size() - 1).date;
        result.finalCapital = round2(cash);
        result.totalReturnPct = round2(percentageChange(initialCapital, cash));
        result.buyAndHoldReturnPct = round2(buyAndHoldReturnPct);
        result.excessReturnPct = round2(result.totalReturnPct - result.buyAndHoldReturnPct);
        result.tradeCount = tradeCount;
        result.winCount = winCount;
        result.winRate = tradeCount > 0 ? round2((winCount * 100.0) / tradeCount) : 0.0;
        List<Double> benchmarkReturns = calculateBenchmarkReturnsFromHistory(data, benchmarkIndex);
        RiskMetrics metrics = calculateRiskMetrics(equityCurve, benchmarkReturns);
        result.sortinoRatio = round2(metrics.sortino());
        result.sharpeRatio = round2(metrics.sharpe());
        result.calmarRatio = round2(metrics.calmar());
        result.ulcerIndex = round2(metrics.ulcer());
        result.beta = round2(metrics.beta());
        result.treynorRatio = round2(metrics.treynor());
        result.informationRatio = round2(metrics.information());
        result.lastSignal = lastSignal;
        result.note = "策略使用收盤確認訊號、次日開盤成交；若最後仍持股，則以期末收盤價結算。";
        return result;
    }

    private static BacktestResult baseResult(String symbol,
                                             int shortWindow,
                                             int longWindow,
                                             int lookbackDays,
                                             double initialCapital) {
        BacktestResult result = new BacktestResult();
        result.symbol = symbol == null ? "" : symbol.trim();
        result.strategy = "均線交叉策略";
        result.shortWindow = shortWindow;
        result.longWindow = longWindow;
        result.lookbackDays = lookbackDays;
        result.initialCapital = round2(initialCapital);
        result.finalCapital = round2(initialCapital);
        result.totalReturnPct = 0.0;
        result.buyAndHoldReturnPct = 0.0;
        result.excessReturnPct = 0.0;
        result.tradeCount = 0;
        result.winCount = 0;
        result.winRate = 0.0;
        result.sortinoRatio = 0.0;
        result.sharpeRatio = 0.0;
        result.calmarRatio = 0.0;
        result.ulcerIndex = 0.0;
        result.beta = 0.0;
        result.treynorRatio = 0.0;
        result.informationRatio = 0.0;
        result.lastSignal = "持觀";
        result.note = "";
        return result;
    }

    private static RiskMetrics calculateRiskMetrics(List<Double> equityCurve, List<Double> benchmarkReturns) {
        double sortino = calculateSortinoRatio(equityCurve);
        double sharpe = calculateSharpeRatio(equityCurve);
        double calmar = calculateCalmarRatio(equityCurve);
        double ulcer = calculateUlcerIndex(equityCurve);

        List<Double> strategyReturns = calculateDailyReturns(equityCurve);
        ReturnsPair aligned = alignReturns(strategyReturns, benchmarkReturns);
        double beta = calculateBeta(aligned.strategyReturns(), aligned.benchmarkReturns());
        double treynor = calculateTreynorRatio(aligned.strategyReturns(), beta);
        double information = calculateInformationRatio(aligned.strategyReturns(), aligned.benchmarkReturns());
        return new RiskMetrics(sortino, sharpe, calmar, ulcer, beta, treynor, information);
    }

    private static void validateParams(int shortWindow, int longWindow, double initialCapital) {
        if (shortWindow <= 0 || longWindow <= 0) {
            throw new IllegalArgumentException("均線週期必須大於 0");
        }
        if (shortWindow >= longWindow) {
            throw new IllegalArgumentException("短均線週期必須小於長均線週期");
        }
        if (initialCapital <= 0) {
            throw new IllegalArgumentException("初始資金必須大於 0");
        }
    }

    private static List<StockDataPoint> normalizeHistory(List<StockDataPoint> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }

        List<StockDataPoint> normalized = new ArrayList<>();
        for (StockDataPoint point : history) {
            if (point == null) {
                continue;
            }
            double close = getClosePrice(point);
            if (close <= 0) {
                continue;
            }
            normalized.add(point);
        }
        normalized.sort(Comparator.comparing(point -> point.date));
        return normalized;
    }

    private static double[] calculateSmaSeries(double[] values, int window) {
        double[] result = new double[values.length];
        double sum = 0.0;

        for (int i = 0; i < values.length; i++) {
            sum += values[i];
            if (i >= window) {
                sum -= values[i - window];
            }

            result[i] = i >= window - 1 ? (sum / window) : Double.NaN;
        }
        return result;
    }

    private static String diagnoseForBacktest(List<StockDataPoint> history, int endIndex) {
        double currentPrice = getClosePrice(history.get(endIndex));
        double ma5 = calculateMA(history, endIndex, 5);
        double ma20 = calculateMA(history, endIndex, 20);
        double ma60 = calculateMA(history, endIndex, 60);

        if (ma5 > ma20 && ma20 > ma60 && currentPrice > ma5) {
            return "多頭強勢排列";
        }
        if (ma5 > ma20 && currentPrice > ma20) {
            return "多頭初升段";
        }
        if (currentPrice < ma5 && ma5 < ma20 && ma20 < ma60) {
            return "空頭強勢排列";
        }
        if (currentPrice < ma60) {
            return "長期走勢偏弱";
        }
        return "震盪整理中";
    }

    private static double calculateMA(List<StockDataPoint> history, int endIndex, int days) {
        if (history == null || history.isEmpty() || days <= 0 || endIndex < 0 || endIndex >= history.size()) {
            return 0.0;
        }
        if (endIndex + 1 < days) {
            return 0.0;
        }

        double sum = 0.0;
        for (int i = endIndex - days + 1; i <= endIndex; i++) {
            double close = getClosePrice(history.get(i));
            if (close <= 0) {
                return 0.0;
            }
            sum += close;
        }
        return sum / days;
    }

    private static double getOpenPrice(StockDataPoint point) {
        return point.o > 0 ? point.o : getClosePrice(point);
    }

    private static double getClosePrice(StockDataPoint point) {
        if (point.c > 0) {
            return point.c;
        }
        if (point.price > 0) {
            return point.price;
        }
        return 0.0;
    }

    private static double percentageChange(double start, double end) {
        if (start <= 0) {
            return 0.0;
        }
        return ((end - start) / start) * 100.0;
    }

    private static double calculateSortinoRatio(List<Double> equityCurve) {
        List<Double> returns = calculateDailyReturns(equityCurve);
        if (returns.size() < 2) {
            return 0.0;
        }

        double mean = returns.stream().mapToDouble(v -> v).average().orElse(0.0);
        double downsideSquareSum = 0.0;
        int downsideCount = 0;
        for (double r : returns) {
            if (r < 0) {
                downsideSquareSum += r * r;
                downsideCount++;
            }
        }

        if (downsideCount == 0) {
            return mean > 0 ? 10.0 : 0.0;
        }

        double downsideDeviation = Math.sqrt(downsideSquareSum / downsideCount);
        if (downsideDeviation <= 0) {
            return 0.0;
        }

        double annualizedSortino = (mean / downsideDeviation) * Math.sqrt(252.0);
        if (!Double.isFinite(annualizedSortino)) {
            return 0.0;
        }
        return annualizedSortino;
    }

    private static double calculateSharpeRatio(List<Double> equityCurve) {
        List<Double> returns = calculateDailyReturns(equityCurve);
        if (returns.size() < 2) {
            return 0.0;
        }

        double mean = returns.stream().mapToDouble(v -> v).average().orElse(0.0);
        double variance = 0.0;
        for (double r : returns) {
            double diff = r - mean;
            variance += diff * diff;
        }
        variance /= (returns.size() - 1);
        double stdDev = Math.sqrt(Math.max(0.0, variance));
        if (stdDev <= 0) {
            return mean > 0 ? 10.0 : 0.0;
        }

        double annualizedSharpe = (mean / stdDev) * Math.sqrt(252.0);
        return Double.isFinite(annualizedSharpe) ? annualizedSharpe : 0.0;
    }

    private static double calculateCalmarRatio(List<Double> equityCurve) {
        if (equityCurve == null || equityCurve.size() < 3) {
            return 0.0;
        }

        List<Double> returns = calculateDailyReturns(equityCurve);
        if (returns.isEmpty()) {
            return 0.0;
        }

        double initial = equityCurve.get(0);
        double ending = equityCurve.get(equityCurve.size() - 1);
        if (initial <= 0 || ending <= 0) {
            return 0.0;
        }

        double totalReturn = (ending / initial) - 1.0;
        double annualizedReturn;
        if (totalReturn <= -1.0) {
            annualizedReturn = -1.0;
        } else {
            annualizedReturn = Math.pow(1.0 + totalReturn, 252.0 / returns.size()) - 1.0;
        }

        double peak = initial;
        double maxDrawdown = 0.0;
        for (double equity : equityCurve) {
            if (equity <= 0) {
                continue;
            }
            if (equity > peak) {
                peak = equity;
            }
            if (peak > 0) {
                double drawdown = (peak - equity) / peak;
                if (drawdown > maxDrawdown) {
                    maxDrawdown = drawdown;
                }
            }
        }

        if (maxDrawdown <= 0) {
            return annualizedReturn > 0 ? 10.0 : 0.0;
        }

        double calmar = annualizedReturn / maxDrawdown;
        return Double.isFinite(calmar) ? calmar : 0.0;
    }

    private static double calculateUlcerIndex(List<Double> equityCurve) {
        if (equityCurve == null || equityCurve.size() < 2) {
            return 0.0;
        }
        double peak = equityCurve.get(0);
        if (peak <= 0) {
            return 0.0;
        }
        double squareSum = 0.0;
        int count = 0;
        for (double equity : equityCurve) {
            if (equity <= 0) {
                continue;
            }
            peak = Math.max(peak, equity);
            double drawdownPct = peak > 0 ? ((equity - peak) / peak) * 100.0 : 0.0;
            if (drawdownPct < 0) {
                squareSum += drawdownPct * drawdownPct;
            }
            count++;
        }
        if (count == 0) {
            return 0.0;
        }
        double ui = Math.sqrt(squareSum / count);
        return Double.isFinite(ui) ? ui : 0.0;
    }

    private static List<Double> calculateBenchmarkReturnsFromHistory(List<StockDataPoint> history, int startIndex) {
        if (history == null || history.isEmpty() || startIndex < 0 || startIndex >= history.size() - 1) {
            return List.of();
        }
        List<Double> returns = new ArrayList<>();
        for (int i = Math.max(1, startIndex + 1); i < history.size(); i++) {
            double prev = getClosePrice(history.get(i - 1));
            double curr = getClosePrice(history.get(i));
            if (prev <= 0 || curr <= 0) {
                continue;
            }
            returns.add((curr - prev) / prev);
        }
        return returns;
    }

    private static ReturnsPair alignReturns(List<Double> strategyReturns, List<Double> benchmarkReturns) {
        if (strategyReturns == null || benchmarkReturns == null || strategyReturns.isEmpty() || benchmarkReturns.isEmpty()) {
            return new ReturnsPair(List.of(), List.of());
        }
        int size = Math.min(strategyReturns.size(), benchmarkReturns.size());
        List<Double> alignedStrategy = new ArrayList<>(strategyReturns.subList(strategyReturns.size() - size, strategyReturns.size()));
        List<Double> alignedBenchmark = new ArrayList<>(benchmarkReturns.subList(benchmarkReturns.size() - size, benchmarkReturns.size()));
        return new ReturnsPair(alignedStrategy, alignedBenchmark);
    }

    private static double calculateBeta(List<Double> strategyReturns, List<Double> benchmarkReturns) {
        if (strategyReturns == null || benchmarkReturns == null || strategyReturns.size() < 2 || benchmarkReturns.size() < 2) {
            return 0.0;
        }
        int n = Math.min(strategyReturns.size(), benchmarkReturns.size());
        if (n < 2) {
            return 0.0;
        }
        double meanStrategy = strategyReturns.stream().mapToDouble(v -> v).average().orElse(0.0);
        double meanBenchmark = benchmarkReturns.stream().mapToDouble(v -> v).average().orElse(0.0);
        double covariance = 0.0;
        double varianceBenchmark = 0.0;
        for (int i = 0; i < n; i++) {
            double s = strategyReturns.get(i) - meanStrategy;
            double b = benchmarkReturns.get(i) - meanBenchmark;
            covariance += s * b;
            varianceBenchmark += b * b;
        }
        covariance /= (n - 1);
        varianceBenchmark /= (n - 1);
        if (varianceBenchmark <= 0) {
            return 0.0;
        }
        double beta = covariance / varianceBenchmark;
        return Double.isFinite(beta) ? beta : 0.0;
    }

    private static double calculateTreynorRatio(List<Double> strategyReturns, double beta) {
        if (strategyReturns == null || strategyReturns.isEmpty() || Math.abs(beta) < 1e-9) {
            return 0.0;
        }
        double mean = strategyReturns.stream().mapToDouble(v -> v).average().orElse(0.0);
        double annualized = mean * 252.0;
        double treynor = annualized / beta;
        return Double.isFinite(treynor) ? treynor : 0.0;
    }

    private static double calculateInformationRatio(List<Double> strategyReturns, List<Double> benchmarkReturns) {
        if (strategyReturns == null || benchmarkReturns == null || strategyReturns.size() < 2 || benchmarkReturns.size() < 2) {
            return 0.0;
        }
        int n = Math.min(strategyReturns.size(), benchmarkReturns.size());
        if (n < 2) {
            return 0.0;
        }
        List<Double> activeReturns = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            activeReturns.add(strategyReturns.get(i) - benchmarkReturns.get(i));
        }
        double meanActive = activeReturns.stream().mapToDouble(v -> v).average().orElse(0.0);
        double variance = 0.0;
        for (double r : activeReturns) {
            double diff = r - meanActive;
            variance += diff * diff;
        }
        variance /= (n - 1);
        double trackingError = Math.sqrt(Math.max(0.0, variance));
        if (trackingError <= 0) {
            return meanActive > 0 ? 10.0 : 0.0;
        }
        double infoRatio = (meanActive / trackingError) * Math.sqrt(252.0);
        return Double.isFinite(infoRatio) ? infoRatio : 0.0;
    }

    private static List<Double> calculateDailyReturns(List<Double> equityCurve) {
        if (equityCurve == null || equityCurve.size() < 2) {
            return List.of();
        }

        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < equityCurve.size(); i++) {
            double prev = equityCurve.get(i - 1);
            double curr = equityCurve.get(i);
            if (prev <= 0 || curr <= 0) {
                continue;
            }
            returns.add((curr - prev) / prev);
        }
        return returns;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record ReturnsPair(List<Double> strategyReturns, List<Double> benchmarkReturns) {}

    private record RiskMetrics(double sortino,
                               double sharpe,
                               double calmar,
                               double ulcer,
                               double beta,
                               double treynor,
                               double information) {}
}
