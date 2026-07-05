package org.gtalent;

public class RadarScoreResult {
    public int trend;        // 趨勢
    public int momentum;     // 動能
    public int rawMomentum;  // 原始動能（降噪前）
    public int money;        // 籌碼
    public int bigHolderScore; // 千張大戶籌碼動態分數
    public double dayTradingRate; // 當沖佔比（0~1）
    public double momentumTrustWeight; // 當沖降噪後的動能信任權重（四段制 calculateConfidenceWeight）
    public double confidenceWeight;    // 四段制信任度：>=60%→0.40 / >=45%→0.65 / >=30%→0.85 / 其他→1.0
    public boolean noiseCompensationApplied; // 高噪音補償是否觸發（當沖率>=60%，money 分數×1.2）
    public int volatility;   // 波動
    public int context;      // 環境
    public int fundamental;  // 營收
    public int news;         // 消息面
    public boolean etfMode;  // ETF 模式（排除營收評分）
    public String assetType;  // 商品類型
    public String market;     // 市場別 TWSE/OTC
    public String strategyMode; // 當前資產策略模式
    public String strategyDescription; // 策略模式說明
    public int totalScore;   // 加權綜合評分
    public String conclusion; // 綜合結論
    public String label;      // 標的類型標籤
    public MoneySource moneySource;          // 籌碼分數來源細節
    public FundamentalDetail fundamentalDetail; // 基本面細部分析（ETF 模式為 null）
    public MarginDetail marginDetail;        // 融資融券風控細節（FinMind 數據不足時為 null）
    public NewsDetail newsDetail;            // 消息面細節（新聞不足時仍提供中性分）
    public RadarTacticalAnalyzer.TacticalAnalysisResult tacticalAnalysis; // 戰術分析結果（根據六軸形狀判定操盤策略）

    public RadarScoreResult() {
    }

    /**
     * 基本面精細化分析細節（供前端 AI 診斷報告使用）
     */
    public static class FundamentalDetail {
        /** 精細化基本面原始分數（0-20，與 AdvancedFundamentalService 對齊） */
        public int rawScore;
        /** 三率三升：毛利率、營業利益率、淨利率同步 QoQ 上升 */
        public boolean tripleRiseActive;
        /** 月營收年增率（YoY）為正成長 */
        public boolean revenueGrowing;
        /** 毛利率 QoQ 下滑（即使月營收成長，也可能是「降價求售型假成長」） */
        public boolean marginDropping;
        /** 存貨週轉天數 QoQ 下降（去化加速，庫存健康） */
        public boolean inventoryHealthy;
        /** 合約負債 QoQ 上升（未來入帳訂單能見度提升） */
        public boolean contractLiabilityGrowing;
        /** 是否有完整的季報資料（無資料時前端顯示降級提示） */
        public boolean hasQuarterData;
    }

    public static class MoneySource {        public int trustDays;
        public double lockRatioPct;
        public long latestNetBuy;
        public double avgNetBuy5;
        public int institutionalMoneyScore;
        public int bigHolderNormalizedScore;
        public double institutionalWeight;
        public double bigHolderWeight;
        public double moneyPercentile;
        public int moneyPercentileSampleSize;
        public boolean noiseCompensationApplied; // 高噪音補償觸發（money 分數 × 1.2）
        public double noiseConfidenceWeight;      // 觸發當下的信任度權重
    }

    /**
     * 融資融券風控細節（供前端 AI 診斷報告使用）
     */
    public static class MarginDetail {
        /** MarginAnalysisService 計算出的信用交易評分（融資減+10 / 融資暴增-15 / 軋空+10） */
        public int marginScore;
        /** 今日融資餘額 (張) */
        public long marginPurchaseLimit;
        /** 今日融券餘額 (張) */
        public long shortSaleLimit;
        /** 資券比 = 融券/融資（%），用於前端顯示 */
        public double shortToMarginRatioPct;
        /** 5 日前融資餘額（用於判定週趨勢） */
        public long marginLimitP5;
        /** 融資週趨勢: "DECREASING"(健康) | "SURGING"(危險) | "STABLE"(穩定) */
        public String marginWeekTrend;
        /** 是否有軋空潛力 */
        public boolean shortSqueezeAlert;
        /** 是否有散戶套牢警示 */
        public boolean retailTrapWarning;
    }

    /**
     * 消息面細節（供前端 tooltip 使用）
     */
    public static class NewsDetail {
        /** NewsReactionService 原始分數（預設區間 -15~15） */
        public int rawScore;
        /** 當日平均新聞情緒（-1~1） */
        public double sentiment;
        /** 當日漲跌幅（例如 0.05 = +5%） */
        public double todayReturn;
        /** 當沖率（0~1） */
        public double dayTradingRate;
        /** 今日有效新聞筆數 */
        public int newsCount;
    }
}
