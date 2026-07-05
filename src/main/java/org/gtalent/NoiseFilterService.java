package org.gtalent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class NoiseFilterService {

    @Value("${noise-filter.day-trading.low-threshold:0.30}")
    private double lowThreshold;

    @Value("${noise-filter.day-trading.high-threshold:0.55}")
    private double highThreshold;

    @Value("${noise-filter.momentum.weight.low:1.00}")
    private double lowNoiseMomentumWeight;

    @Value("${noise-filter.momentum.weight.medium:0.70}")
    private double mediumNoiseMomentumWeight;

    @Value("${noise-filter.momentum.weight.high:0.50}")
    private double highNoiseMomentumWeight;

    @Value("${noise-filter.trend.boost.medium:1.10}")
    private double mediumNoiseTrendBoost;

    @Value("${noise-filter.trend.boost.high:1.25}")
    private double highNoiseTrendBoost;

    @Value("${noise-filter.money.boost.medium:1.10}")
    private double mediumNoiseMoneyBoost;

    @Value("${noise-filter.money.boost.high:1.25}")
    private double highNoiseMoneyBoost;

    public double getFilteredMomentumScore(double rawMomentumScore, double dayTradingRate) {
        return rawMomentumScore * resolveMomentumWeight(dayTradingRate);
    }

    public double resolveMomentumWeight(double dayTradingRate) {
        double normalizedRate = normalizeDayTradingRate(dayTradingRate);
        if (normalizedRate > highThreshold) {
            return highNoiseMomentumWeight;
        }
        if (normalizedRate >= lowThreshold) {
            return mediumNoiseMomentumWeight;
        }
        return lowNoiseMomentumWeight;
    }

    public RadarWeightProfile resolveRadarWeightProfile(double dayTradingRate) {
        double normalizedRate = normalizeDayTradingRate(dayTradingRate);
        if (normalizedRate > highThreshold) {
            return new RadarWeightProfile(highNoiseTrendBoost, highNoiseMomentumWeight, highNoiseMoneyBoost);
        }
        if (normalizedRate >= lowThreshold) {
            return new RadarWeightProfile(mediumNoiseTrendBoost, mediumNoiseMomentumWeight, mediumNoiseMoneyBoost);
        }
        return new RadarWeightProfile(1.0, lowNoiseMomentumWeight, 1.0);
    }

    public double normalizeDayTradingRate(double dayTradingRate) {
        if (Double.isNaN(dayTradingRate) || Double.isInfinite(dayTradingRate) || dayTradingRate <= 0) {
            return 0.0;
        }
        if (dayTradingRate > 1.0) {
            return Math.min(dayTradingRate / 100.0, 1.0);
        }
        return Math.min(dayTradingRate, 1.0);
    }

    public record RadarWeightProfile(double trendFactor, double momentumFactor, double moneyFactor) {
    }

    // =========================================================================
    // 信任度權重（Confidence Weight）與真實成交量
    // =========================================================================

    /**
     * 根據當沖佔比，計算技術指標的信任度權重（降噪係數）。
     * <p>
     * 當沖行為讓 KD、RSI 等短線指標嚴重失真，本方法提供固定分段折扣：
     * <ul>
     *   <li>&gt;= 60%：極度投機，信任度 0.40（打 4 折）</li>
     *   <li>45% ~ 60%：中高雜訊，信任度 0.65（打 65 折）</li>
     *   <li>30% ~ 45%：輕微雜訊，信任度 0.85（打 85 折）</li>
     *   <li>&lt; 30%：籌碼乾淨，信任度 1.0（完全信任）</li>
     * </ul>
     *
     * @param dayTradingRate 當沖佔比，0.0 ~ 1.0（例如 0.65 代表 65%）
     * @return 信任度權重 (0.40 ~ 1.0)
     */
    public double calculateConfidenceWeight(double dayTradingRate) {
        double rate = normalizeDayTradingRate(dayTradingRate);
        if (rate >= 0.60) {
            return 0.40; // 極度投機
        } else if (rate >= 0.45) {
            return 0.65; // 中高雜訊
        } else if (rate >= 0.30) {
            return 0.85; // 輕微雜訊
        }
        return 1.0;     // 籌碼乾淨
    }

    /**
     * 還原真實成交量（扣除當沖的虛胖流水帳）。
     * <p>
     * 當沖包含一買一賣，在交易所統計中會創造雙倍的「虛胖成交量」；
     * 扣除後才是真正沉澱下來的波段籌碼量。
     *
     * @param totalVolume      總成交張數（或股數）
     * @param dayTradingVolume 當沖張數（或股數）
     * @return 真正沉澱的波段籌碼量（保證 &gt;= 0）
     */
    public long calculateRealVolume(long totalVolume, long dayTradingVolume) {
        long realVolume = totalVolume - dayTradingVolume;
        return Math.max(realVolume, 0);
    }

    /**
     * 便利方法：一次取得完整的降噪分析結果。
     *
     * @param totalVolume      總成交張數
     * @param dayTradingVolume 當沖張數
     * @return {@link NoiseAnalysis} 包含信任度權重與真實成交量
     */
    public NoiseAnalysis analyze(long totalVolume, long dayTradingVolume) {
        double dayTradingRate = (totalVolume > 0)
                ? normalizeDayTradingRate((double) dayTradingVolume / totalVolume)
                : 0.0;
        double confidenceWeight = calculateConfidenceWeight(dayTradingRate);
        long realVolume = calculateRealVolume(totalVolume, dayTradingVolume);
        return new NoiseAnalysis(dayTradingRate, confidenceWeight, realVolume);
    }

    /**
     * 降噪分析結果值物件。
     *
     * @param dayTradingRate   當沖佔比 (0.0 ~ 1.0)
     * @param confidenceWeight 技術指標信任度權重 (0.40 ~ 1.0)
     * @param realVolume       還原後的真實成交量
     */
    public record NoiseAnalysis(
            double dayTradingRate,
            double confidenceWeight,
            long realVolume
    ) {
        /** 判斷市場是否處於高雜訊狀態（當沖率 &gt;= 45%） */
        public boolean isHighNoise() {
            return dayTradingRate >= 0.45;
        }

        @Override
        public String toString() {
            return String.format(
                    "NoiseAnalysis{dayTradingRate=%.2f%%, confidenceWeight=%.2f, realVolume=%d, highNoise=%b}",
                    dayTradingRate * 100, confidenceWeight, realVolume, isHighNoise()
            );
        }
    }
}

