package org.gtalent;

import org.gtalent.dto.RadarConfigDto;
import org.springframework.stereotype.Service;

/**
 * 六大軸向量化公式計算器，實現防禦機制與一票否決邏輯。
 *
 * 軸向規格：
 * A. 基本面 (Fundamentals)：三率三升、合約負債、存貨週轉
 * B. 技術面 (Technicals)：KD 黃金交叉、低檔底背離；當沖 >55% 時乘以 0.4
 * C. 波動爆發 (Volatility)：BBW 擠壓、股價突破上軌
 * D. 風控基期 (Risk & Margin)：52週相對位置；位置 >40% 或股價 >400 元時歸 0
 * E. 籌碼結構 (Microstructure)：融資遞減、軋空組態、千張大戶上升
 * F. 消息輿情 (Sentiment)：情緒極性、非對稱回應（NRI）；利多出盡時熔斷至 0
 */
@Service
public class RadarQuantitativeCalculator {

    /**
     * A. 基本面計算
     * 機構級權重：三率三升（毛利率、利益率、淨利率 QoQ+YoY）（+50%）
     *           、合約負債攀升（+30%）
     *           、存貨週轉天數創低（+20%）
     *
     * ETF 自動遞延：ETF 本身無基本面，評分直接為 0
     * 總計最高 100 分（三率三升 + 負債 + 存貨 = 100）
     */
    public int calculateFundamentals(RadarConfigDto.QuantitativeDetails details, boolean isEtf) {
        if (isEtf) {
            // ETF 無單一公司基本面，自動評分為 0
            return 0;
        }

        int score = 0;

        // 三率三升：毛利率 QoQ+YoY、利益率 QoQ+YoY、淨利率 QoQ+YoY 同時上升
        if (details.isTripleRiseActive()) {
            score += 50; // 機構級權重 50%
        }

        // 合約負債攀升：負債結構改善，預示信用槓桿下降
        if (details.getContractLiabilityGrowth() > 0) {
            score += 30; // 機構級權重 30%
        }

        // 存貨週轉天數創低：現金流加速，資金不卡在存貨
        if (details.getInventoryTuroverDays() > 0) {
            score += 20; // 機構級權重 20%
        }

        return Math.min(100, Math.max(0, score));
    }

    /**
     * B. 技術面計算
     * 機構級權重：KD 中軸黃金交叉（+40%）
     *           、KD 低檔底背離（+40%）
     * 總計最高 80 分
     *
     * 降噪聯動：當日沖交易率（Day Trading Rate）>55% 時，表散戶過度投機，
     *          此軸向得分乘以 0.4（衰減 60%），防止被主力假盤帶節奏
     */
    public int calculateTechnicals(RadarConfigDto.QuantitativeDetails details) {
        int score = 0;

        // KD 中軸黃金交叉：K 值向上穿越 D 值，技術面轉強訊號
        score += 40; // 機構級權重 40%

        // KD 低檔底背離：股價創新低但 KD 值止跌回升，機構潛伏訊號
        score += 40; // 機構級權重 40%

        int finalScore = Math.min(80, Math.max(0, score));

        // ========== 降噪聯動：當沖率 >55% 時認定市場散戶過度炒作 ==========
        // 日沖（Day Trading）佔成交量 >55% 表示散戶快進快出，技術面容易被洗盤
        if (details.getDayTradingRate() > 0.55) {
            // 衰減係數 0.4：技術訊號信度下降 60%
            finalScore = (int) Math.round(finalScore * 0.4);
        }

        return finalScore;
    }

    /**
     * C. 波動爆發計算
     * 機構級權重：Bollinger Band 寬度（BBW）擠壓至歷史極低點（+60%）
     *           、股價突破上軌（+40%）
     * 總計最高 100 分
     *
     * 意義：衡量資金「時間成本」是否即將引爆。
     * - BBW 接近 0：上下軌距離最小，波動被極度壓縮，能量聚集到臨界點
     * - 股價突破上軌：壓縮的能量瞬間釋放，風險報酬比最佳
     */
    public int calculateVolatility(RadarConfigDto.QuantitativeDetails details) {
        int score = 0;

        // BBW 擠壓至歷史極低點：波動率降至最低，主力積累能量的訊號
        // bbwCompressionAlert 由數據層根據 BBW 歷史百分位判定
        if (details.isBbwCompressionAlert()) {
            score += 60; // 機構級權重 60%
        }

        // 股價突破上軌：確認能量釋放，瀑布型上漲的開始
        score += 40; // 機構級權重 40%

        return Math.min(100, Math.max(0, score));
    }

