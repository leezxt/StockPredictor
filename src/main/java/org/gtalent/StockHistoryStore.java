package org.gtalent;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StockHistoryStore {
    private final StockDataRepository stockDataRepository;

    public StockHistoryStore(StockDataRepository stockDataRepository) {
        this.stockDataRepository = stockDataRepository;
    }

    public List<StockDataPoint> getRecentHistory(String symbol, int days) {
        return stockDataRepository.getRecentHistory(symbol, days);
    }

    public List<StockDataPoint> getFullHistory(String symbol, int days) {
        return stockDataRepository.getFullHistory(symbol, days);
    }

    public void saveMonthlyHistory(String symbol, List<String[]> monthData) {
        stockDataRepository.saveMonthlyHistory(symbol, monthData);
    }
}
