package org.gtalent;

import org.gtalent.dto.InstitutionalSyncResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Service
public class EnhancedInstitutionalService {
    private static final Logger logger = Logger.getLogger(EnhancedInstitutionalService.class.getName());
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final TwseService twseService = new TwseService();

    @Autowired
    private FinMindClient finMindClient;

    /**
     * 計算法人合力指標 (外資 + 投信)
     */
    public InstitutionalSyncResult calculateInstitutionalSync(String symbol, int days) {
        List<InstitutionalTrade> trades = getInstitutionalDataWithFallback(symbol, days);
        if (trades.isEmpty()) {
            return new InstitutionalSyncResult(0, "無資料", "無資料", false, 0);
        }

        double score = 0;
        long foreignNet = 0;
        long trustNet = 0;
        int foreignBuyDays = 0;
        int trustBuyDays = 0;

        for (InstitutionalTrade trade : trades) {
            foreignNet += trade.getForeignBuy();
            trustNet += trade.getTrustBuy();
            if (trade.getForeignBuy() > 0) foreignBuyDays++;
            if (trade.getTrustBuy() > 0) trustBuyDays++;
        }

        // 分數計算: 買超天數比例 + 買超張數加權
        // 投信加權 1.5, 外資 1.0
        double foreignScore = (double) foreignBuyDays / trades.size() * 40;
        double trustScore = (double) trustBuyDays / trades.size() * 60;
        score = foreignScore + trustScore;

        String fTrend = foreignNet > 0 ? "買超" : (foreignNet < 0 ? "賣超" : "盤整");
        String tTrend = trustNet > 0 ? "買超" : (trustNet < 0 ? "賣超" : "盤整");
        boolean sync = foreignNet > 0 && trustNet > 0;

        return new InstitutionalSyncResult(score, fTrend, tTrend, sync, foreignNet + trustNet);
    }

    public List<InstitutionalTrade> getInstitutionalDataWithFallback(String symbol, int days) {
        if (symbol == null || symbol.isBlank() || days <= 0) {
            return new ArrayList<>();
        }
        List<InstitutionalTrade> localData = DatabaseManager.getRecentInstitutionalTrades(symbol, days);
        if (!localData.isEmpty()) {
            logger.info("📦 從本地數據庫獲取 " + symbol);
            return localData;
        }
        List<InstitutionalTrade> twseData = twseService.fetchRecentInstitutionalData(symbol, Math.min(days, 30));
        if (twseData != null && !twseData.isEmpty()) {
            logger.info("🌐 從 TWSE 官網獲取 " + symbol);
            DatabaseManager.saveInstitutionalTrades(symbol, twseData);
            return twseData;
        }
        logger.info("🔄 TWSE 失敗，嘗試 FinMind API...");
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);
        List<FinMindChipData> chipData = finMindClient.fetchChipDataByDateRange(symbol, startDate.format(DATE_FORMATTER), endDate.format(DATE_FORMATTER));
        if (!chipData.isEmpty()) {
            logger.info("✅ 從 FinMind API 獲取 " + symbol);
            List<InstitutionalTrade> result = new ArrayList<>();
            for (FinMindChipData chip : chipData) {
                InstitutionalTrade trade = new InstitutionalTrade();
                trade.setDate(chip.getDate());
                trade.setTrustBuy(chip.getBuy());
                trade.setDailyVolume(chip.getBuy() + chip.getSell());
                result.add(trade);
            }
            DatabaseManager.saveInstitutionalTrades(symbol, result);
            return result;
        }
        logger.warning("⚠️  無法獲取 " + symbol + " 的數據");
        return new ArrayList<>();
    }

    public List<FinMindChipData> getLatestChipDataFromFinMind(String symbol) {
        try {
            logger.info("📊 獲取 " + symbol + " 的最新籌碼資料");
            return finMindClient.fetchLatestChipData(symbol);
        } catch (Exception e) {
            logger.severe("❌ 獲取失敗: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public int calculateChipConcentrationScore(String symbol) {
        List<FinMindChipData> chipData = getLatestChipDataFromFinMind(symbol);
        if (chipData.isEmpty()) {
            return 0;
        }
        int score = 0;
        long totalBuy = 0;
        long totalSell = 0;
        int buyDays = 0;
        for (FinMindChipData data : chipData) {
            totalBuy += data.getBuy();
            totalSell += data.getSell();
            if (data.getBuy() > data.getSell()) {
                buyDays++;
            }
        }
        if (buyDays >= 5) {
            score += 25;
        } else if (buyDays >= 3) {
            score += 15;
        } else if (buyDays >= 1) {
            score += 5;
        }
        long netBuy = totalBuy - totalSell;
        long totalVolume = totalBuy + totalSell;
        if (totalVolume > 0) {
            double ratio = (double) netBuy / totalVolume;
            if (ratio > 0.2) {
                score += 30;
            } else if (ratio > 0.1) {
                score += 20;
            } else if (ratio > 0.05) {
                score += 10;
            }
        }
        return Math.min(score, 100);
    }

    public ChipTrendAnalysis analyzeChipTrend(String symbol) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(10);
        List<FinMindChipData> chipData = finMindClient.fetchChipDataByDateRange(symbol, startDate.format(DATE_FORMATTER), endDate.format(DATE_FORMATTER));
        ChipTrendAnalysis analysis = new ChipTrendAnalysis();
        analysis.setSymbol(symbol);
        analysis.setDataPoints(chipData.size());
        if (chipData.isEmpty()) {
            return analysis;
        }
        long totalNetBuy = 0;
        int positiveDays = 0;
        for (FinMindChipData data : chipData) {
            long netBuy = data.getBuy() - data.getSell();
            totalNetBuy += netBuy;
            if (netBuy > 0) {
                positiveDays++;
            }
        }
        analysis.setAverageNetBuy(totalNetBuy / Math.max(chipData.size(), 1));
        analysis.setPositiveDaysRatio((double) positiveDays / chipData.size());
        analysis.setTrend(analysis.getPositiveDaysRatio() > 0.5 ? "📈 上升" : "📉 下降");
        return analysis;
    }

    public static class ChipTrendAnalysis {
        private String symbol;
        private int dataPoints;
        private long averageNetBuy;
        private double positiveDaysRatio;
        private String trend;

        public String getSymbol() {
            return symbol;
        }

        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }

        public int getDataPoints() {
            return dataPoints;
        }

        public void setDataPoints(int dataPoints) {
            this.dataPoints = dataPoints;
        }

        public long getAverageNetBuy() {
            return averageNetBuy;
        }

        public void setAverageNetBuy(long averageNetBuy) {
            this.averageNetBuy = averageNetBuy;
        }

        public double getPositiveDaysRatio() {
            return positiveDaysRatio;
        }

        public void setPositiveDaysRatio(double positiveDaysRatio) {
            this.positiveDaysRatio = positiveDaysRatio;
        }

        public String getTrend() {
            return trend;
        }

        public void setTrend(String trend) {
            this.trend = trend;
        }

        @Override
        public String toString() {
            return "ChipTrendAnalysis{" + "symbol='" + symbol + '\'' + ", dataPoints=" + dataPoints + ", averageNetBuy=" + averageNetBuy + ", positiveDaysRatio=" + String.format("%.2f%%", positiveDaysRatio * 100) + ", trend='" + trend + '\'' + '}';
        }
    }
}

