package org.gtalent;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.logging.Logger;

/**
 * 信用交易風控與算分引擎
 *
 * <p>提供兩大機構級核心診斷：
 * <ol>
 *   <li><b>籌碼沉澱（融資減）</b>：融資持續下降代表散戶洗盤完畢，籌碼乾淨，主力更容易拉抬。</li>
 *   <li><b>潛在軋空（資券比暴增）</b>：融券餘額大且持續增加，一旦行情反轉，空頭被迫回補，
 *       引發「軋空行情」，可帶動股價短期急漲。</li>
 * </ol>
 *
 * <p><b>評分範圍</b>：無上限，但實際分佈通常在 -15 ~ +20 之間。
 * <ul>
 *   <li>+10：融資週減（籌碼健康，散戶洗出）</li>
 *   <li>-15：融資週增超 15%（警告：散戶套牢盤湧入）</li>
 *   <li>+10：資券比 >= 30% 且融券繼續增加（軋空潛力）</li>
 * </ul>
 *
 * @author StockPredictor Team
 * @version 1.0
 */
@Service
public class MarginAnalysisService {

    private static final Logger logger = Logger.getLogger(MarginAnalysisService.class.getName());

    /** 融資週增警戒閾值（超過此倍率視為散戶瘋狂湧入） */
    private static final double MARGIN_SURGE_THRESHOLD = 1.15;

    /** 資券比軋空觸發閾值（融券/融資 >= 30% 視為有軋空潛力） */
    private static final double SHORT_TO_MARGIN_TRIGGER = 0.30;

    // ════════════════════════════════════════════════════════════
    //  公開 API
    // ════════════════════════════════════════════════════════════

    /**
     * 根據融資融券歷史計算信用交易評分。
     *
     * <p><b>前置條件</b>：至少需要 5 筆歷史資料（含今日）才能計算。
     *
     * <p><b>評分邏輯</b>：
     * <table border="1">
     *   <tr><th>指標</th><th>條件</th><th>分數</th></tr>
     *   <tr><td>融資週減</td><td>今日融資 &lt; 5日前融資</td><td>+10 (籌碼健康)</td></tr>
     *   <tr><td>融資暴增警告</td><td>今日融資 &gt; 5日前融資 × 1.15</td><td>-15 (散戶套牢)</td></tr>
     *   <tr><td>資券比軋空</td><td>融券/融資 &ge; 30% 且今日融券增加</td><td>+10 (軋空潛力)</td></tr>
     * </table>
     *
     * @param marginHistory 融資融券歷史資料（按日期升序排列，最新在末尾）
     * @return 信用交易評分，資料不足時回傳 0
     */
    public int calculateMarginScore(List<FinMindMarginData> marginHistory) {
        if (marginHistory == null || marginHistory.size() < 5) {
            logger.fine("⚠️  融資融券歷史資料不足（需 5 筆），回傳 0 分");
            return 0;
        }

        int score = 0;
        int len = marginHistory.size();

        FinMindMarginData today = marginHistory.get(len - 1);   // 今日
        FinMindMarginData p1    = marginHistory.get(len - 2);   // 前一日
        FinMindMarginData p5    = marginHistory.get(len - 5);   // 5 日前（約一週）

        logger.fine(String.format("⚙️  信用評分計算: %s | 今日融資=%d 今日融券=%d 5日前融資=%d",
                today.getStock_id(),
                today.getMarginPurchaseLimit(),
                today.getShortSaleLimit(),
                p5.getMarginPurchaseLimit()));

        // ════════════════════════════════════════════════════════
        // 指標 1：融資減 vs 融資暴增 → 籌碼健康度
        // ════════════════════════════════════════════════════════
        score += evaluateMarginTrend(today, p5);

        // ════════════════════════════════════════════════════════
        // 指標 2：資券比 → 軋空可能性
        // ════════════════════════════════════════════════════════
        score += evaluateShortSqueezeRisk(today, p1);

        logger.fine(String.format("📊 信用評分結果: %s → %d 分", today.getStock_id(), score));

        return score;
    }

    // ════════════════════════════════════════════════════════════
    //  私有輔助方法
    // ════════════════════════════════════════════════════════════

    /**
     * 評估融資趨勢：籌碼沉澱 vs 散戶瘋狂湧入。
     *
     * @param today 今日資料
     * @param p5    5 日前資料
     * @return 分數（+10 健康 | -15 警告 | 0 普通）
     */
    private int evaluateMarginTrend(FinMindMarginData today, FinMindMarginData p5) {
        long todayMargin = today.getMarginPurchaseLimit();
        long p5Margin    = p5.getMarginPurchaseLimit();

        if (todayMargin < p5Margin) {
            // ✅ 融資週減：散戶正在割肉認賠，籌碼洗乾淨，主力拉抬更輕鬆
            double reduceRate = (double)(p5Margin - todayMargin) / p5Margin * 100;
            logger.info(String.format("✅ 籌碼沉澱：融資週減 %.1f%%（今=%d, 5日前=%d）→ +10",
                    reduceRate, todayMargin, p5Margin));
            return 10;

        } else if (p5Margin > 0 && todayMargin > p5Margin * MARGIN_SURGE_THRESHOLD) {
            // ⚠️ 融資週增超 15%：散戶瘋狂融資進場，屬於散戶套牢盤，強行扣分
            double surgeRate = (double)(todayMargin - p5Margin) / p5Margin * 100;
            logger.warning(String.format("⚠️  融資暴增警告：週增 %.1f%%（今=%d, 5日前=%d）→ -15",
                    surgeRate, todayMargin, p5Margin));
            return -15;

        } else {
            logger.fine(String.format("◯  融資正常波動（今=%d, 5日前=%d）→ 0", todayMargin, p5Margin));
            return 0;
        }
    }

    /**
     * 評估資券比與軋空風險。
     *
     * <p><b>軋空觸發條件</b>：
     * <ol>
     *   <li>資券比 (ShortSaleLimit / MarginPurchaseLimit) >= 0.30</li>
     *   <li>今日融券餘額 > 前一日融券餘額（融券繼續增加）</li>
     * </ol>
     *
     * @param today 今日資料
     * @param p1    前一日資料
     * @return 分數（+10 軋空潛力 | 0 無信號）
     */
    private int evaluateShortSqueezeRisk(FinMindMarginData today, FinMindMarginData p1) {
        long todayMargin = today.getMarginPurchaseLimit();
        long todayShort  = today.getShortSaleLimit();
        long p1Short     = p1.getShortSaleLimit();

        if (todayMargin <= 0) {
            logger.fine("⚠️  融資為 0，無法計算資券比");
            return 0;
        }

        // 資券比 = 融券餘額 / 融資餘額
        double shortToMarginRatio = (double) todayShort / todayMargin;

        if (shortToMarginRatio >= SHORT_TO_MARGIN_TRIGGER && todayShort > p1Short) {
            // ✅ 資券比超過 30% 且融券繼續增加：市場空頭不死，極易引發「軋空行情」
            logger.info(String.format("✅ 軋空潛力：資券比 %.1f%%（融券=%d, 融資=%d），融券續增 →  +10",
                    shortToMarginRatio * 100, todayShort, todayMargin));
            return 10;

        } else {
            logger.fine(String.format("◯  資券比 %.1f%%，未達軋空觸發條件 → 0",
                    shortToMarginRatio * 100));
            return 0;
        }
    }
}

