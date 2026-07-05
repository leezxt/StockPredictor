package org.gtalent;

/**
 * 智能股市分析系統：全型態戰術矩陣組態
 *
 * 六大極端型態（依優先順序判斷）：
 * 1. ETF_DEFENSIVE    (⚡ 右下錨定型) - ETF模式優先路由
 * 2. ZOMBIE_STOCK     (🕸️ 內縮乾癟型) - 全軸≤30，一票否決
 * 3. GOLDEN_COMPLETE  (👑 全面飽滿型) - 全軸≥75，黃金完全體
 * 4. RETAIL_TRAP      (⚠️ 上重下輕型) - 利多出盡，強制熔斷
 * 5. LURKING_PEARL    (🟢 左下擴張型) - 底部珍珠，分批潛伏
 * 6. SHORT_TERM_MONSTER(🔴 右側尖刺型) - 妖股短打，嚴格停損
 * 7. BALANCED         (⚖️ 均衡中性)   - 標準樣本
 * 8. UNKNOWN          (❓ 未知組態)   - 待分類
 */
public class RadarTacticalAnalyzer {

    /**
     * 額外上下文資料（供六型態細部判斷）
     */
    public static class TacticalContext {
        public boolean etfMode;
        public double  dayTradingRate;         // 0~1；≥0.60 → 散戶極度投機
        public int     newsCount;              // 當日有效新聞數；= 0 → 殭屍股
        public boolean noiseCompensationApplied;
        public boolean shortSqueezeAlert;      // 資券比 >30%，軋空警示
        public boolean retailTrapWarning;      // 融資爆增警告
        public double  priceLocation;          // 52週相對位置 0~1（0=低點）

        public TacticalContext() {}
    }

    /**
     * 戰術型態定義分類。
     *
     * 用於把具體 TacticalType 收斂成前端與交易規則更容易消費的高階語意：
     * 機會、風險、防禦、中性、未知。
     */
    public enum TacticalDefinitionType {
        OPPORTUNITY("機會型", "可觀察進場或加碼的正向結構"),
        SPECULATIVE("投機型", "只適合短線交易的高波動結構"),
        RISK_VETO("風險否決型", "系統應禁止買進或優先出場的高風險結構"),
        DEFENSIVE("防禦型", "偏資產配置或逢低承接的防守結構"),
        NEUTRAL("中性型", "方向尚未明確，需等待催化訊號"),
        UNKNOWN("未知型", "資料不足或型態尚未分類");

        private final String chineseName;
        private final String description;

        TacticalDefinitionType(String chineseName, String description) {
            this.chineseName = chineseName;
            this.description = description;
        }

        public String getChineseName() { return chineseName; }
        public String getDescription() { return description; }
    }

    /**
     * 雷達圖戰術類型枚舉
     */
    public enum TacticalType {
        GOLDEN_COMPLETE  (TacticalDefinitionType.OPPORTUNITY, "全面飽滿型", "黃金六角完全體",
                "萬中選一的波段主升段飆股，六軸均≥75，機構級最大下注組態。"),
        LURKING_PEARL    (TacticalDefinitionType.OPPORTUNITY, "左下擴張型", "籌碼沉澱底部珍珠",
                "底部「利空不跌」珍珠股，基本面與籌碼備齊，靜待技術點火。"),
        SHORT_TERM_MONSTER(TacticalDefinitionType.SPECULATIVE, "右側尖刺型", "題材投機妖股",
                "純散戶與熱錢炒作，基本面空心，嚴格移動停損，禁止長線抱股。"),
        RETAIL_TRAP      (TacticalDefinitionType.RISK_VETO, "上重下輕型", "利多出盡陷阱",
                "主力利用利多新聞出貨，NRI轉嚴重負值，系統強制熔斷剝奪買進權限。"),
        ETF_DEFENSIVE    (TacticalDefinitionType.DEFENSIVE, "右下錨定型", "ETF超賣防禦矩陣",
                "ETF折價超賣，下檔由淨值與成分股防禦力鎖定，越跌越買的防守型部位。"),
        ZOMBIE_STOCK     (TacticalDefinitionType.RISK_VETO, "內縮乾癟型", "無量邊緣冷門股",
                "殭屍股，六軸全縮≤30，資金時間成本極高，一票否決直接剔除不予配置。"),
        BALANCED         (TacticalDefinitionType.NEUTRAL, "均衡中性",   "標準樣本",
                "各維度均衡分佈，未構成明確交越交集，等待突破訊號。"),
        UNKNOWN          (TacticalDefinitionType.UNKNOWN, "未知組態",   "待分類",
                "分佈無明確特徵，可能為過度雜訊或資料不足。");