    /**
     * D. 風控基期計算
     * 量化公式：priceLocation = (現價 - 52週最低) / (52週最高 - 52週最低)
     *          範圍 0~1（0=52週低、1=52週高）
     *
     * 一票否決制（Veto Mechanism）：
     * - 若 priceLocation > 0.40（即股價在 52 週範圍上方 40％ 以上）
     *   OR 絕對股價 > 400 元
     *   → 此軸向直接歸 0，並觸發其他軸向內縮 50%（聯動防禦）
     *
     * - 否則計分：得分 = (1.0 - priceLocation) * 100
     *   位置越低（越接近 52 週低點），得分越高，風險越低，介入機會越好
     */
    public int calculateRiskMargin(RadarConfigDto.QuantitativeDetails details) {
        // ========== 一票否決檢查 ==========
        if (details.isRiskMarginVeto()) {
            // 位置 >0.40（上方 40%） 或絕對股價 >400 元
            // 表示股價已在高檔，風險偏大，直接歸零
            return 0;
        }

        // ========== 正常計分 ==========
        // priceLocation = (現價 - 52週低) / (52週高 - 52週低)
        // 0 = 52週低點（最安全，風險最低）
        // 1 = 52週高點（最危險，風險最高）
        // 得分邏輯：位置越低，得分越高
        int score = (int) Math.round((1.0 - details.getPriceLocation()) * 100);

        return Math.max(0, Math.min(100, score));
    }

    /**
     * E. 籌碼結構計算
     * 機構級權重：融資連續遞減（+40%）
     *           、軋空組態（+40%）：資券比 >30% 且融券增加
     *           、千張大戶持股比上升（+20%）
     * 總計最高 100 分
     *
     * 組態判定邏輯：
     * 1. 融資遞減：連續 5 日（含）以上融資餘額下降，表主力減碼或散戶出逃
     * 2. 軋空組態：資券比（融資/融券）>30% 且融券較前日增加
     *    → 表融券部位被迫回補，上升空間受限但反彈迫切
     * 3. 千張大戶：大戶持股比（千張以上）較前期上升
     *    → 表機構籌碼積累，買方力道強勁
     */
    public int calculateMicrostructure(RadarConfigDto.QuantitativeDetails details) {
        int score = 0;

        // 融資連續遞減 5 日以上：主力主要集中在融券軋空或現股持有，減少融資意願
        if (details.getMarginDecreaseDays() >= 5) {
            score += 40; // 機構級權重 40%
        }

        // 軋空組態：資券比 >30% 且融券增加
        // 資券比 = 融資餘額 / 融券餘額
        // >30% 表示融資相對融券過多，空方需要回補，市場傾斜
        if (details.isMarginShortSqueeze()) {
            score += 40; // 機構級權重 40%
        }

        // 千張大戶持股比上升：機構籌碼集中度提高，買方主導市場
        if (details.getLargeHolderRatio() > 0) {
            score += 20; // 機構級權重 20%
        }

        return Math.min(100, Math.max(0, score));
    }

    /**
     * F. 消息輿情計算
     * 核心指標：新聞情緒極性（-1~1） + 非對稱回應指數（NRI, -1~1）
     *
     * 計分邏輯：
     * 1. 情緒極性基礎得分 = ((sentiment + 1.0) / 2.0) * 100
     *    範圍 0~100（-1=全負 → 0 分，0=中立 → 50 分，+1=全正 → 100 分）
     *
     * 2. NRI（非對稱回應）調整：
     *    - NRI > 0.5：強正向，新聞與股價互動良好 → 正向回應 * 1.2
     *    - NRI (-0.3~0.5]：中立 → 不調整
     *    - NRI <= -0.3：弱負向，情緒被市場現實打臉 → 減分 * 0.6
     *
     * 熔斷機制（利多出盡警告）：
     * - 高情緒（sentiment > 0.5）且低 NRI（NRI < -0.3）
     * - 表示新聞面大漲但股價表現不給力 → 主力出貨訊號
     * - 此軸向直接歸 0，並前端觸發紅色警告
     */
    public int calculateSentiment(RadarConfigDto.QuantitativeDetails details) {
        // ========== 熔斷檢查：利多出盡 ==========
        if (details.isSentimentMeltdown()) {
            // 高情緒 + 低 NRI = 利多出盡，主力已出貨
            // 此軸向歸零，前端顯示警告 ⚠️
            return 0;
        }

        // ========== 正常計分 ==========
        // 情緒極性轉換：-1~1 → 0~100
        double sentiment = details.getNewsSentiment();
        int baseScore = (int) Math.round(((sentiment + 1.0) / 2.0) * 100);

        // ========== NRI 非對稱回應調整 ==========
        double nri = details.getNri();

        if (nri > 0.5) {
            // 強正向互動：新聞利多與股價上漲腳步一致
            // 市場反應良好，額外加分（上限 100）
            baseScore = Math.min(100, (int) Math.round(baseScore * 1.2));
        } else if (nri < -0.3) {
            // 弱負向互動：新聞利多但股價漲不動
            // 市場反應冷淡，減分（防止被假利多帶節奏）
            baseScore = Math.max(0, (int) Math.round(baseScore * 0.6));
        }
        // else: -0.3 <= nri <= 0.5，中立區間，不調整

        return Math.max(0, Math.min(100, baseScore));
    }

