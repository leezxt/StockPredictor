package org.gtalent;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MarketBreadthService {

    public MarketBreadthResult calculateMarketBreadth() {
        List<String> symbols = DatabaseManager.getAllSymbols();
        if (symbols.isEmpty()) {
            MarketBreadthResult emptyResult = new MarketBreadthResult(0.0, 0, 0);
            DatabaseManager.saveDailyMarketBreadth(emptyResult);
            return emptyResult;
        }

        int eligibleCount = 0;
        int bullishCount = 0;

        for (String symbol : symbols) {
            if (symbol == null || symbol.isBlank()) {
                continue;
            }

            Double[] mas = calculateMovingAverages(symbol);
            if (mas == null) {
                continue;
            }

            long latestVol = DatabaseManager.getLatestVolume(symbol);
            long volMA5 = DatabaseManager.calculateVolumeMA(symbol, 5);
            long latestRealVol = getLatestRealVolumeOrRaw(symbol, latestVol);
            long realVolMA5 = getRealVolumeMA5OrRaw(symbol, volMA5);
            // Exclude extremely illiquid symbols where volume is missing or negligible
            if (latestRealVol <= 0 || realVolMA5 <= 0 || latestRealVol < Math.max(1L, Math.round(realVolMA5 * 0.05))) {
                continue;
            }

            eligibleCount++;
            if (mas[0] > mas[1] && mas[1] > mas[2]) {
                bullishCount++;
            }
        }

        if (eligibleCount == 0) {
            MarketBreadthResult emptyResult = new MarketBreadthResult(0.0, 0, 0);
            DatabaseManager.saveDailyMarketBreadth(emptyResult);
            return emptyResult;
        }

        double breadth = (double) bullishCount / eligibleCount * 100.0;
        MarketBreadthResult result = new MarketBreadthResult(breadth, eligibleCount, bullishCount);
        DatabaseManager.saveDailyMarketBreadth(result);
        return result;
    }

    public List<MarketBreadthSnapshot> getMarketBreadthHistory(int days) {
        int safeDays = Math.max(1, Math.min(days, 365));
        calculateMarketBreadth();
        return DatabaseManager.getMarketBreadthHistory(safeDays);
    }

    private Double[] calculateMovingAverages(String symbol) {
        double ma5 = DatabaseManager.calculateMA(symbol, 5);
        double ma20 = DatabaseManager.calculateMA(symbol, 20);
        double ma60 = DatabaseManager.calculateMA(symbol, 60);

        if (ma5 == 0.0 || ma20 == 0.0 || ma60 == 0.0) {
            return null;
        }

        return new Double[] { ma5, ma20, ma60 };
    }

    private long getLatestRealVolumeOrRaw(String symbol, long rawLatestVolume) {
        if (rawLatestVolume <= 0) {
            return 0L;
        }
        List<FinMindDayTradingData> dayTradingRows = DatabaseManager.getDayTradingHistory(symbol, 1);
        if (dayTradingRows.isEmpty()) {
            return rawLatestVolume;
        }
        FinMindDayTradingData latest = dayTradingRows.get(dayTradingRows.size() - 1);
        long dayTradingVolume = latest == null ? 0L : Math.max(0L, latest.getDayTradingVolume());
        return Math.max(0L, rawLatestVolume - dayTradingVolume * 2L);
    }

    private long getRealVolumeMA5OrRaw(String symbol, long rawVolMA5) {
        if (rawVolMA5 <= 0) {
            return 0L;
        }
        List<FinMindDayTradingData> dayTradingRows = DatabaseManager.getDayTradingHistory(symbol, 5);
        List<StockDataPoint> stockRows = DatabaseManager.getFullHistory(symbol, 5);
        if (dayTradingRows.isEmpty() || stockRows.isEmpty()) {
            return rawVolMA5;
        }

        java.util.Map<String, FinMindDayTradingData> dayTradingByDate = new java.util.HashMap<>();
        for (FinMindDayTradingData row : dayTradingRows) {
            if (row != null && row.getDate() != null) {
                dayTradingByDate.put(row.getDate(), row);
            }
        }

        long sum = 0L;
        int count = 0;
        for (StockDataPoint stockRow : stockRows) {
            long rawVolume = Math.max(0L, stockRow.volume);
            FinMindDayTradingData dayRow = dayTradingByDate.get(stockRow.date);
            long dayTradingVolume = dayRow == null ? 0L : Math.max(0L, dayRow.getDayTradingVolume());
            long realVolume = Math.max(0L, rawVolume - dayTradingVolume * 2L);
            sum += realVolume;
            count++;
        }
        if (count == 0) {
            return rawVolMA5;
        }
        return Math.round(sum / (double) count);
    }
}