        private final TacticalDefinitionType definitionType;
        private final String chineseName;
        private final String shortName;
        private final String description;

        TacticalType(TacticalDefinitionType definitionType, String chineseName,
                     String shortName, String description) {
            this.definitionType = definitionType;
            this.chineseName    = chineseName;
            this.shortName      = shortName;
            this.description    = description;
        }

        public TacticalDefinitionType getDefinitionType() { return definitionType; }
        public String getChineseName() { return chineseName; }
        public String getShortName()   { return shortName; }
        public String getDescription() { return description; }
    }

    /**
     * 戰術分析結果 DTO
     */
    public static class TacticalAnalysisResult {
        public TacticalType type;
        public TacticalDefinitionType definitionType;
        public String definitionName;
        public String definitionDescription;
        public String tacticName;
        public String tacticEmoji;
        public String description;
        public String operationAdvice;       // 操盤決策
        public String riskWarning;           // 風險警示
        public String entrySignal;           // 進場訊號
        public String exitSignal;            // 出場訊號
        public int    confidenceScore;       // 置信度 0~100
        public boolean perfectMatch;         // 👑 GOLDEN_COMPLETE 時為 true → 前端點亮完美匹配勳章
        public boolean isBuyVetoed;          // ⚠️ RETAIL_TRAP / ZOMBIE_STOCK → 前端顯示禁止買入
        public String  tradeInstruction;     // 後端交易指令摘要（前端顯示）

        public TacticalAnalysisResult() {}

        public TacticalType getType()               { return type; }
        public void setType(TacticalType t) {
            this.type = t;
            applyDefinitionType(t);
        }
        public TacticalDefinitionType getDefinitionType() { return definitionType; }
        public void setDefinitionType(TacticalDefinitionType t) {
            this.definitionType = t;
            this.definitionName = t == null ? null : t.getChineseName();
            this.definitionDescription = t == null ? null : t.getDescription();
        }
        public String getDefinitionName()           { return definitionName; }
        public void setDefinitionName(String s)     { this.definitionName = s; }
        public String getDefinitionDescription()    { return definitionDescription; }
        public void setDefinitionDescription(String s) { this.definitionDescription = s; }
        public String getTacticName()               { return tacticName; }
        public void setTacticName(String s)         { this.tacticName = s; }
        public String getTacticEmoji()              { return tacticEmoji; }
        public void setTacticEmoji(String s)        { this.tacticEmoji = s; }
        public String getDescription()              { return description; }
        public void setDescription(String s)        { this.description = s; }
        public String getOperationAdvice()          { return operationAdvice; }
        public void setOperationAdvice(String s)    { this.operationAdvice = s; }
        public String getRiskWarning()              { return riskWarning; }
        public void setRiskWarning(String s)        { this.riskWarning = s; }
        public String getEntrySignal()              { return entrySignal; }
        public void setEntrySignal(String s)        { this.entrySignal = s; }
        public String getExitSignal()               { return exitSignal; }
        public void setExitSignal(String s)         { this.exitSignal = s; }
        public int  getConfidenceScore()            { return confidenceScore; }
        public void setConfidenceScore(int n)       { this.confidenceScore = n; }
        public boolean isPerfectMatch()             { return perfectMatch; }
        public void setPerfectMatch(boolean b)      { this.perfectMatch = b; }
        public boolean isIsBuyVetoed()              { return isBuyVetoed; }
        public void setIsBuyVetoed(boolean b)       { this.isBuyVetoed = b; }
        public String getTradeInstruction()         { return tradeInstruction; }
        public void setTradeInstruction(String s)   { this.tradeInstruction = s; }

