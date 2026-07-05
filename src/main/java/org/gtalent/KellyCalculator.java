package org.gtalent;

/**
 * 凱利公式計算器
 * 用來計算最佳資金配置比例，避免過度下注導致資金崩潰。
 *
 * Kelly 公式：f = (p*b - q) / b
 *   p = 勝率
 *   q = 1 - p（敗率）
 *   b = 盈虧比 (avgProfit / |avgLoss|)
 *
 * 實務上使用 Half-Kelly（半凱利）以降低波動風險。
 */
public class KellyCalculator {

    private KellyCalculator() {
        // Utility class
    }

    /**
     * 計算 Half-Kelly 倉位比例。
     *
     * @param winRate   勝率（0~1，例如 0.6 代表 60%）
     * @param avgProfit 平均獲利金額（正數）
     * @param avgLoss   平均虧損金額（正數或負數均可，取絕對值使用）
     * @return Half-Kelly 建議倉位比例（0~1），若公式為負值則回傳 0
     */
    public static double calculateKelly(double winRate, double avgProfit, double avgLoss) {
        double absLoss = Math.abs(avgLoss);
        if (absLoss == 0 || avgProfit <= 0) {
            return 0.0;
        }

        double p = Math.max(0.0, Math.min(1.0, winRate)); // 限制在 [0, 1]
        double q = 1.0 - p;
        double b = avgProfit / absLoss; // 盈虧比

        double fullKelly = (p * b - q) / b;

        // 半凱利：降低波動，實務更常用
        return Math.max(0.0, fullKelly * 0.5);
    }

    /**
     * 計算完整 Kelly（未折半）。
     * 通常不建議直接使用，僅供參考。
     */
    public static double calculateFullKelly(double winRate, double avgProfit, double avgLoss) {
        double absLoss = Math.abs(avgLoss);
        if (absLoss == 0 || avgProfit <= 0) {
            return 0.0;
        }

        double p = Math.max(0.0, Math.min(1.0, winRate));
        double q = 1.0 - p;
        double b = avgProfit / absLoss;

        return Math.max(0.0, (p * b - q) / b);
    }

    /**
     * 根據 BacktestResult 直接計算並回傳 Half-Kelly 建議倉位（百分比，0~100）。
     *
     * @param result 回測結果
     * @return 建議倉位百分比，例如 25.0 代表 25%
     */
    public static double fromBacktestResult(BacktestResult result) {
        if (result == null || result.tradeCount == 0) {
            return 0.0;
        }

        double winRate = result.winRate / 100.0;
        double avgProfit = result.avgProfit;
        double avgLoss = Math.abs(result.avgLoss);

        if (avgProfit <= 0 || avgLoss <= 0) {
            return 0.0;
        }

        double halfKelly = calculateKelly(winRate, avgProfit, avgLoss);
        // 回傳百分比，最高限制 50%（避免極端情況）
        return Math.min(50.0, Math.round(halfKelly * 10000.0) / 100.0);
    }
}

