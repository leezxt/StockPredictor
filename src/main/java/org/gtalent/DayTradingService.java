package org.gtalent;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class DayTradingService {

    private final FinMindClient finMindClient;

    public DayTradingService(FinMindClient finMindClient) {
        this.finMindClient = finMindClient;
    }

    public List<FinMindDayTradingData> getDayTradingHistory(String symbol, int days) {
        String cleanSymbol = symbol == null ? "" : symbol.trim();
        if (cleanSymbol.isBlank() || days <= 0) {
            return List.of();
        }

        int safeDays = Math.max(1, Math.min(days, 180));
        List<FinMindDayTradingData> local = DatabaseManager.getDayTradingHistory(cleanSymbol, safeDays);
        if (local.size() >= Math.min(safeDays, 10)) {
            return local;
        }

        String startDate = LocalDate.now().minusDays(Math.max(30, safeDays * 2L)).toString();
        List<FinMindDayTradingData> fetched = finMindClient.fetchFinMindDayTradingData(cleanSymbol, startDate);
        if (!fetched.isEmpty()) {
            DatabaseManager.saveDayTradingData(cleanSymbol, fetched);
        }

        return DatabaseManager.getDayTradingHistory(cleanSymbol, safeDays);
    }
}