        public void applyDefinitionType(TacticalType t) {
            setDefinitionType(t == null ? null : t.getDefinitionType());
        }
    }

    // ────────────────────────────────────────────────────────────
    //  Public API
    // ────────────────────────────────────────────────────────────

    /** 向後相容的舊介面（無額外 context） */
    public TacticalAnalysisResult analyzeTactical(int fundamentals, int technicals,
                                                   int volatility,  int riskMargin,
                                                   int microstructure, int sentiment) {
        return analyzeTactical(fundamentals, technicals, volatility,
                riskMargin, microstructure, sentiment, new TacticalContext());
    }

    /**
     * 全型態矩陣核心分析（含 TacticalContext 額外欄位）
     * 判斷優先順序：ETF → 殭屍 → 黃金完全體 → 利多陷阱 → 底部珍珠 → 妖股 → 均衡 → 未知
     */
    public TacticalAnalysisResult analyzeTactical(int fundamentals, int technicals,
                                                   int volatility,  int riskMargin,
                                                   int microstructure, int sentiment,
                                                   TacticalContext ctx) {
        // ① ETF 特殊路由（最優先）
        if (ctx.etfMode) {
            return buildEtfDefensive(fundamentals, technicals, volatility, riskMargin, microstructure, sentiment, ctx);
        }

        // ② 殭屍股一票否決（六軸全部 ≤ 30）
        if (fundamentals <= 30 && technicals <= 30 && volatility <= 30
                && riskMargin <= 30 && microstructure <= 30 && sentiment <= 30) {
            return buildZombieStock(ctx);
        }

        // ③ 黃金完全體（六軸全部 ≥ 75）
        if (fundamentals >= 75 && technicals >= 75 && volatility >= 75
                && riskMargin >= 75 && microstructure >= 75 && sentiment >= 75) {
            return buildGoldenComplete();
        }

        // ④ 利多出盡陷阱（上重下輕：基本面＋技術面高，風控＋籌碼＋消息低）
        if (fundamentals >= 65 && technicals >= 65
                && riskMargin <= 40 && microstructure <= 40 && sentiment <= 35) {
            return buildRetailTrap(ctx);
        }

        // ⑤ 底部珍珠（左下擴張：基本面＋籌碼＋消息＋風控高，技術＋波動低）
        if (fundamentals > 65 && microstructure > 65 && sentiment > 55 && riskMargin > 55
                && technicals <= 40 && volatility <= 40) {
            return buildLurkingPearl(ctx);
        }

        // ⑥ 題材妖股（右側尖刺：技術＋波動＋消息高，基本面＋風控低）
        if (technicals >= 75 && volatility >= 75 && sentiment >= 75
                && fundamentals <= 40 && riskMargin <= 40) {
            return buildShortTermMonster(ctx);
        }

        // ⑦ 均衡中性
        if (Math.abs(fundamentals - technicals) < 20 && Math.abs(microstructure - sentiment) < 20) {
            return buildBalanced();
        }

        // ⑧ 未知組態
        return buildUnknown();
    }

    // ────────────────────────────────────────────────────────────
    //  Pattern Builders（各型態建置器）
    // ────────────────────────────────────────────────────────────

