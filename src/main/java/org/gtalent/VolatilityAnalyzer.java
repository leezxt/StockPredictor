package org.gtalent;

import java.util.List;

public class VolatilityAnalyzer {

    public int calculateBBWScore(List<Double> bbwHistory) {
        if (bbwHistory == null || bbwHistory.size() < 2) {
            return 0;
        }

        int score = 0;
        double currentBBW = bbwHistory.get(bbwHistory.size() - 1);

        // 取最近 60 筆的最小值（如果不足 60 筆則取全部）
        int lookback = Math.min(bbwHistory.size(), 60);
        double minBBW = bbwHistory.subList(bbwHistory.size() - lookback, bbwHistory.size())
                                  .stream()
                                  .mapToDouble(v -> v)
                                  .min()
                                  .orElse(0);

        // 1. 偵測擠壓狀態
        if (currentBBW <= minBBW * 1.1) { // 接近 60 日最低寬度的 110%
            score += 15; // 潛伏加分
        }

        // 2. 偵測擴張斜率 (爆發力)
        double prevBBW = bbwHistory.get(bbwHistory.size() - 2);
        if (currentBBW > prevBBW && currentBBW > minBBW) {
            score += 10; // 動能釋放中
        }

        return score;
    }
}

