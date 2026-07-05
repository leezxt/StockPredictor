package org.gtalent;

import java.util.List;

public class InstitutionalScore implements ScoreProvider {

    @Override
    public int getScore(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return 0;
        }

        List<InstitutionalTrade> data = DatabaseManager.getRecentInstitutionalTrades(symbol, 10);
        if (data.isEmpty()) {
            return 0;
        }

        InstitutionalTrade latest = data.get(data.size() - 1);
        int score = 0;

        // 法人合買 (20分)
        if (latest.getForeignBuy() > 0 && latest.getTrustBuy() > 0) {
            score += 20;
        }

        // 買盤增溫 (15分)
        double avg5 = DatabaseManager.getAverageInstitutionalNetBuy(symbol, 5);
        if (latest.getTotalNetBuy() > avg5) {
            score += 15;
        }

        // 投信鎖碼 (10分)
        if (DatabaseManager.countConsecutiveTrustBuyDays(symbol, 10) >= 3) {
            score += 10;
        }

        return score;
    }
}