    /** 1. 👑 全面飽滿型：黃金六角完全體 */
    private TacticalAnalysisResult buildGoldenComplete() {
        TacticalAnalysisResult r = new TacticalAnalysisResult();
        r.setType(TacticalType.GOLDEN_COMPLETE);
        r.tacticName      = "黃金六角完全體";
        r.tacticEmoji     = "👑";
        r.description     = TacticalType.GOLDEN_COMPLETE.getDescription();
        r.perfectMatch    = true;
        r.isBuyVetoed     = false;
        r.operationAdvice = "啟動凱利公式最大下注組態。六軸均≥75，萬中選一的波段主升段飆股。"
                + "財報觸發三率三升、投信持續鎖碼、資券比高具軋空燃料、NRI呈「利多真突破」。"
                + "沿著波段軌道（20 MA）死死抱牢利潤，直到籌碼結構或技術動能出現結構性損壞。";
        r.riskWarning     = "高置信高回報，但一旦任一軸跌破 75 應即時降低倉位。後續財報若未能延續三率三升，須提前設停利。";
        r.entrySignal     = "收盤突破 20MA + KD 金叉確認 + 量能溫和放大 + 三率三升財報發布後次日開盤";
        r.exitSignal      = "52週位置 > 40% 或股價 > 400 元，先行獲利 50%；跌破 20MA 月線立即清倉。";
        r.tradeInstruction = "✅ 完美匹配 / ✅ 完美訊號：允許最大配置比例，啟動凱利公式上限。";
        r.confidenceScore = 95;
        return r;
    }

    /** 2. 🟢 左下擴張型：籌碼沉澱底部珍珠 */
    private TacticalAnalysisResult buildLurkingPearl(TacticalContext ctx) {
        TacticalAnalysisResult r = new TacticalAnalysisResult();
        r.setType(TacticalType.LURKING_PEARL);
        r.tacticName      = "籌碼沉澱底部珍珠";
        r.tacticEmoji     = "🟢";
        r.description     = TacticalType.LURKING_PEARL.getDescription();
        r.perfectMatch    = false;
        r.isBuyVetoed     = false;
        r.operationAdvice = "左側交易戰略潛伏標的。52週相對位置極低（pos≤0.25），市場關注度極低，"
                + "散戶割肉離場（融資遞減、融券沉澱），技術面長期無量打底。"
                + "限制單筆下單上限，轉為「分批逢低吸納」佈局。"
                + "鎖定標的靜待 BBW 從極度擠壓中釋放、右側技術動能放量點火訊號。";
        r.riskWarning     = "底部磨底可能持續數月，不可急進。需守住支撐位；若跌破關鍵均線則鎖損出場，避免越陷越深。";
        r.entrySignal     = "KD 底背離確認 + 陽線收高 + 投信連買≥5天 + 單日量能異常放大 + BBW 開口擴張";
        r.exitSignal      = "技術面突破 20MA 後回踩不破再加碼；任何利空負面新聞觸發立即出場。";
        r.tradeInstruction = "⬅️ 左側潛伏：分批建倉，單筆上限 30%；資金比重控制，靜待技術引爆。"
                + (ctx.shortSqueezeAlert ? " ⚡ 軋空警示啟動，反彈爆發力更強。" : "");
        r.confidenceScore = 85;
        return r;
    }

    /** 3. 🔴 右側尖刺型：題材投機妖股 */
    private TacticalAnalysisResult buildShortTermMonster(TacticalContext ctx) {
        TacticalAnalysisResult r = new TacticalAnalysisResult();
        r.setType(TacticalType.SHORT_TERM_MONSTER);
        r.tacticName      = "題材投機妖股";
        r.tacticEmoji     = "🔴";
        r.description     = TacticalType.SHORT_TERM_MONSTER.getDescription();
        r.perfectMatch    = false;
        r.isBuyVetoed     = false;
        boolean extremeSpec = ctx.dayTradingRate >= 0.60;
        r.operationAdvice = "右側交易極短線投機。媒體瘋狂報導（newsCount爆量）、波動度觸發🌪️擴張。"
                + "基本面空心，當沖率"
                + (extremeSpec ? "≥60%（散戶極度狂熱），" : ">55%，")
                + "禁止長線抱股與向下攤平。"
                + "後端自動壓低凱利比例，嚴防假突破。交易策略限定「沿 5MA 移動停損」。";
        r.riskWarning     = "⚠️ 散戶套牢燈號已亮：融資爆增、基期 >40%。"
                + "一旦 KD 出現高檔死亡交叉或投信反手倒貨，立即無條件清倉出局，嚴禁攤平。";
        r.entrySignal     = "短線啟動 + 成交量突增 + KD 急速拉升至 80 以上（注意不可在高位追漲）";
        r.exitSignal      = "KD 死叉確認 或 跌破前 5 日低點；獲利超過目標立即鎖定 50% 出場；絕不隔夜留倉。";
        r.tradeInstruction = "🚨 凱利公式強制降比例：最大配置 ≤ 20%。交易策略限 5MA 移動停損，嚴禁長抱。";
        r.confidenceScore = 75;
        return r;
    }