    /**
     * 綜合評分：六軸均衡加權計算（每軸 1/6 權重）
     *
     * 防禦機制優先級（按嚴重程度排序）：
     *
     * ========== 一級防禦：D 軸一票否決（風控基期）==========
     * 若 riskMargin == 0（位置 >0.40 或股價 >400 元）
     * → 所有其他軸向內縮 50%
     * → 表示股價已入高檔，即使其他信號強勁也應審慎
     * → 綜合得分會顯著下降
     *
     * ========== 二級防禦：F 軸熔斷（消息輿情）==========
     * 若 sentiment == 0 且 isSentimentMeltdown == true（利多出盡逃頂）
     * → 消息面已歸零，前端觸發 ⚠️ 紅色警告
     * → 表示利多已消化，需要強大的技術或籌碼支撐
     *
     * ========== 三級防禦：B 軸降噪聯動（技術面）==========
     * 若 dayTradingRate > 0.55（散戶過度投機）
     * → B 軸已自動衰減至 40% 以下
     * → 表示技術面可信度不足
     *
     * 最終綜合 = (A + B + C + D + E + F) / 6
     */
    public int calculateCompositeScore(RadarConfigDto.QuantitativeDetails details,
                                       int fundamentals,
                                       int technicals,
                                       int volatility,
                                       int riskMargin,
                                       int microstructure,
                                       int sentiment) {
        // ========== 一級防禦：D 軸一票否決，其他軸內縮 50% ==========
        if (riskMargin == 0) {
            // D 軸已歸零，表示股價在高檔，風險已然顯現
            // 全面內縮整體雷達，所有其他軸向信號可信度下降
            fundamentals = Math.max(0, (int) Math.round(fundamentals * 0.5));
            technicals = Math.max(0, (int) Math.round(technicals * 0.5));
            volatility = Math.max(0, (int) Math.round(volatility * 0.5));
            microstructure = Math.max(0, (int) Math.round(microstructure * 0.5));
            sentiment = Math.max(0, (int) Math.round(sentiment * 0.5));
        }

        // ========== 二級防禦：F 軸熔斷時前端觸發警告 ==========
        if (details.isSentimentMeltdown()) {
            // 消息輿情熔斷，sentiment 已歸 0，此處確保一致
            sentiment = 0;
        }

        // ========== 計算均衡綜合分數 ==========
        // 六軸等權加權（每軸 1/6）
        int composite = (fundamentals + technicals + volatility + Math.max(0, riskMargin)
                        + microstructure + sentiment) / 6;

        return Math.max(0, Math.min(100, composite));
    }

    /**
     * 根據量化元資料填充防禦機制標記
     * 
     * 三層防禦機制設置流程：
     * 1. D 軸一票否決 (Risk Margin Veto)
     * 2. B 軸降噪聯動 (Technical Noise Factor)
     * 3. F 軸熔斷 (Sentiment Meltdown)
     */
    public void applyDefensiveMechanisms(RadarConfigDto.QuantitativeDetails details) {
        
        // ========== 一票否決：D 軸風控基期 ==========
        // 檢查條件：priceLocation > 0.40（位置上方 40%以上） 或 currentPrice > 400 元
        if (details.getPriceLocation() > 0.40 || details.getCurrentPrice() > 400) {
            details.setRiskMarginVeto(true);
            // → D 軸直接歸 0，其他軸內縮 50%
        }

        // ========== 降噪聯動：B 軸技術面 ==========
        // 檢查當沖率是否超過 55%（散戶過度投機）
        if (details.getDayTradingRate() > 0.55) {
            // 日沖比例過高，市場被散戶主導，技術訊號容易被洗盤
            details.setTechnicalNoiseFactor(0.4);
            // → B 軸將被衰減至 40% 
        } else {
            // 當沖率正常，技術訊號可信度高
            details.setTechnicalNoiseFactor(1.0);
        }

        // ========== 軋空組態：E 軸籌碼結構 ==========
        // 檢查條件：資券比（融資/融券）> 30% 且融券餘額增加
        if (details.getMarginShortRatio() > 30.0) {
            details.setMarginShortSqueeze(true);
            // → E 軸籌碼結構得分額外加 40%，表示軋空組態成立
        }

        // ========== 熔斷機制：F 軸消息輿情 ==========
        // 檢查條件：高情緒（sentiment > 0.5）+ 低 NRI（NRI < -0.3）
        // 表示利多新聞但股價未跟上，是主力出貨訊號
        if (details.getNewsSentiment() > 0.5 && details.getNri() < -0.3) {
            details.setSentimentMeltdown(true);
            // → F 軸直接歸 0，前端觸發 ⚠️ 紅色警告「利多出盡」
        }
    }
}

