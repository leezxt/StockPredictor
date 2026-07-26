package org.gtalent;

import org.gtalent.dto.BollingerResult;
import org.gtalent.dto.IchimokuResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class IndicatorCalculator {
    private final StockDataRepository stockDataRepository;

    public IndicatorCalculator(StockDataRepository stockDataRepository) {
        this.stockDataRepository = stockDataRepository;
    }

    /**
     * 計算布林通道 (20, 2)
     */
    public BollingerResult calculateBollinger(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return new BollingerResult(0, 0, 0, 0);
        }

        int period = 20;
        List<Double> prices = new ArrayList<>();
        for (StockDataPoint point : stockDataRepository.getFullHistory(symbol.trim(), period)) {
            if (point != null) {
                prices.add(point.c);
            }
        }

        if (prices.size() < period) {
            return new BollingerResult(0, 0, 0, 0);
        }

        double sum = 0;
        for (double p : prices) sum += p;
        double ma = sum / period;

        double varianceSum = 0;
        for (double p : prices) varianceSum += Math.pow(p - ma, 2);
        double sd = Math.sqrt(varianceSum / period);

        double upper = ma + (2 * sd);
        double lower = ma - (2 * sd);
        double bw = (ma == 0) ? 0 : (upper - lower) / ma;

        return new BollingerResult(upper, ma, lower, bw);
    }

    /**
     * 計算一目均衡表
     */
    public IchimokuResult calculateIchimoku(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return new IchimokuResult(0, 0, 0, 0, 0);
        }

        // 需要數據: 轉折線(9), 基準線(26), 先行帶B(52), 遲行帶(26日前)
        // 為了簡單起見，我們取最後 78 筆 (52 + 26)
        int needed = 78;
        List<StockDataPoint> data =
                new ArrayList<>(stockDataRepository.getFullHistory(symbol.trim(), needed));
        Collections.reverse(data);

        if (data.size() < 52) {
            return new IchimokuResult(0, 0, 0, 0, 0);
        }

        // 轉折線 (Tenkan-sen): (9日內最高 + 9日內最低) / 2
        double h9 = data.get(0).h, l9 = data.get(0).l;
        for (int i = 0; i < Math.min(9, data.size()); i++) {
            h9 = Math.max(h9, data.get(i).h);
            l9 = Math.min(l9, data.get(i).l);
        }
        double tenkan = (h9 + l9) / 2.0;

        // 基準線 (Kijun-sen): (26日內最高 + 26日內最低) / 2
        double h26 = data.get(0).h, l26 = data.get(0).l;
        for (int i = 0; i < Math.min(26, data.size()); i++) {
            h26 = Math.max(h26, data.get(i).h);
            l26 = Math.min(l26, data.get(i).l);
        }
        double kijun = (h26 + l26) / 2.0;

        // 先行帶A (Senkou Span A): (轉折線 + 基準線) / 2, 繪製於 26 日後
        // 注意：這裡計算的是「目前的」先行帶A值
        double spanA = (tenkan + kijun) / 2.0;

        // 先行帶B (Senkou Span B): (52日內最高 + 52日內最低) / 2, 繪製於 26 日後
        double h52 = data.get(0).h, l52 = data.get(0).l;
        for (int i = 0; i < Math.min(52, data.size()); i++) {
            h52 = Math.max(h52, data.get(i).h);
            l52 = Math.min(l52, data.get(i).l);
        }
        double spanB = (h52 + l52) / 2.0;

        // 遲行帶 (Chikou Span): 當日收盤價, 繪製於 26 日前
        double chikou = data.get(0).c;

        return new IchimokuResult(tenkan, kijun, spanA, spanB, chikou);
    }

    /**
     * 計算 VWAP (當日成交量加權平均價)
     * 這裡簡化為最近 20 日的加權平均
     */
    public double calculateVWAP(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return 0.0;
        }

        int period = 20;
        double totalValue = 0;
        long totalVolume = 0;
        for (StockDataPoint point : stockDataRepository.getFullHistory(symbol.trim(), period)) {
            if (point != null) {
                totalValue += point.c * point.volume;
                totalVolume += point.volume;
            }
        }

        return (totalVolume == 0) ? 0 : totalValue / totalVolume;
    }

    public double calculateRSI(String symbol, int period) {
        if (symbol == null || symbol.isBlank() || period <= 0) {
            return 50.0;
        }

        List<Double> prices = stockDataRepository.getFullHistory(symbol.trim(), period + 1).stream()
                .map(point -> point.c)
                .toList();

        if (prices.size() <= period) {
            return 50.0;
        }

        double avgGain = 0.0;
        double avgLoss = 0.0;
        for (int i = 1; i < prices.size(); i++) {
            double diff = prices.get(i) - prices.get(i - 1);
            if (diff > 0) {
                avgGain += diff;
            } else {
                avgLoss += Math.abs(diff);
            }
        }

        avgGain /= period;
        avgLoss /= period;

        if (avgLoss == 0.0) {
            return 100.0;
        }

        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    public MACDResult calculateMACD(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return new MACDResult(0, 0, 0);
        }
        // 取足夠的收盤價，建議 35 筆以上
        int emaShort = 12;
        int emaLong = 26;
        int deaPeriod = 9;
        int minCount = emaLong + deaPeriod; // 35
        List<Double> prices = stockDataRepository.getFullHistory(symbol.trim(), minCount).stream()
                .map(point -> point.c)
                .toList();
        if (prices.size() < minCount) {
            return new MACDResult(0, 0, 0);
        }
        // 計算 EMA12, EMA26
        List<Double> ema12List = new ArrayList<>();
        List<Double> ema26List = new ArrayList<>();
        double ema12 = prices.get(0);
        double ema26 = prices.get(0);
        double k12 = 2.0 / (emaShort + 1);
        double k26 = 2.0 / (emaLong + 1);
        for (double price : prices) {
            ema12 = price * k12 + ema12 * (1 - k12);
            ema26 = price * k26 + ema26 * (1 - k26);
            ema12List.add(ema12);
            ema26List.add(ema26);
        }
        // 計算 DIF
        List<Double> difList = new ArrayList<>();
        for (int i = 0; i < prices.size(); i++) {
            difList.add(ema12List.get(i) - ema26List.get(i));
        }
        // 計算 DEA (DIF 的 9 日 EMA)
        List<Double> deaList = new ArrayList<>();
        double dea = difList.get(0);
        double k9 = 2.0 / (deaPeriod + 1);
        for (double dif : difList) {
            dea = dif * k9 + dea * (1 - k9);
            deaList.add(dea);
        }
        // 取最後一筆
        double dif = difList.get(difList.size() - 1);
        double deaVal = deaList.get(deaList.size() - 1);
        double histogram = (dif - deaVal) * 2;
        return new MACDResult(dif, deaVal, histogram);
    }

    public List<MACDResult> calculateMACDSeries(String symbol, int limit) {
        if (symbol == null || symbol.isBlank() || limit <= 0) {
            return Collections.emptyList();
        }
        int emaShort = 12;
        int emaLong = 26;
        int deaPeriod = 9;
        int minCount = Math.max(limit, emaLong + deaPeriod); // ensure enough data
        List<Double> prices = stockDataRepository.getFullHistory(symbol.trim(), minCount).stream()
                .map(point -> point.c)
                .toList();
        if (prices.size() < emaLong + deaPeriod) {
            return Collections.emptyList();
        }
        // 計算 EMA12, EMA26
        List<Double> ema12List = new ArrayList<>();
        List<Double> ema26List = new ArrayList<>();
        double ema12 = prices.get(0);
        double ema26 = prices.get(0);
        double k12 = 2.0 / (emaShort + 1);
        double k26 = 2.0 / (emaLong + 1);
        for (double price : prices) {
            ema12 = price * k12 + ema12 * (1 - k12);
            ema26 = price * k26 + ema26 * (1 - k26);
            ema12List.add(ema12);
            ema26List.add(ema26);
        }
        // 計算 DIF
        List<Double> difList = new ArrayList<>();
        for (int i = 0; i < prices.size(); i++) {
            difList.add(ema12List.get(i) - ema26List.get(i));
        }
        // 計算 DEA (DIF 的 9 日 EMA)
        List<Double> deaList = new ArrayList<>();
        double dea = difList.get(0);
        double k9 = 2.0 / (deaPeriod + 1);
        for (double dif : difList) {
            dea = dif * k9 + dea * (1 - k9);
            deaList.add(dea);
        }
        // 建立 MACDResult列表（對齊價格）
        List<MACDResult> result = new ArrayList<>();
        for (int i = 0; i < prices.size(); i++) {
            double dif = difList.get(i);
            double deaVal = deaList.get(i);
            double hist = (dif - deaVal) * 2;
            result.add(new MACDResult(dif, deaVal, hist));
        }
        // 只返回最後的'limit'個結果
        if (result.size() > limit) {
            return result.subList(result.size() - limit, result.size());
        } else {
            return result;
        }
    }

    public List<KDResult> calculateKD(String symbol, int limit) {
        if (symbol == null || symbol.isBlank() || limit <= 0) {
            return Collections.emptyList();
        }

        // KD 需要最近 9 天的高低價與收盤價，計算 limit 天份額外需要緩衝
        int rsvPeriod = 9;
        int totalNeeded = limit + rsvPeriod + 5; // 額外緩衝

        List<StockDataPoint> data = stockDataRepository.getFullHistory(symbol.trim(), totalNeeded);

        if (data.size() < rsvPeriod) {
            return Collections.emptyList();
        }

        List<KDResult> kdResults = new ArrayList<>();
        double k = 50.0;
        double d = 50.0;

        for (int i = 0; i < data.size(); i++) {
            if (i < rsvPeriod - 1) {
                continue;
            }

            // 1. ˆ— RSV = (Close - L9) / (H9 - L9) * 100
            double currentClose = data.get(i).c;
            double low9 = data.get(i).l;
            double high9 = data.get(i).h;

            for (int j = i - rsvPeriod + 1; j < i; j++) {
                low9 = Math.min(low9, data.get(j).l);
                high9 = Math.max(high9, data.get(j).h);
            }

            double rsv = 50.0;
            if (high9 != low9) {
                rsv = (currentClose - low9) / (high9 - low9) * 100.0;
            }

            // 2. K = 2/3 * prevK + 1/3 * RSV
            k = (2.0 / 3.0) * k + (1.0 / 3.0) * rsv;
            // 3. D = 2/3 * prevD + 1/3 * K
            d = (2.0 / 3.0) * d + (1.0 / 3.0) * k;

            kdResults.add(new KDResult(k, d));
        }

        // 只返回 limit 筆
        if (kdResults.size() > limit) {
            return kdResults.subList(kdResults.size() - limit, kdResults.size());
        }
        return kdResults;
    }

    public List<Double> calculateBBWSeries(String symbol, int limit) {
        if (symbol == null || symbol.isBlank() || limit <= 0) {
            return Collections.emptyList();
        }

        int maPeriod = 20;
        int totalNeeded = limit + maPeriod + 5;

        List<Double> prices = stockDataRepository.getFullHistory(symbol.trim(), totalNeeded).stream()
                .map(point -> point.c)
                .toList();

        if (prices.size() < maPeriod) {
            return Collections.emptyList();
        }

        List<Double> bbwSeries = new ArrayList<>();
        for (int i = 0; i <= prices.size() - maPeriod; i++) {
            List<Double> subList = prices.subList(i, i + maPeriod);

            double sum = 0;
            for (double p : subList) sum += p;
            double ma = sum / maPeriod;

            double varianceSum = 0;
            for (double p : subList) varianceSum += Math.pow(p - ma, 2);
            double sd = Math.sqrt(varianceSum / maPeriod);

            // BBW = (Upper Band - Lower Band) / Middle Band
            // (MA + 2SD - (MA - 2SD)) / MA = 4SD / MA
            double bbw = (ma == 0) ? 0 : (4 * sd) / ma;
            bbwSeries.add(bbw);
        }

        if (bbwSeries.size() > limit) {
            return bbwSeries.subList(bbwSeries.size() - limit, bbwSeries.size());
        }
        return bbwSeries;
    }

    public double calculateADX(String symbol, int period) {
        if (symbol == null || symbol.isBlank() || period <= 1) {
            return 0.0;
        }

        int totalNeeded = Math.max(period * 4, period + 5);
        List<StockDataPoint> data = stockDataRepository.getFullHistory(symbol.trim(), totalNeeded);

        if (data.size() < period + 2) {
            return 0.0;
        }

        List<Double> trList = new ArrayList<>();
        List<Double> plusDmList = new ArrayList<>();
        List<Double> minusDmList = new ArrayList<>();

        for (int i = 1; i < data.size(); i++) {
            StockDataPoint prev = data.get(i - 1);
            StockDataPoint curr = data.get(i);

            double prevClose = prev.c > 0 ? prev.c : prev.price;
            double high = curr.h;
            double low = curr.l;
            if (high <= 0 || low <= 0 || prevClose <= 0) {
                continue;
            }

            double upMove = high - prev.h;
            double downMove = prev.l - low;

            double plusDm = (upMove > downMove && upMove > 0) ? upMove : 0.0;
            double minusDm = (downMove > upMove && downMove > 0) ? downMove : 0.0;

            double tr = Math.max(high - low, Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));

            trList.add(tr);
            plusDmList.add(plusDm);
            minusDmList.add(minusDm);
        }

        if (trList.size() < period + 1) {
            return 0.0;
        }

        double trSmoothed = 0.0;
        double plusDmSmoothed = 0.0;
        double minusDmSmoothed = 0.0;
        for (int i = 0; i < period; i++) {
            trSmoothed += trList.get(i);
            plusDmSmoothed += plusDmList.get(i);
            minusDmSmoothed += minusDmList.get(i);
        }

        List<Double> dxList = new ArrayList<>();
        for (int i = period; i < trList.size(); i++) {
            trSmoothed = trSmoothed - (trSmoothed / period) + trList.get(i);
            plusDmSmoothed = plusDmSmoothed - (plusDmSmoothed / period) + plusDmList.get(i);
            minusDmSmoothed = minusDmSmoothed - (minusDmSmoothed / period) + minusDmList.get(i);

            if (trSmoothed <= 0) {
                continue;
            }

            double plusDi = (plusDmSmoothed / trSmoothed) * 100.0;
            double minusDi = (minusDmSmoothed / trSmoothed) * 100.0;
            double diSum = plusDi + minusDi;
            if (diSum <= 0) {
                continue;
            }

            double dx = Math.abs(plusDi - minusDi) / diSum * 100.0;
            dxList.add(dx);
        }

        if (dxList.isEmpty()) {
            return 0.0;
        }

        int seedWindow = Math.min(period, dxList.size());
        double adx = 0.0;
        for (int i = 0; i < seedWindow; i++) {
            adx += dxList.get(i);
        }
        adx /= seedWindow;

        for (int i = seedWindow; i < dxList.size(); i++) {
            adx = ((adx * (period - 1)) + dxList.get(i)) / period;
        }

        return Math.max(0.0, Math.min(100.0, adx));
    }

    public double calculateATR(String symbol, int period) {
        if (symbol == null || symbol.isBlank() || period <= 1) {
            return 0.0;
        }

        int totalNeeded = Math.max(period * 3, period + 10);
        List<StockDataPoint> data = stockDataRepository.getFullHistory(symbol.trim(), totalNeeded);

        if (data.size() < period + 1) {
            return 0.0;
        }

        List<Double> trList = new ArrayList<>();
        for (int i = 1; i < data.size(); i++) {
            StockDataPoint prev = data.get(i - 1);
            StockDataPoint curr = data.get(i);
            double prevClose = prev.c > 0 ? prev.c : prev.price;
            if (curr.h <= 0 || curr.l <= 0 || prevClose <= 0) {
                continue;
            }
            double tr = Math.max(curr.h - curr.l, Math.max(Math.abs(curr.h - prevClose), Math.abs(curr.l - prevClose)));
            trList.add(tr);
        }

        if (trList.size() < period) {
            return 0.0;
        }

        double atr = 0.0;
        for (int i = 0; i < period; i++) {
            atr += trList.get(i);
        }
        atr /= period;

        for (int i = period; i < trList.size(); i++) {
            atr = ((atr * (period - 1)) + trList.get(i)) / period;
        }
        return Math.max(0.0, atr);
    }

    /**
     * OBV 強度（標準化）：約略落在 -1 ~ +1 區間，正值代表量價偏多。
     */
    public double calculateOBV(String symbol, int lookbackDays) {
        if (symbol == null || symbol.isBlank() || lookbackDays <= 1) {
            return 0.0;
        }

        List<StockDataPoint> data = stockDataRepository.getFullHistory(symbol.trim(), lookbackDays + 1);

        if (data.size() < 3) {
            return 0.0;
        }

        double obv = 0.0;
        double volumeSum = 0.0;
        int moveCount = 0;
        for (int i = 1; i < data.size(); i++) {
            double prevClose = data.get(i - 1).c > 0 ? data.get(i - 1).c : data.get(i - 1).price;
            double close = data.get(i).c > 0 ? data.get(i).c : data.get(i).price;
            double volume = Math.max(0L, data.get(i).volume);
            if (prevClose <= 0 || close <= 0 || volume <= 0) {
                continue;
            }
            if (close > prevClose) {
                obv += volume;
            } else if (close < prevClose) {
                obv -= volume;
            }
            volumeSum += volume;
            moveCount++;
        }

        if (moveCount == 0 || volumeSum <= 0) {
            return 0.0;
        }

        double avgVolume = volumeSum / moveCount;
        double normalized = obv / (avgVolume * moveCount);
        if (!Double.isFinite(normalized)) {
            return 0.0;
        }
        return normalized;
    }

    public double calculateMFI(String symbol, int period) {
        if (symbol == null || symbol.isBlank() || period <= 1) {
            return 50.0;
        }

        List<StockDataPoint> data = stockDataRepository.getFullHistory(symbol.trim(), period + 5);

        if (data.size() < period + 1) {
            return 50.0;
        }

        int from = data.size() - (period + 1);
        double positiveFlow = 0.0;
        double negativeFlow = 0.0;

        for (int i = from + 1; i < data.size(); i++) {
            StockDataPoint prev = data.get(i - 1);
            StockDataPoint curr = data.get(i);
            if (curr.h <= 0 || curr.l <= 0 || curr.c <= 0 || curr.volume <= 0
                    || prev.h <= 0 || prev.l <= 0 || prev.c <= 0) {
                continue;
            }
            double prevTypical = (prev.h + prev.l + prev.c) / 3.0;
            double currTypical = (curr.h + curr.l + curr.c) / 3.0;
            double rawMoneyFlow = currTypical * curr.volume;
            if (currTypical > prevTypical) {
                positiveFlow += rawMoneyFlow;
            } else if (currTypical < prevTypical) {
                negativeFlow += rawMoneyFlow;
            }
        }

        if (positiveFlow <= 0 && negativeFlow <= 0) {
            return 50.0;
        }
        if (negativeFlow <= 0) {
            return 100.0;
        }

        double moneyRatio = positiveFlow / negativeFlow;
        double mfi = 100.0 - (100.0 / (1.0 + moneyRatio));
        if (!Double.isFinite(mfi)) {
            return 50.0;
        }
        return Math.max(0.0, Math.min(100.0, mfi));
    }

    /**
     * SuperTrend 方向：1=多頭、-1=空頭、0=資料不足。
     */
    public int calculateSuperTrendDirection(String symbol, int period, double multiplier) {
        if (symbol == null || symbol.isBlank() || period < 5 || multiplier <= 0) {
            return 0;
        }

        List<StockDataPoint> data = loadOhlcv(symbol, Math.max(period * 4, period + 30));
        if (data.size() < period + 5) {
            return 0;
        }

        List<Double> atrSeries = calculateAtrSeries(data, period);
        if (atrSeries.isEmpty()) {
            return 0;
        }

        int atrOffset = data.size() - atrSeries.size();
        double finalUpper = 0.0;
        double finalLower = 0.0;
        double superTrend = 0.0;
        int trend = 0;

        for (int i = atrOffset; i < data.size(); i++) {
            StockDataPoint curr = data.get(i);
            double close = curr.c > 0 ? curr.c : curr.price;
            if (close <= 0 || curr.h <= 0 || curr.l <= 0) {
                continue;
            }
            double atr = atrSeries.get(i - atrOffset);
            if (atr <= 0) {
                continue;
            }

            double hl2 = (curr.h + curr.l) / 2.0;
            double basicUpper = hl2 + multiplier * atr;
            double basicLower = hl2 - multiplier * atr;

            if (i == atrOffset) {
                finalUpper = basicUpper;
                finalLower = basicLower;
                trend = close >= finalLower ? 1 : -1;
                superTrend = trend > 0 ? finalLower : finalUpper;
                continue;
            }

            StockDataPoint prevPoint = data.get(i - 1);
            double prevClose = prevPoint.c > 0 ? prevPoint.c : prevPoint.price;
            finalUpper = (basicUpper < finalUpper || prevClose > finalUpper) ? basicUpper : finalUpper;
            finalLower = (basicLower > finalLower || prevClose < finalLower) ? basicLower : finalLower;

            if (trend > 0) {
                if (close < finalLower) {
                    trend = -1;
                    superTrend = finalUpper;
                } else {
                    superTrend = finalLower;
                }
            } else {
                if (close > finalUpper) {
                    trend = 1;
                    superTrend = finalLower;
                } else {
                    superTrend = finalUpper;
                }
            }
        }
        return trend;
    }

    /**
     * Donchian 位置值（0~1）：接近 1 代表貼近上軌，接近 0 代表貼近下軌。
     */
    public double calculateDonchianPosition(String symbol, int period) {
        if (symbol == null || symbol.isBlank() || period < 5) {
            return 0.5;
        }

        List<StockDataPoint> data = loadOhlcv(symbol, period + 5);
        if (data.size() < period) {
            return 0.5;
        }

        int start = data.size() - period;
        double highest = Double.NEGATIVE_INFINITY;
        double lowest = Double.POSITIVE_INFINITY;
        for (int i = start; i < data.size(); i++) {
            StockDataPoint p = data.get(i);
            if (p.h > 0) highest = Math.max(highest, p.h);
            if (p.l > 0) lowest = Math.min(lowest, p.l);
        }
        double close = data.get(data.size() - 1).c > 0 ? data.get(data.size() - 1).c : data.get(data.size() - 1).price;
        if (highest <= lowest || close <= 0) {
            return 0.5;
        }
        return Math.max(0.0, Math.min(1.0, (close - lowest) / (highest - lowest)));
    }

    public double calculateCMF(String symbol, int period) {
        if (symbol == null || symbol.isBlank() || period < 5) {
            return 0.0;
        }
        List<StockDataPoint> data = loadOhlcv(symbol, period + 5);
        if (data.size() < period) {
            return 0.0;
        }

        int start = data.size() - period;
        double mfvSum = 0.0;
        double volumeSum = 0.0;
        for (int i = start; i < data.size(); i++) {
            StockDataPoint p = data.get(i);
            double close = p.c > 0 ? p.c : p.price;
            double high = p.h;
            double low = p.l;
            double volume = Math.max(0L, p.volume);
            if (close <= 0 || high <= low || volume <= 0) {
                continue;
            }
            double mfm = ((close - low) - (high - close)) / (high - low);
            double mfv = mfm * volume;
            mfvSum += mfv;
            volumeSum += volume;
        }

        if (volumeSum <= 0) {
            return 0.0;
        }
        double cmf = mfvSum / volumeSum;
        return Double.isFinite(cmf) ? Math.max(-1.0, Math.min(1.0, cmf)) : 0.0;
    }

    public double calculateCCI(String symbol, int period) {
        if (symbol == null || symbol.isBlank() || period < 5) {
            return 0.0;
        }
        List<StockDataPoint> data = loadOhlcv(symbol, period + 5);
        if (data.size() < period) {
            return 0.0;
        }

        int start = data.size() - period;
        List<Double> typicalPrices = new ArrayList<>();
        for (int i = start; i < data.size(); i++) {
            StockDataPoint p = data.get(i);
            if (p.h <= 0 || p.l <= 0 || p.c <= 0) {
                continue;
            }
            typicalPrices.add((p.h + p.l + p.c) / 3.0);
        }
        if (typicalPrices.size() < period) {
            return 0.0;
        }

        double sma = typicalPrices.stream().mapToDouble(v -> v).average().orElse(0.0);
        if (sma <= 0) {
            return 0.0;
        }
        double meanDev = 0.0;
        for (double tp : typicalPrices) {
            meanDev += Math.abs(tp - sma);
        }
        meanDev /= typicalPrices.size();
        if (meanDev <= 0) {
            return 0.0;
        }

        double latestTp = typicalPrices.get(typicalPrices.size() - 1);
        double cci = (latestTp - sma) / (0.015 * meanDev);
        return Double.isFinite(cci) ? cci : 0.0;
    }

    public double calculateWilliamsR(String symbol, int period) {
        if (symbol == null || symbol.isBlank() || period < 5) {
            return -50.0;
        }
        List<StockDataPoint> data = loadOhlcv(symbol, period + 5);
        if (data.size() < period) {
            return -50.0;
        }

        int start = data.size() - period;
        double highestHigh = Double.NEGATIVE_INFINITY;
        double lowestLow = Double.POSITIVE_INFINITY;
        for (int i = start; i < data.size(); i++) {
            StockDataPoint p = data.get(i);
            if (p.h > 0) highestHigh = Math.max(highestHigh, p.h);
            if (p.l > 0) lowestLow = Math.min(lowestLow, p.l);
        }
        double close = data.get(data.size() - 1).c > 0 ? data.get(data.size() - 1).c : data.get(data.size() - 1).price;
        if (highestHigh <= lowestLow || close <= 0) {
            return -50.0;
        }
        double wr = -100.0 * ((highestHigh - close) / (highestHigh - lowestLow));
        return Double.isFinite(wr) ? Math.max(-100.0, Math.min(0.0, wr)) : -50.0;
    }

    public double calculateAroonOscillator(String symbol, int period) {
        if (symbol == null || symbol.isBlank() || period < 5) {
            return 0.0;
        }
        List<StockDataPoint> data = loadOhlcv(symbol, period + 5);
        if (data.size() < period) {
            return 0.0;
        }

        int start = data.size() - period;
        int highIndex = start;
        int lowIndex = start;
        double highest = Double.NEGATIVE_INFINITY;
        double lowest = Double.POSITIVE_INFINITY;
        for (int i = start; i < data.size(); i++) {
            StockDataPoint p = data.get(i);
            if (p.h > highest) {
                highest = p.h;
                highIndex = i;
            }
            if (p.l > 0 && p.l < lowest) {
                lowest = p.l;
                lowIndex = i;
            }
        }

        int periodsSinceHigh = (data.size() - 1) - highIndex;
        int periodsSinceLow = (data.size() - 1) - lowIndex;
        double aroonUp = ((period - periodsSinceHigh) / (double) period) * 100.0;
        double aroonDown = ((period - periodsSinceLow) / (double) period) * 100.0;
        double osc = aroonUp - aroonDown;
        return Double.isFinite(osc) ? Math.max(-100.0, Math.min(100.0, osc)) : 0.0;
    }

    private List<StockDataPoint> loadOhlcv(String symbol, int limit) {
        return stockDataRepository.getFullHistory(symbol.trim(), Math.max(10, limit));
    }

    private static List<Double> calculateAtrSeries(List<StockDataPoint> data, int period) {
        if (data == null || data.size() < period + 1) {
            return List.of();
        }
        List<Double> trList = new ArrayList<>();
        for (int i = 1; i < data.size(); i++) {
            StockDataPoint prev = data.get(i - 1);
            StockDataPoint curr = data.get(i);
            double prevClose = prev.c > 0 ? prev.c : prev.price;
            if (curr.h <= 0 || curr.l <= 0 || prevClose <= 0) {
                continue;
            }
            double tr = Math.max(curr.h - curr.l, Math.max(Math.abs(curr.h - prevClose), Math.abs(curr.l - prevClose)));
            trList.add(tr);
        }
        if (trList.size() < period) {
            return List.of();
        }

        List<Double> atr = new ArrayList<>();
        double seed = 0.0;
        for (int i = 0; i < period; i++) {
            seed += trList.get(i);
        }
        seed /= period;
        atr.add(seed);
        double prevAtr = seed;
        for (int i = period; i < trList.size(); i++) {
            prevAtr = ((prevAtr * (period - 1)) + trList.get(i)) / period;
            atr.add(prevAtr);
        }
        return atr;
    }
}