    /** 4. ⚠️ 上重下輕型：利多出盡陷阱 */
    private TacticalAnalysisResult buildRetailTrap(TacticalContext ctx) {
        TacticalAnalysisResult r = new TacticalAnalysisResult();
        r.setType(TacticalType.RETAIL_TRAP);
        r.tacticName      = "利多出盡陷阱";
        r.tacticEmoji     = "⚠️";
        r.description     = TacticalType.RETAIL_TRAP.getDescription();
        r.perfectMatch    = false;
        r.isBuyVetoed     = true;          // 強制禁止買入
        r.operationAdvice = "系統強制熔斷，剝奪買進權限！"
                + "通常出現在績優股高檔：公司發布極佳利多，技術面維持多頭排列，"
                + "但散戶融資大舉湧入後，主力大戶反手倒貨，股價開高走低收黑K。"
                + "現有持股者立即觸發波段止盈（停利）指令。";
        r.riskWarning     = "⚠️ 歸零警告！偵測到消息面利多出盡，主力利用新聞出貨。"
                + "NRI 指數轉嚴重負值，籌碼面已顯示法人調節。"
                + (ctx.retailTrapWarning ? " 融資爆增警告確認，散戶接盤風險極高！" : "");
        r.entrySignal     = "🚫 禁止新進場。系統已熔斷本股買進權限。";
        r.exitSignal      = "現有持股：立即執行波段停利，清除所有倉位。"
                + "信用交易模組：此標的列入融券放空優先候選名單。";
        r.tradeInstruction = "🔴 強制停利出場指令：全數賣出 + 考慮融券放空 + 停利後列入黑名單觀察。";
        r.confidenceScore = 88;
        return r;
    }

    /** 5. ⚡ 右下錨定型：ETF 超賣防禦矩陣 */
    private TacticalAnalysisResult buildEtfDefensive(int fundamentals, int technicals,
                                                      int volatility, int riskMargin,
                                                      int microstructure, int sentiment,
                                                      TacticalContext ctx) {
        TacticalAnalysisResult r = new TacticalAnalysisResult();
        r.setType(TacticalType.ETF_DEFENSIVE);
        r.tacticName      = "ETF超賣防禦矩陣";
        r.tacticEmoji     = "⚡";
        r.description     = TacticalType.ETF_DEFENSIVE.getDescription();
        r.perfectMatch    = false;
        r.isBuyVetoed     = false;
        boolean isBargain = riskMargin > 65; // 風控基期分數高 = 位置低 = 折價超賣
        r.operationAdvice = "ETF 專屬診斷路由。基本面得分直接歸零（etfMode 自動排除個股財報權重）。"
                + (isBargain
                ? "🛒 ETF 點心時間已到！目前市價嚴重低於淨值，折價狀態啟動防禦買進。"
                : "技術面與波動面收縮低位，但下檔空間已被折價與成分股分散特性鎖定。")
                + "交易戰術：大額越跌越買 或 定期定額加碼，作為整體資產組態的防守型權益資產。";
        r.riskWarning     = "ETF 不具備個股爆發力，不適合短線操作。系統性風險（如金融危機）無法完全迴避。注意成分股集中度與流動性。";
        r.entrySignal     = "市價折價 > 0.5% + 技術面出現超賣反彈跡象 + 成交量回升";
        r.exitSignal      = "折價收斂至 ±0.1% 以內 或 達到預定分配目標比例。";
        r.tradeInstruction = "⚡ ETF 防禦模式：採定期定額或逢低加碼，比例依整體資產組態決定。";
        r.confidenceScore = isBargain ? 80 : 65;
        return r;
    }

