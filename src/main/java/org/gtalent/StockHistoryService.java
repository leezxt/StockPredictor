package org.gtalent;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StockHistoryService {
    private final HistoryBackfillService historyBackfillService;
    private final StockDataRepository stockDataRepository;

    public StockHistoryService(
            HistoryBackfillService historyBackfillService,
            StockDataRepository stockDataRepository) {
        this.historyBackfillService = historyBackfillService;
        this.stockDataRepository = stockDataRepository;
    }

    public List<StockDataPoint> getRecentHistory(String symbol, int requiredDays) {
        String cleanSymbol = normalizeSymbol(symbol);
        int safeRequiredDays = normalizeRequiredDays(requiredDays);
        ensureRecentHistory(cleanSymbol, safeRequiredDays);
        return stockDataRepository.getRecentHistory(cleanSymbol, safeRequiredDays);
    }

    public List<StockDataPoint> getFullHistory(String symbol, int requiredDays) {
        String cleanSymbol = normalizeSymbol(symbol);
        int safeRequiredDays = normalizeRequiredDays(requiredDays);
        ensureFullHistory(cleanSymbol, safeRequiredDays);
        return stockDataRepository.getFullHistory(cleanSymbol, safeRequiredDays);
    }

    public double getLatestPrice(String symbol) {
        return stockDataRepository.getLatestPrice(normalizeSymbol(symbol));
    }

    public HistoryBackfillService.BackfillTaskSnapshot getBackfillTask(String symbol) {
        return historyBackfillService.getTask(normalizeSymbol(symbol));
    }

    public void ensureRecentHistory(String symbol, int requiredDays) {
        String cleanSymbol = normalizeSymbol(symbol);
        if (cleanSymbol.isBlank()) {
            return;
        }

        int safeRequiredDays = normalizeRequiredDays(requiredDays);
        List<StockDataPoint> existing =
                stockDataRepository.getRecentHistory(cleanSymbol, safeRequiredDays);
        if (!isRecentHistoryReady(existing, safeRequiredDays)) {
            historyBackfillService.requestBackfill(cleanSymbol, safeRequiredDays, false);
        }
    }

    public void ensureFullHistory(String symbol, int requiredDays) {
        String cleanSymbol = normalizeSymbol(symbol);
        if (cleanSymbol.isBlank()) {
            return;
        }

        int safeRequiredDays = normalizeRequiredDays(requiredDays);
        List<StockDataPoint> existing =
                stockDataRepository.getFullHistory(cleanSymbol, safeRequiredDays);
        if (!isFullHistoryReady(existing, safeRequiredDays)) {
            historyBackfillService.requestBackfill(cleanSymbol, safeRequiredDays, true);
        }
    }

    private boolean isRecentHistoryReady(List<StockDataPoint> data, int requiredDays) {
        if (data.size() < requiredDays) {
            return false;
        }

        long nonZeroVolumes = data.stream().filter(point -> point.volume > 0).count();
        long minRequiredNonZero = Math.max(1L, (long) Math.ceil(data.size() * 0.9));
        return nonZeroVolumes >= minRequiredNonZero;
    }

    private boolean isFullHistoryReady(List<StockDataPoint> data, int requiredDays) {
        if (data.size() < requiredDays) {
            return false;
        }

        long validOhlc = data.stream()
                .filter(point -> point.o > 0 && point.h > 0 && point.l > 0 && point.c > 0)
                .count();
        long minRequiredValid = Math.max(1L, (long) Math.ceil(data.size() * 0.9));
        return validOhlc >= minRequiredValid;
    }

    private int normalizeRequiredDays(int requiredDays) {
        return Math.max(1, requiredDays);
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim();
    }
}
