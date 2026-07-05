package org.gtalent;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * KD 高級動能引擎
 * 
 * 提供專業級的 KD 指標分析，包含：
 * 1. 高檔/低檔鈍化偵測（連續3天）
 * 2. 黃金交叉/死亡交叉位置細化
 * 3. 低檔背離偵測
 * 4. 綜合評分機制
 * 
 * @author StockPredictor Team
 * @version 2.1
 */
@Service
public class KdAdvancedService {

    /**
     * 分析 KD 動能強度（簡化版）
     * 
     * @param kdHistory KD 歷史資料（至少需要 5 筆）
     * @return KD 動能評分（-15 ~ +37）
     */
    public int analyzeKdStrength(List<KdData> kdHistory) {
        return analyze(kdHistory).getScore();
    }

    /**
     * 完整分析 KD 動能（含信號說明）
     * 
     * 評分規則：
     * - 高檔鈍化：+15 分（飆股基因）
     * - 低檔鈍化：-15 分（避免抄底）
     * - 低檔黃金交叉：+10 分（安全進場點）
     * - 中軸黃金交叉：+12 分（多頭加速點）
     * - 高檔死亡交叉：-12 分（獲利了結）
     * - 低檔背離：+10 分（反轉訊號）
     * 
     * @param kdHistory KD 歷史資料列表
     * @return 分析結果（包含評分和信號列表）
     */
    public AnalysisResult analyze(List<KdData> kdHistory) {
        if (kdHistory == null || kdHistory.size() < 5) {
            System.out.println("⚠️ KD 資料不足，至少需要 5 筆歷史資料");
            return new AnalysisResult(0, Collections.singletonList("KD 資料不足（至少需要 5 筆）"));
        }

        int score = 0;
        List<String> signals = new ArrayList<>();
        int len = kdHistory.size();

        // 取得最近的資料點
        KdData today = kdHistory.get(len - 1);
        KdData yesterday = kdHistory.get(len - 2);

        System.out.println("📊 開始分析 KD 動能：K=" + today.getKValue() + ", D=" + today.getDValue());

        // ════════════════════════════════════════════════════════════════
        // 1. 偵測高檔/低檔鈍化（連續 3 天）
        // ════════════════════════════════════════════════════════════════
        boolean highPassivation = true;  // 高檔鈍化（K >= 80）
        boolean lowPassivation = true;   // 低檔鈍化（K <= 20）
        
        for (int i = 1; i <= 3; i++) {
            KdData data = kdHistory.get(len - i);
            if (data.getKValue() < 80) {
                highPassivation = false;
            }
            if (data.getKValue() > 20) {
                lowPassivation = false;
            }
        }

        if (highPassivation) {
            System.out.println("🔥 強勢高檔鈍化！極端多頭動能");
            score += 15; // 飆股基因，給予高分
            signals.add("🔥 強勢高檔鈍化：極端多頭動能（連續3天 K >= 80）");
        } else if (lowPassivation) {
            System.out.println("⚠️ 低檔鈍化！千萬不可抄底");
            score -= 15; // 弱勢股，扣分
            signals.add("⚠️ 低檔鈍化：避免盲目抄底（連續3天 K <= 20）");
        }

        // ════════════════════════════════════════════════════════════════
        // 2. 判斷黃金交叉 / 死亡交叉的位置細化
        // ════════════════════════════════════════════════════════════════
        boolean wasKBelowD = yesterday.getKValue() < yesterday.getDValue();
        boolean isKAboveD = today.getKValue() > today.getDValue();

        // 黃金交叉（K 線向上穿越 D 線）
        if (wasKBelowD && isKAboveD) {
            if (today.getKValue() < 30) {
                score += 10; // 低檔黃金交叉（安全打底進場點）
                System.out.println("✅ 低檔黃金交叉（K<30）：安全打底進場點");
                signals.add("✅ 低檔黃金交叉：安全打底進場點（K=" + String.format("%.2f", today.getKValue()) + "）");
            } else if (today.getKValue() >= 45 && today.getKValue() <= 55) {
                score += 12; // 中軸 50 強勢突破（多頭加速點）
                System.out.println("🚀 中軸黃金交叉（K=45~55）：多頭加速點");
                signals.add("🚀 中軸黃金交叉：多頭加速點（K=" + String.format("%.2f", today.getKValue()) + "）");
            } else {
                System.out.println("📈 黃金交叉：動能轉強");
                signals.add("📈 黃金交叉：動能轉強（K=" + String.format("%.2f", today.getKValue()) + "）");
            }
        }

        // 死亡交叉（K 線向下穿越 D 線）
        boolean wasKAboveOrEqualD = yesterday.getKValue() >= yesterday.getDValue();
        boolean isKBelowOrEqualD = today.getKValue() <= today.getDValue();
        
        if (wasKAboveOrEqualD && isKBelowOrEqualD) {
            if (today.getKValue() > 70) {
                score -= 12; // 高檔死亡交叉（波段獲利了結）
                System.out.println("⬇️ 高檔死亡交叉（K>70）：波段獲利了結");
                signals.add("⬇️ 高檔死亡交叉：波段獲利了結區（K=" + String.format("%.2f", today.getKValue()) + "）");
            } else {
                System.out.println("📉 死亡交叉：動能轉弱");
                signals.add("📉 死亡交叉：動能轉弱（K=" + String.format("%.2f", today.getKValue()) + "）");
            }
        }

        // ════════════════════════════════════════════════════════════════
        // 3. 簡單低檔背離偵測（對比今日與 5 天前）
        // ════════════════════════════════════════════════════════════════
        KdData pastData = kdHistory.get(len - 5);
        boolean lowDivergence = today.getClosePrice() < pastData.getClosePrice()
                && today.getKValue() > pastData.getKValue()
                && today.getKValue() < 40;

        if (lowDivergence) {
            score += 10;
            System.out.println("🚀 偵測到技術面【低檔背離】！股價創低但動能回升");
            signals.add("🚀 低檔背離：價格創低但動能回升（反轉訊號）");
        }

        // ════════════════════════════════════════════════════════════════
        // 4. 總結
        // ════════════════════════════════════════════════════════════════
        if (signals.isEmpty()) {
            signals.add("📊 KD 訊號中性，持續觀察");
        }

        System.out.println("📊 KD 分析完成，總評分：" + score + " 分");
        return new AnalysisResult(score, signals);
    }

    /**
     * 分析結果包裝類
     */
    public static class AnalysisResult {
        private final int score;
        private final List<String> signals;

        public AnalysisResult(int score, List<String> signals) {
            this.score = score;
            this.signals = signals == null ? Collections.emptyList() : List.copyOf(signals);
        }

        /**
         * 獲取 KD 動能評分
         * @return 評分（-15 ~ +37）
         */
        public int getScore() {
            return score;
        }

        /**
         * 獲取 KD 信號列表
         * @return 信號說明列表
         */
        public List<String> getSignals() {
            return signals;
        }

        /**
         * 獲取動能等級描述
         * @return 動能等級（極強/強/中性/弱/極弱）
         */
        public String getStrengthLevel() {
            if (score >= 20) return "極強";
            if (score >= 10) return "強";
            if (score >= -5) return "中性";
            if (score >= -10) return "弱";
            return "極弱";
        }

        @Override
        public String toString() {
            return "KD 分析結果 { 評分=" + score + ", 動能=" + getStrengthLevel() + ", 信號=" + signals + " }";
        }
    }
}


