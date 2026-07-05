package org.gtalent;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Service
public class ScoreEngine {

    @Value("${scoring.money.weight.institutional:0.70}")
    private double MONEY_WEIGHT_INSTITUTIONAL;

    @Value("${scoring.money.weight.big-holder:0.30}")
    private double MONEY_WEIGHT_BIG_HOLDER;

    // Trend weights
    @Value("${scoring.trend.bonus.price-above-ma5:20}") private int TREND_BONUS_PRICE_ABOVE_MA5;
    @Value("${scoring.trend.penalty.price-below-ma5:12}") private int TREND_PENALTY_PRICE_BELOW_MA5;
    @Value("${scoring.trend.bonus.ma5-above-ma10:15}") private int TREND_BONUS_MA5_ABOVE_MA10;
    @Value("${scoring.trend.penalty.ma5-below-ma10:10}") private int TREND_PENALTY_MA5_BELOW_MA10;
    @Value("${scoring.trend.bonus.ma10-above-ma20:20}") private int TREND_BONUS_MA10_ABOVE_MA20;
    @Value("${scoring.trend.penalty.ma10-below-ma20:15}") private int TREND_PENALTY_MA10_BELOW_MA20;
    @Value("${scoring.trend.bonus.ma20-above-ma60:25}") private int TREND_BONUS_MA20_ABOVE_MA60;
    @Value("${scoring.trend.penalty.ma20-below-ma60:18}") private int TREND_PENALTY_MA20_BELOW_MA60;
    @Value("${scoring.trend.bonus.distance-core:20}") private int TREND_BONUS_DISTANCE_CORE;
    @Value("${scoring.trend.bonus.distance-high:10}") private int TREND_BONUS_DISTANCE_HIGH;
    @Value("${scoring.trend.bonus.distance-recovery:8}") private int TREND_BONUS_DISTANCE_RECOVERY;
    @Value("${scoring.trend.penalty.distance-overheat:8}") private int TREND_PENALTY_DISTANCE_OVERHEAT;
    @Value("${scoring.trend.penalty.distance-bubble:15}") private int TREND_PENALTY_DISTANCE_BUBBLE;
    @Value("${scoring.trend.penalty.distance-weak:12}") private int TREND_PENALTY_DISTANCE_WEAK;
    @Value("${scoring.trend.penalty.distance-broken:20}") private int TREND_PENALTY_DISTANCE_BROKEN;

    // Momentum weights
    @Value("${scoring.momentum.bonus.rsi-healthy:35}") private int MOMENTUM_BONUS_RSI_HEALTHY;
    @Value("${scoring.momentum.bonus.rsi-good:25}") private int MOMENTUM_BONUS_RSI_GOOD;
    @Value("${scoring.momentum.bonus.rsi-hot:12}") private int MOMENTUM_BONUS_RSI_HOT;
    @Value("${scoring.momentum.bonus.rsi-oversold:18}") private int MOMENTUM_BONUS_RSI_OVERSOLD;
    @Value("${scoring.momentum.penalty.rsi-overheat:18}") private int MOMENTUM_PENALTY_RSI_OVERHEAT;
    @Value("${scoring.momentum.penalty.rsi-extreme-weak:15}") private int MOMENTUM_PENALTY_RSI_EXTREME_WEAK;
    @Value("${scoring.momentum.bonus.macd-positive:20}") private int MOMENTUM_BONUS_MACD_POSITIVE;
    @Value("${scoring.momentum.bonus.macd-expanding:15}") private int MOMENTUM_BONUS_MACD_EXPANDING;
    @Value("${scoring.momentum.penalty.macd-shrinking:4}") private int MOMENTUM_PENALTY_MACD_SHRINKING;
    @Value("${scoring.momentum.bonus.macd-near-zero:8}") private int MOMENTUM_BONUS_MACD_NEAR_ZERO;
    @Value("${scoring.momentum.penalty.macd-weak-negative:8}") private int MOMENTUM_PENALTY_MACD_WEAK_NEGATIVE;
    @Value("${scoring.momentum.penalty.macd-strong-negative:15}") private int MOMENTUM_PENALTY_MACD_STRONG_NEGATIVE;
    @Value("${scoring.momentum.bonus.kd-golden:20}") private int MOMENTUM_BONUS_KD_GOLDEN;
    @Value("${scoring.momentum.bonus.kd-golden-low-zone:10}") private int MOMENTUM_BONUS_KD_GOLDEN_LOW_ZONE;
    @Value("${scoring.momentum.bonus.kd-above:8}") private int MOMENTUM_BONUS_KD_ABOVE;
    @Value("${scoring.momentum.penalty.kd-below:10}") private int MOMENTUM_PENALTY_KD_BELOW;
    @Value("${scoring.momentum.penalty.kd-death:15}") private int MOMENTUM_PENALTY_KD_DEATH;
    @Value("${scoring.momentum.penalty.kd-death-high-zone:8}") private int MOMENTUM_PENALTY_KD_DEATH_HIGH_ZONE;

