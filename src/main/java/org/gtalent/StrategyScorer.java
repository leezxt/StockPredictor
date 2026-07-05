package org.gtalent;

public class StrategyScorer {
    public int calculateScore(double rsi, boolean macdGoldenCross, boolean volumeUp, boolean strongThanMarket) {
        int score = 0;
        if (rsi < 60) {
            score += 25; // 沒過熱，加分
        }
        if (macdGoldenCross) {
            score += 25; // 動能強，加分
        }
        if (volumeUp) {
            score += 25; // 有量支撐，加分
        }
        if (strongThanMarket) {
            score += 25; // 強於大盤，加分
        }
        return score; // 滿分 100
    }
}

