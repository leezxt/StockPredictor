package org.gtalent;

import org.springframework.stereotype.Service;

/**
 * 精細化 RSI 動能評分服務
 *
 * 算法規則（機構級權重）：
 * 1. 基礎分：日 RSI 與週 RSI 加權混合
 *    - 散戶當沖氾濫（dayTradingRate ≥ 55%）→ 日 RSI 減權至 30%，週 RSI 提權至 70%（降噪）
 *    - 正常情況 → 日 RSI 60%，週 RSI 40%
 *
 * 2. 黃金交叉與死亡交叉（±15 分）
 *    - KD 黃金交叉 + 中軸以上（K>D AND RSI>50）→ +15（多頭轉強）
 *    - KD 死亡交叉 + 中軸以下（K<D AND RSI<50）→ -15（空頭加速）
 *
 * 3. 背離引擎熔斷（絕對判定）
 *    - 底背離（股價新低，但 RSI 不同步新低）→ 直接 95（珍珠股潛力）
 *    - 頂背離（股價新高，但 RSI 不同步新高）→ 直接 10（出貨陷阱）
 *
 * 4. 50 軸鐵壁支撐（+10 分）
 *    - 多頭主升段特徵：RSI 持續守住 50 軸支撐 → +10（鐵壁動能）
 */
@Service
public class RsiAdvancedService {

    /**
     * 計算精細化動能得分
     *
     * @param rsiData         精細化 RSI 數據（含日線、週線、背離等）
     * @param dayTradingRate  當沖率（0~1；≥0.55 → 散戶當沖氾濫）
     * @return                動能分數（0~100）
     */
    public int calculateRefinedMomentum(RefinedRsiData rsiData, double dayTradingRate) {
        if (rsiData == null) {
            return 50; // 數據不足，取中性值
        }

        double rsi14 = rsiData.getRsi14();
        double rsiMa9 = rsiData.getRsiMa9();
        double weeklyRsi14 = rsiData.getWeeklyRsi14();
        boolean isBullishDivergence = rsiData.isBullishDivergence();
        boolean isBearishDivergence = rsiData.isBearishDivergence();

        // ========== 背離引擎熔斷（最優先判定） ==========
        // 底背離：股價創新低但 RSI 止跌回升 → 強力珍珠股訊號
        if (isBullishDivergence) {
            return 95; // 直接賦予極高動能預期
        }
        // 頂背離：股價創新高但 RSI 無力新高 → 主力出貨陷阱
        if (isBearishDivergence) {
            return 10; // 強制降至冰點
        }

        // ========== 核心基礎分：日線 + 週線加權混合 ==========
        // 當沖率 ≥ 55% → 散戶過度投機，減低日線權重，提高週線降噪
        // 當沖率 < 55%  → 正常分佈，日線更敏感
        double baseRsi;
        if (dayTradingRate >= 0.55) {
            // 高當沖環境：減少日線雜訊，以週線長線判定為主
            baseRsi = rsi14 * 0.3 + weeklyRsi14 * 0.7;
        } else {
            // 正常環境：平衡日線敏感度與週線趨勢
            baseRsi = rsi14 * 0.6 + weeklyRsi14 * 0.4;
        }

        double finalScore = baseRsi;

        // ========== 細化規則 1：黃金交叉 / 死亡交叉 ==========
        // 黃金交叉：K 線向上穿越 D 線，且處於中軸以上 → 多頭確認轉強
        if (rsi14 > rsiMa9 && rsi14 > 50) {
            finalScore += 15; // 多頭轉強加分
        }
        // 死亡交叉：K 線向下穿越 D 線，且處於中軸以下 → 空頭加速破位
        else if (rsi14 < rsiMa9 && rsi14 < 50) {
            finalScore -= 15; // 空頭加速減分
        }

        // ========== 細化規則 2：50 軸鐵壁支撐 ==========
        // 多頭主升段表現：RSI 持續守住 50 軸支撐 → 鐵壁級別動能加分
        if (rsiData.isHldingFiftySupport()) {
            finalScore += 10;
        }

        // ========== 最終得分範圍限制 ==========
        return (int) Math.max(0, Math.min(100, finalScore));
    }

    /**
     * 簡化版本：僅用日線 RSI 計算（無背離判定）
     * 用於快速評估，不涉及複雜背離檢查
     */
    public int calculateSimpleMomentum(double rsi14, double rsiMa9, double dayTradingRate) {
        double baseRsi = (dayTradingRate >= 0.55)
            ? rsi14 * 0.5 + 50 * 0.5  // 高當沖時，降低敏感度
            : rsi14;

        double score = baseRsi;

        // 黃金 / 死亡交叉
        if (rsi14 > rsiMa9 && rsi14 > 50) {
            score += 10;
        } else if (rsi14 < rsiMa9 && rsi14 < 50) {
            score -= 10;
        }

        return (int) Math.max(0, Math.min(100, score));
    }
}