    // Money weights
    @Value("${scoring.money.bonus.trust-days-8:40}") private int MONEY_BONUS_TRUST_DAYS_8;
    @Value("${scoring.money.bonus.trust-days-5:30}") private int MONEY_BONUS_TRUST_DAYS_5;
    @Value("${scoring.money.bonus.trust-days-3:20}") private int MONEY_BONUS_TRUST_DAYS_3;
    @Value("${scoring.money.bonus.trust-days-1:8}") private int MONEY_BONUS_TRUST_DAYS_1;
    @Value("${scoring.money.penalty.trust-days-0:10}") private int MONEY_PENALTY_TRUST_DAYS_0;
    @Value("${scoring.money.bonus.lock-ratio-15:35}") private int MONEY_BONUS_LOCK_RATIO_15;
    @Value("${scoring.money.bonus.lock-ratio-8:20}") private int MONEY_BONUS_LOCK_RATIO_8;
    @Value("${scoring.money.bonus.lock-ratio-3:10}") private int MONEY_BONUS_LOCK_RATIO_3;
    @Value("${scoring.money.penalty.lock-ratio-low:10}") private int MONEY_PENALTY_LOCK_RATIO_LOW;
    @Value("${scoring.money.bonus.net-buy-positive:10}") private int MONEY_BONUS_NET_BUY_POSITIVE;
    @Value("${scoring.money.bonus.net-buy-above-avg:15}") private int MONEY_BONUS_NET_BUY_ABOVE_AVG;
    @Value("${scoring.money.penalty.net-buy-negative:10}") private int MONEY_PENALTY_NET_BUY_NEGATIVE;
    @Value("${scoring.money.penalty.net-buy-below-avg:8}") private int MONEY_PENALTY_NET_BUY_BELOW_AVG;
    @Value("${scoring.money.penalty.net-buy-avg-negative:5}") private int MONEY_PENALTY_NET_BUY_AVG_NEGATIVE;

    // Volatility weights
    @Value("${scoring.volatility.base-when-range-invalid:50}") private int VOLATILITY_BASE_WHEN_RANGE_INVALID;
    @Value("${scoring.volatility.squeeze-scale:70.0}") private double VOLATILITY_SQUEEZE_SCALE;
    @Value("${scoring.volatility.bonus.opening:20}") private int VOLATILITY_BONUS_OPENING;
    @Value("${scoring.volatility.bonus.low-band:10}") private int VOLATILITY_BONUS_LOW_BAND;
    @Value("${scoring.volatility.penalty.expanded:8}") private int VOLATILITY_PENALTY_EXPANDED;
    @Value("${scoring.volatility.penalty.over-expanded:15}") private int VOLATILITY_PENALTY_OVER_EXPANDED;
    @Value("${scoring.volatility.penalty.opening-over-expanded:6}") private int VOLATILITY_PENALTY_OPENING_OVER_EXPANDED;

    // Context weights (base +/- adjustments)
    @Value("${scoring.context.base-score:50}") private int CONTEXT_BASE_SCORE;
    @Value("${scoring.context.bonus.breadth-80:45}") private int CONTEXT_BONUS_BREADTH_80;
    @Value("${scoring.context.bonus.breadth-70:35}") private int CONTEXT_BONUS_BREADTH_70;
    @Value("${scoring.context.bonus.breadth-55:20}") private int CONTEXT_BONUS_BREADTH_55;
    @Value("${scoring.context.bonus.breadth-45:10}") private int CONTEXT_BONUS_BREADTH_45;
    @Value("${scoring.context.penalty.breadth-30:5}") private int CONTEXT_PENALTY_BREADTH_30;
    @Value("${scoring.context.penalty.breadth-15:20}") private int CONTEXT_PENALTY_BREADTH_15;
    @Value("${scoring.context.penalty.breadth-0:35}") private int CONTEXT_PENALTY_BREADTH_0;