    /** 6. 🕸️ 內縮乾癟型：無量邊緣冷門股（殭屍股） */
    private TacticalAnalysisResult buildZombieStock(TacticalContext ctx) {
        TacticalAnalysisResult r = new TacticalAnalysisResult();
        r.setType(TacticalType.ZOMBIE_STOCK);
        r.tacticName      = "無量邊緣冷門股";
        r.tacticEmoji     = "🕸️";
        r.description     = TacticalType.ZOMBIE_STOCK.getDescription();
        r.perfectMatch    = false;
        r.isBuyVetoed     = true;          // 一票否決
        r.operationAdvice = "一票否決制（Failover 熔斷）。算分引擎第一階段篩選直接剔除，不予配置任何資金。"
                + "六軸全部≤30：營收連續衰退、均線糾結死寂、波動顯示😴缺乏波動、"
                + "法人淨買超近乎歸零"
                + (ctx.newsCount == 0 ? "、連續數週零新聞。" : "。")
                + "徹底規避資金鎖死在無流動性殭屍標的的時間成本風險。";
        r.riskWarning     = "🚫 流動性陷阱！此股缺乏买盘支撑，一旦進場幾乎確定被套。資金時間成本極高，存在退市或掛牌異常風險。";
        r.entrySignal     = "🚫 禁止進場。此標的已被系統列入黑名單，不予任何資金配置。";
        r.exitSignal      = "若已持有：以任何可成交價格全部出清，不計成本離場。";
        r.tradeInstruction = "⛔ 一票否決：系統直接剔除，0% 資金配置。現有持倉者立即無條件清倉。";
        r.confidenceScore = 90;
        return r;
    }

    /** 7. ⚖️ 均衡中性 */
    private TacticalAnalysisResult buildBalanced() {
        TacticalAnalysisResult r = new TacticalAnalysisResult();
        r.setType(TacticalType.BALANCED);
        r.tacticName      = "均衡中性標準樣本";
        r.tacticEmoji     = "⚖️";
        r.description     = TacticalType.BALANCED.getDescription();
        r.perfectMatch    = false;
        r.isBuyVetoed     = false;
        r.operationAdvice = "各維度均衡分佈，未構成明確交越交集。等待技術面突破訊號後再介入，切勿盲目追漲。";
        r.riskWarning     = "風險中等，需耐心等待確認訊號。整體市場氛圍決定方向，建議降低單筆配置比例。";
        r.entrySignal     = "等待技術面突破確認 + 籌碼面轉強信號 + 消息面利多觸媒";
        r.exitSignal      = "跌破重要支撐位或技術面出現死叉反轉訊號。";
        r.tradeInstruction = "⚖️ 標準模式：正常倉位（50%~60%），等待催化劑出現。";
        r.confidenceScore = 60;
        return r;
    }

    /** 8. ❓ 未知組態 */
    private TacticalAnalysisResult buildUnknown() {
        TacticalAnalysisResult r = new TacticalAnalysisResult();
        r.setType(TacticalType.UNKNOWN);
        r.tacticName      = "待分類組態";
        r.tacticEmoji     = "❓";
        r.description     = TacticalType.UNKNOWN.getDescription();
        r.perfectMatch    = false;
        r.isBuyVetoed     = false;
        r.operationAdvice = "分佈無明確特徵，可能為過渡期雜訊或資料不足。建議等待更多訊號確認後再介入。";
        r.riskWarning     = "資訊不足，風險評估困難。降低倉位，保持觀望態度。";
        r.entrySignal     = "候補觀察中，等待明確訊號";
        r.exitSignal      = "候補觀察中，跌破支撐優先出場";
        r.tradeInstruction = "❓ 觀察模式：倉位 ≤ 30%，等待型態明確化。";
        r.confidenceScore = 40;
        return r;
    }
}