    // Fundamental weights
    @Value("${scoring.fundamental.bonus.yoy-30:10}") private int FUNDAMENTAL_BONUS_YOY_30;
    @Value("${scoring.fundamental.bonus.yoy-15:6}") private int FUNDAMENTAL_BONUS_YOY_15;
    @Value("${scoring.fundamental.penalty.yoy-negative:10}") private int FUNDAMENTAL_PENALTY_YOY_NEGATIVE;
    @Value("${scoring.fundamental.bonus.mom-10:8}") private int FUNDAMENTAL_BONUS_MOM_10;
    @Value("${scoring.fundamental.bonus.mom-positive:4}") private int FUNDAMENTAL_BONUS_MOM_POSITIVE;
    @Value("${scoring.fundamental.penalty.mom-negative-10:8}") private int FUNDAMENTAL_PENALTY_MOM_NEGATIVE_10;

    // News reaction normalization
    @Value("${scoring.news.raw-min:-15}") private int NEWS_RAW_MIN;
    @Value("${scoring.news.raw-max:15}") private int NEWS_RAW_MAX;
    @Value("${scoring.news.neutral-score:50}") private int NEWS_NEUTRAL_SCORE;

    public synchronized Map<String, Object> reloadScoringConfig() {
        Map<String, Object> result = new LinkedHashMap<>();
        Path source = resolveConfigPath();
        if (source == null) {
            result.put("success", false);
            result.put("message", "找不到可重載的 application.properties（請確認工作目錄或傳入 spring.config.location）");
            return result;
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(source)) {
            props.load(in);
        } catch (IOException e) {
            result.put("success", false);
            result.put("message", "重載失敗: " + e.getMessage());
            result.put("source", source.toString());
            return result;
        }

        MONEY_WEIGHT_INSTITUTIONAL = readDouble(props, "scoring.money.weight.institutional", MONEY_WEIGHT_INSTITUTIONAL);
        MONEY_WEIGHT_BIG_HOLDER = readDouble(props, "scoring.money.weight.big-holder", MONEY_WEIGHT_BIG_HOLDER);

        TREND_BONUS_PRICE_ABOVE_MA5 = readInt(props, "scoring.trend.bonus.price-above-ma5", TREND_BONUS_PRICE_ABOVE_MA5);
        TREND_PENALTY_PRICE_BELOW_MA5 = readInt(props, "scoring.trend.penalty.price-below-ma5", TREND_PENALTY_PRICE_BELOW_MA5);
        TREND_BONUS_MA5_ABOVE_MA10 = readInt(props, "scoring.trend.bonus.ma5-above-ma10", TREND_BONUS_MA5_ABOVE_MA10);
        TREND_PENALTY_MA5_BELOW_MA10 = readInt(props, "scoring.trend.penalty.ma5-below-ma10", TREND_PENALTY_MA5_BELOW_MA10);
        TREND_BONUS_MA10_ABOVE_MA20 = readInt(props, "scoring.trend.bonus.ma10-above-ma20", TREND_BONUS_MA10_ABOVE_MA20);
        TREND_PENALTY_MA10_BELOW_MA20 = readInt(props, "scoring.trend.penalty.ma10-below-ma20", TREND_PENALTY_MA10_BELOW_MA20);
        TREND_BONUS_MA20_ABOVE_MA60 = readInt(props, "scoring.trend.bonus.ma20-above-ma60", TREND_BONUS_MA20_ABOVE_MA60);
        TREND_PENALTY_MA20_BELOW_MA60 = readInt(props, "scoring.trend.penalty.ma20-below-ma60", TREND_PENALTY_MA20_BELOW_MA60);
        TREND_BONUS_DISTANCE_CORE = readInt(props, "scoring.trend.bonus.distance-core", TREND_BONUS_DISTANCE_CORE);
        TREND_BONUS_DISTANCE_HIGH = readInt(props, "scoring.trend.bonus.distance-high", TREND_BONUS_DISTANCE_HIGH);
        TREND_BONUS_DISTANCE_RECOVERY = readInt(props, "scoring.trend.bonus.distance-recovery", TREND_BONUS_DISTANCE_RECOVERY);
        TREND_PENALTY_DISTANCE_OVERHEAT = readInt(props, "scoring.trend.penalty.distance-overheat", TREND_PENALTY_DISTANCE_OVERHEAT);
        TREND_PENALTY_DISTANCE_BUBBLE = readInt(props, "scoring.trend.penalty.distance-bubble", TREND_PENALTY_DISTANCE_BUBBLE);
        TREND_PENALTY_DISTANCE_WEAK = readInt(props, "scoring.trend.penalty.distance-weak", TREND_PENALTY_DISTANCE_WEAK);
        TREND_PENALTY_DISTANCE_BROKEN = readInt(props, "scoring.trend.penalty.distance-broken", TREND_PENALTY_DISTANCE_BROKEN);

        MOMENTUM_BONUS_RSI_HEALTHY = readInt(props, "scoring.momentum.bonus.rsi-healthy", MOMENTUM_BONUS_RSI_HEALTHY);
        MOMENTUM_BONUS_RSI_GOOD = readInt(props, "scoring.momentum.bonus.rsi-good", MOMENTUM_BONUS_RSI_GOOD);
        MOMENTUM_BONUS_RSI_HOT = readInt(props, "scoring.momentum.bonus.rsi-hot", MOMENTUM_BONUS_RSI_HOT);
        MOMENTUM_BONUS_RSI_OVERSOLD = readInt(props, "scoring.momentum.bonus.rsi-oversold", MOMENTUM_BONUS_RSI_OVERSOLD);
        MOMENTUM_PENALTY_RSI_OVERHEAT = readInt(props, "scoring.momentum.penalty.rsi-overheat", MOMENTUM_PENALTY_RSI_OVERHEAT);
        MOMENTUM_PENALTY_RSI_EXTREME_WEAK = readInt(props, "scoring.momentum.penalty.rsi-extreme-weak", MOMENTUM_PENALTY_RSI_EXTREME_WEAK);
        MOMENTUM_BONUS_MACD_POSITIVE = readInt(props, "scoring.momentum.bonus.macd-positive", MOMENTUM_BONUS_MACD_POSITIVE);
        MOMENTUM_BONUS_MACD_EXPANDING = readInt(props, "scoring.momentum.bonus.macd-expanding", MOMENTUM_BONUS_MACD_EXPANDING);
        MOMENTUM_PENALTY_MACD_SHRINKING = readInt(props, "scoring.momentum.penalty.macd-shrinking", MOMENTUM_PENALTY_MACD_SHRINKING);
        MOMENTUM_BONUS_MACD_NEAR_ZERO = readInt(props, "scoring.momentum.bonus.macd-near-zero", MOMENTUM_BONUS_MACD_NEAR_ZERO);
        MOMENTUM_PENALTY_MACD_WEAK_NEGATIVE = readInt(props, "scoring.momentum.penalty.macd-weak-negative", MOMENTUM_PENALTY_MACD_WEAK_NEGATIVE);
        MOMENTUM_PENALTY_MACD_STRONG_NEGATIVE = readInt(props, "scoring.momentum.penalty.macd-strong-negative", MOMENTUM_PENALTY_MACD_STRONG_NEGATIVE);
        MOMENTUM_BONUS_KD_GOLDEN = readInt(props, "scoring.momentum.bonus.kd-golden", MOMENTUM_BONUS_KD_GOLDEN);
        MOMENTUM_BONUS_KD_GOLDEN_LOW_ZONE = readInt(props, "scoring.momentum.bonus.kd-golden-low-zone", MOMENTUM_BONUS_KD_GOLDEN_LOW_ZONE);
        MOMENTUM_BONUS_KD_ABOVE = readInt(props, "scoring.momentum.bonus.kd-above", MOMENTUM_BONUS_KD_ABOVE);
        MOMENTUM_PENALTY_KD_BELOW = readInt(props, "scoring.momentum.penalty.kd-below", MOMENTUM_PENALTY_KD_BELOW);
        MOMENTUM_PENALTY_KD_DEATH = readInt(props, "scoring.momentum.penalty.kd-death", MOMENTUM_PENALTY_KD_DEATH);
        MOMENTUM_PENALTY_KD_DEATH_HIGH_ZONE = readInt(props, "scoring.momentum.penalty.kd-death-high-zone", MOMENTUM_PENALTY_KD_DEATH_HIGH_ZONE);

        MONEY_BONUS_TRUST_DAYS_8 = readInt(props, "scoring.money.bonus.trust-days-8", MONEY_BONUS_TRUST_DAYS_8);
        MONEY_BONUS_TRUST_DAYS_5 = readInt(props, "scoring.money.bonus.trust-days-5", MONEY_BONUS_TRUST_DAYS_5);
        MONEY_BONUS_TRUST_DAYS_3 = readInt(props, "scoring.money.bonus.trust-days-3", MONEY_BONUS_TRUST_DAYS_3);
        MONEY_BONUS_TRUST_DAYS_1 = readInt(props, "scoring.money.bonus.trust-days-1", MONEY_BONUS_TRUST_DAYS_1);
        MONEY_PENALTY_TRUST_DAYS_0 = readInt(props, "scoring.money.penalty.trust-days-0", MONEY_PENALTY_TRUST_DAYS_0);
        MONEY_BONUS_LOCK_RATIO_15 = readInt(props, "scoring.money.bonus.lock-ratio-15", MONEY_BONUS_LOCK_RATIO_15);
        MONEY_BONUS_LOCK_RATIO_8 = readInt(props, "scoring.money.bonus.lock-ratio-8", MONEY_BONUS_LOCK_RATIO_8);
        MONEY_BONUS_LOCK_RATIO_3 = readInt(props, "scoring.money.bonus.lock-ratio-3", MONEY_BONUS_LOCK_RATIO_3);
        MONEY_PENALTY_LOCK_RATIO_LOW = readInt(props, "scoring.money.penalty.lock-ratio-low", MONEY_PENALTY_LOCK_RATIO_LOW);
        MONEY_BONUS_NET_BUY_POSITIVE = readInt(props, "scoring.money.bonus.net-buy-positive", MONEY_BONUS_NET_BUY_POSITIVE);
        MONEY_BONUS_NET_BUY_ABOVE_AVG = readInt(props, "scoring.money.bonus.net-buy-above-avg", MONEY_BONUS_NET_BUY_ABOVE_AVG);
        MONEY_PENALTY_NET_BUY_NEGATIVE = readInt(props, "scoring.money.penalty.net-buy-negative", MONEY_PENALTY_NET_BUY_NEGATIVE);
        MONEY_PENALTY_NET_BUY_BELOW_AVG = readInt(props, "scoring.money.penalty.net-buy-below-avg", MONEY_PENALTY_NET_BUY_BELOW_AVG);
        MONEY_PENALTY_NET_BUY_AVG_NEGATIVE = readInt(props, "scoring.money.penalty.net-buy-avg-negative", MONEY_PENALTY_NET_BUY_AVG_NEGATIVE);

        VOLATILITY_BASE_WHEN_RANGE_INVALID = readInt(props, "scoring.volatility.base-when-range-invalid", VOLATILITY_BASE_WHEN_RANGE_INVALID);
        VOLATILITY_SQUEEZE_SCALE = readDouble(props, "scoring.volatility.squeeze-scale", VOLATILITY_SQUEEZE_SCALE);
        VOLATILITY_BONUS_OPENING = readInt(props, "scoring.volatility.bonus.opening", VOLATILITY_BONUS_OPENING);
        VOLATILITY_BONUS_LOW_BAND = readInt(props, "scoring.volatility.bonus.low-band", VOLATILITY_BONUS_LOW_BAND);
        VOLATILITY_PENALTY_EXPANDED = readInt(props, "scoring.volatility.penalty.expanded", VOLATILITY_PENALTY_EXPANDED);
        VOLATILITY_PENALTY_OVER_EXPANDED = readInt(props, "scoring.volatility.penalty.over-expanded", VOLATILITY_PENALTY_OVER_EXPANDED);
        VOLATILITY_PENALTY_OPENING_OVER_EXPANDED = readInt(props, "scoring.volatility.penalty.opening-over-expanded", VOLATILITY_PENALTY_OPENING_OVER_EXPANDED);

        CONTEXT_BASE_SCORE = readInt(props, "scoring.context.base-score", CONTEXT_BASE_SCORE);
        CONTEXT_BONUS_BREADTH_80 = readInt(props, "scoring.context.bonus.breadth-80", CONTEXT_BONUS_BREADTH_80);
        CONTEXT_BONUS_BREADTH_70 = readInt(props, "scoring.context.bonus.breadth-70", CONTEXT_BONUS_BREADTH_70);
        CONTEXT_BONUS_BREADTH_55 = readInt(props, "scoring.context.bonus.breadth-55", CONTEXT_BONUS_BREADTH_55);
        CONTEXT_BONUS_BREADTH_45 = readInt(props, "scoring.context.bonus.breadth-45", CONTEXT_BONUS_BREADTH_45);
        CONTEXT_PENALTY_BREADTH_30 = readInt(props, "scoring.context.penalty.breadth-30", CONTEXT_PENALTY_BREADTH_30);
        CONTEXT_PENALTY_BREADTH_15 = readInt(props, "scoring.context.penalty.breadth-15", CONTEXT_PENALTY_BREADTH_15);
        CONTEXT_PENALTY_BREADTH_0 = readInt(props, "scoring.context.penalty.breadth-0", CONTEXT_PENALTY_BREADTH_0);

        FUNDAMENTAL_BONUS_YOY_30 = readInt(props, "scoring.fundamental.bonus.yoy-30", FUNDAMENTAL_BONUS_YOY_30);
        FUNDAMENTAL_BONUS_YOY_15 = readInt(props, "scoring.fundamental.bonus.yoy-15", FUNDAMENTAL_BONUS_YOY_15);
        FUNDAMENTAL_PENALTY_YOY_NEGATIVE = readInt(props, "scoring.fundamental.penalty.yoy-negative", FUNDAMENTAL_PENALTY_YOY_NEGATIVE);
        FUNDAMENTAL_BONUS_MOM_10 = readInt(props, "scoring.fundamental.bonus.mom-10", FUNDAMENTAL_BONUS_MOM_10);
        FUNDAMENTAL_BONUS_MOM_POSITIVE = readInt(props, "scoring.fundamental.bonus.mom-positive", FUNDAMENTAL_BONUS_MOM_POSITIVE);
        FUNDAMENTAL_PENALTY_MOM_NEGATIVE_10 = readInt(props, "scoring.fundamental.penalty.mom-negative-10", FUNDAMENTAL_PENALTY_MOM_NEGATIVE_10);

        NEWS_RAW_MIN = readInt(props, "scoring.news.raw-min", NEWS_RAW_MIN);
        NEWS_RAW_MAX = readInt(props, "scoring.news.raw-max", NEWS_RAW_MAX);
        NEWS_NEUTRAL_SCORE = readInt(props, "scoring.news.neutral-score", NEWS_NEUTRAL_SCORE);

        result.put("success", true);
        result.put("message", "scoring.* 參數已重載");
        result.put("source", source.toString());
        result.put("reloadedAt", Instant.now().toString());
        result.put("moneyWeights", Map.of(
                "institutional", MONEY_WEIGHT_INSTITUTIONAL,
                "bigHolder", MONEY_WEIGHT_BIG_HOLDER
        ));
        return result;
    }

    private Path resolveConfigPath() {
        List<Path> candidates = List.of(
                Paths.get("src/main/resources/application.properties"),
                Paths.get("application.properties")
        );
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private int readInt(Properties props, String key, int current) {
        String raw = props.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return current;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return current;
        }
    }

    private double readDouble(Properties props, String key, double current) {
        String raw = props.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return current;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return current;
        }
    }

    public int scoreTrend(double price, double ma5, double ma10, double ma20, double ma60) {
        if (price <= 0 || ma5 <= 0 || ma10 <= 0 || ma20 <= 0 || ma60 <= 0) {
            return 0;
        }

        double score = 0;
        if (price > ma5) score += TREND_BONUS_PRICE_ABOVE_MA5; else score -= TREND_PENALTY_PRICE_BELOW_MA5;
        if (ma5 > ma10) score += TREND_BONUS_MA5_ABOVE_MA10; else score -= TREND_PENALTY_MA5_BELOW_MA10;
        if (ma10 > ma20) score += TREND_BONUS_MA10_ABOVE_MA20; else score -= TREND_PENALTY_MA10_BELOW_MA20;
        if (ma20 > ma60) score += TREND_BONUS_MA20_ABOVE_MA60; else score -= TREND_PENALTY_MA20_BELOW_MA60;

        double distancePct = (price - ma20) / ma20 * 100.0;
        if (distancePct >= 0 && distancePct <= 8) {
            score += TREND_BONUS_DISTANCE_CORE;
        } else if (distancePct > 8 && distancePct <= 15) {
            score += TREND_BONUS_DISTANCE_HIGH;
        } else if (distancePct < 0 && distancePct >= -5) {
            score += TREND_BONUS_DISTANCE_RECOVERY;
        } else if (distancePct > 15 && distancePct <= 25) {
            score -= TREND_PENALTY_DISTANCE_OVERHEAT;
        } else if (distancePct > 25) {
            score -= TREND_PENALTY_DISTANCE_BUBBLE;
        } else if (distancePct < -5 && distancePct >= -12) {
            score -= TREND_PENALTY_DISTANCE_WEAK;
        } else if (distancePct < -12) {
            score -= TREND_PENALTY_DISTANCE_BROKEN;
        }

        return clampScore(score);
    }

    public int scoreMomentum(double rsi,
                             double macdHist,
                             double macdHistPrev,
                             double k,
                             double d,
                             double prevK,
                             double prevD) {
        double score = 0;

        if (rsi >= 45 && rsi <= 65) {
            score += MOMENTUM_BONUS_RSI_HEALTHY;
        } else if (rsi > 35 && rsi < 75) {
            score += MOMENTUM_BONUS_RSI_GOOD;
        } else if (rsi >= 75 && rsi <= 85) {
            score += MOMENTUM_BONUS_RSI_HOT;
        } else if (rsi >= 20 && rsi <= 35) {
            score += MOMENTUM_BONUS_RSI_OVERSOLD;
        } else if (rsi > 85) {
            score -= MOMENTUM_PENALTY_RSI_OVERHEAT;
        } else if (rsi < 20) {
            score -= MOMENTUM_PENALTY_RSI_EXTREME_WEAK;
        }

        if (macdHist > 0) {
            score += MOMENTUM_BONUS_MACD_POSITIVE;
            if (macdHist > macdHistPrev) {
                score += MOMENTUM_BONUS_MACD_EXPANDING;
            } else {
                score -= MOMENTUM_PENALTY_MACD_SHRINKING;
            }
        } else if (macdHist > -0.02) {
            score += MOMENTUM_BONUS_MACD_NEAR_ZERO;
        } else if (macdHist > -0.05) {
            score -= MOMENTUM_PENALTY_MACD_WEAK_NEGATIVE;
        } else {
            score -= MOMENTUM_PENALTY_MACD_STRONG_NEGATIVE;
        }

        boolean kdGoldenCross = (k > d) && (prevK <= prevD);
        boolean kdDeathCross = (k < d) && (prevK >= prevD);
        if (kdGoldenCross) {
            score += MOMENTUM_BONUS_KD_GOLDEN;
            if (k < 30 && d < 30) {
                score += MOMENTUM_BONUS_KD_GOLDEN_LOW_ZONE;
            }
        } else if (k > d) {
            score += MOMENTUM_BONUS_KD_ABOVE;
        } else {
            score -= MOMENTUM_PENALTY_KD_BELOW;
        }

        if (kdDeathCross) {
            score -= MOMENTUM_PENALTY_KD_DEATH;
            if (k > 80 && d > 80) {
                score -= MOMENTUM_PENALTY_KD_DEATH_HIGH_ZONE;
            }
        }

        return clampScore(score);
    }

    public int scoreMoney(int trustContinuousBuyDays,
                          double lockRatioPct,
                          long latestNetBuy,
                          double avgNetBuy5) {
        return scoreMoney(trustContinuousBuyDays, lockRatioPct, latestNetBuy, avgNetBuy5, 0);
    }

    public int scoreMoney(int trustContinuousBuyDays,
                          double lockRatioPct,
                          long latestNetBuy,
                          double avgNetBuy5,
                          int bigHolderScore) {
        int institutionalScore = scoreMoneyInstitutional(trustContinuousBuyDays, lockRatioPct, latestNetBuy, avgNetBuy5);
        double bigHolderNormalizedScore = clamp(bigHolderScore, 0.0, 20.0) * 5.0;
        double fusedScore = institutionalScore * MONEY_WEIGHT_INSTITUTIONAL
                + bigHolderNormalizedScore * MONEY_WEIGHT_BIG_HOLDER;
        return clampScore(fusedScore);
    }

    private int scoreMoneyInstitutional(int trustContinuousBuyDays,
                                        double lockRatioPct,
                                        long latestNetBuy,
                                        double avgNetBuy5) {
        double score = 0;

        if (trustContinuousBuyDays >= 8) {
            score += MONEY_BONUS_TRUST_DAYS_8;
        } else if (trustContinuousBuyDays >= 5) {
            score += MONEY_BONUS_TRUST_DAYS_5;
        } else if (trustContinuousBuyDays >= 3) {
            score += MONEY_BONUS_TRUST_DAYS_3;
        } else if (trustContinuousBuyDays >= 1) {
            score += MONEY_BONUS_TRUST_DAYS_1;
        } else {
            score -= MONEY_PENALTY_TRUST_DAYS_0;
        }

        if (lockRatioPct >= 15.0) {
            score += MONEY_BONUS_LOCK_RATIO_15;
        } else if (lockRatioPct >= 8.0) {
            score += MONEY_BONUS_LOCK_RATIO_8;
        } else if (lockRatioPct >= 3.0) {
            score += MONEY_BONUS_LOCK_RATIO_3;
        } else if (lockRatioPct < 1.0) {
            score -= MONEY_PENALTY_LOCK_RATIO_LOW;
        }

        if (latestNetBuy > 0) {
            score += MONEY_BONUS_NET_BUY_POSITIVE;
            if (latestNetBuy > avgNetBuy5) {
                score += MONEY_BONUS_NET_BUY_ABOVE_AVG;
            }
        } else {
            score -= MONEY_PENALTY_NET_BUY_NEGATIVE;
            if (latestNetBuy < avgNetBuy5) {
                score -= MONEY_PENALTY_NET_BUY_BELOW_AVG;
            }
            if (avgNetBuy5 < 0) {
                score -= MONEY_PENALTY_NET_BUY_AVG_NEGATIVE;
            }
        }

        return clampScore(score);
    }

    public int scoreVolatility(double bbw, double bbwMin, double bbwMax, boolean opening) {
        if (bbw < 0 || bbwMin < 0 || bbwMax < 0) {
            return 0;
        }

        double score;
        double normalized = 0.5;
        if (bbwMax <= bbwMin) {
            score = VOLATILITY_BASE_WHEN_RANGE_INVALID;
        } else {
            normalized = (bbw - bbwMin) / (bbwMax - bbwMin);
            normalized = clamp(normalized, 0.0, 1.0);
            double squeeze = 1.0 - normalized;
            score = squeeze * VOLATILITY_SQUEEZE_SCALE;
        }

        if (opening && normalized <= 0.65) {
            score += VOLATILITY_BONUS_OPENING;
        } else if (bbw <= bbwMin + (bbwMax - bbwMin) * 0.35) {
            score += VOLATILITY_BONUS_LOW_BAND;
        }

        if (normalized > 0.85) {
            score -= VOLATILITY_PENALTY_OVER_EXPANDED;
        } else if (normalized > 0.70) {
            score -= VOLATILITY_PENALTY_EXPANDED;
        }

        if (opening && normalized > 0.80) {
            score -= VOLATILITY_PENALTY_OPENING_OVER_EXPANDED;
        }

        return clampScore(score);
    }

    public int scoreContext(double marketBreadthPct) {
        double score = CONTEXT_BASE_SCORE;
        if (marketBreadthPct >= 80) score += CONTEXT_BONUS_BREADTH_80;
        else if (marketBreadthPct >= 70) score += CONTEXT_BONUS_BREADTH_70;
        else if (marketBreadthPct >= 55) score += CONTEXT_BONUS_BREADTH_55;
        else if (marketBreadthPct >= 45) score += CONTEXT_BONUS_BREADTH_45;
        else if (marketBreadthPct >= 30) score -= CONTEXT_PENALTY_BREADTH_30;
        else if (marketBreadthPct >= 15) score -= CONTEXT_PENALTY_BREADTH_15;
        else score -= CONTEXT_PENALTY_BREADTH_0;
        return clampScore(score);
    }

    public int scoreFundamental(int revenueScore, double latestYoy, double latestMom) {
        double score = clamp(revenueScore, 0.0, 20.0) * 5.0;

        if (latestYoy > 30.0) {
            score += FUNDAMENTAL_BONUS_YOY_30;
        } else if (latestYoy > 15.0) {
            score += FUNDAMENTAL_BONUS_YOY_15;
        } else if (latestYoy < 0.0) {
            score -= FUNDAMENTAL_PENALTY_YOY_NEGATIVE;
        }

        if (latestMom > 10.0) {
            score += FUNDAMENTAL_BONUS_MOM_10;
        } else if (latestMom > 0.0) {
            score += FUNDAMENTAL_BONUS_MOM_POSITIVE;
        } else if (latestMom < -10.0) {
            score -= FUNDAMENTAL_PENALTY_MOM_NEGATIVE_10;
        }

        return clampScore(score);
    }

    public int scoreNewsReaction(int rawNewsScore) {
        if (NEWS_RAW_MAX <= NEWS_RAW_MIN) {
            return clampScore(NEWS_NEUTRAL_SCORE);
        }
        double normalized = (rawNewsScore - NEWS_RAW_MIN) * 100.0 / (NEWS_RAW_MAX - NEWS_RAW_MIN);
        return clampScore(normalized);
    }

    private int clampScore(double value) {
        return (int) Math.round(clamp(value, 0.0, 100.0));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}

