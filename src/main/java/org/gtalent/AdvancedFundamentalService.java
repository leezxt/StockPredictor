package org.gtalent;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 進階基本面算分引擎
 *
 * <p>在原有月營收邏輯之上，整合了來自 FinMind 財務報表的兩大核心加分模組：
 * <ol>
 *   <li><b>三率三升</b>（+6 分）：毛利率、營業利益率、淨利率同步攀升，代表本業獲利品質提升。</li>
 *   <li><b>庫存健康度 + 合約負債</b>（各 +3 分）：存貨週轉天數縮短 → 貨賣得快；
 *       合約負債上升 → 未來入帳訂單增加。</li>
 * </ol>
 *
 * <p>總滿分 20 分，與 {@link ScoreEngine#scoreFundamental} 輸入規格相容。
 *
 * <p><b>資料來源：</b>
 * <ul>
 *   <li>月營收：MOPS open data（由 {@link FundamentalService} 維護）</li>
 *   <li>綜合損益表：FinMind {@code TaiwanStockFinancialStatements}</li>
 *   <li>資產負債表：FinMind {@code TaiwanStockBalanceSheet}</li>
 * </ul>
 */
@Service
public class AdvancedFundamentalService {

    // ════════════════════════════════════════════════════════════
    //  季度三率打包資料模型（用於三升三升判定）
    // ════════════════════════════════════════════════════════════

    /**
     * 單一季度的三率資料容器。
     * 用途：將 FinMind 單筆資料流打包成季度層級的完整三率，便於 QoQ 比較。
     */
    public static class QuarterMargin {
        /** 毛利率 (%) */
        public double grossMargin = 0.0;
        /** 營業利益率 (%) */
        public double operatingMargin = 0.0;
        /** 淨利率 (%) */
        public double netMargin = 0.0;

        @Override
        public String toString() {
            return String.format("QuarterMargin{毛利=%.2f%%, 營業=%.2f%%, 淨利=%.2f%%}",
                    grossMargin, operatingMargin, netMargin);
        }
    }

    // ════════════════════════════════════════════════════════════

    private static final Logger logger = Logger.getLogger(AdvancedFundamentalService.class.getName());

    // ── 財報 type 欄位對應（FinMind 可能使用英文或中文名稱） ─────────────────────────────

    // ★ 三率「直接」欄位（FinMind TaiwanStockFinancialStatements 直接提供，值已是百分比）
    // API 範例：https://api.finmindtrade.com/api/v4/data?dataset=TaiwanStockFinancialStatements
    //          &data_id=2330&start_date=2025-01-01&token=YOUR_TOKEN
    // 回傳格式：{"date":"2025-03-31","stock_id":"2330","type":"GrossProfitMargin","value":58.56}
    /** 綜合損益表：毛利率（%），FinMind 直接欄位，無需由毛利÷收入推算 */
    private static final String[] TYPE_GROSS_PROFIT_MARGIN = {
            "GrossProfitMargin", "毛利率"};
    /** 綜合損益表：營業利益率（%），FinMind 直接欄位 */
    private static final String[] TYPE_OPERATING_PROFIT_MARGIN = {
            "OperatingProfitMargin", "OperatingIncomeMargin", "營業利益率"};
    /** 綜合損益表：稅後淨利率（%），FinMind 直接欄位 */
    private static final String[] TYPE_NET_PROFIT_MARGIN = {
            "NetProfitMargin", "ProfitMargin", "稅後淨利率", "淨利率"};

    // ★ 三率「原始金額」欄位（直接欄位不存在時的後備推算來源）
    /** 綜合損益表：營業收入淨額 */
    private static final String[] TYPE_REVENUE = {"Revenue", "營業收入淨額", "淨收益"};
    /** 綜合損益表：毛利 */
    private static final String[] TYPE_GROSS_PROFIT = {"GrossProfit", "毛利（毛損）淨額", "毛利淨額", "毛利"};
    /** 綜合損益表：營業利益 */
    private static final String[] TYPE_OPERATING_INCOME = {"OperatingIncome", "營業利益（損失）", "營業利益"};
    /** 綜合損益表：稅後淨利 */
    private static final String[] TYPE_NET_INCOME = {"NetIncome", "本期淨利（淨損）", "稅後淨利", "淨利"};
    /** 資產負債表：存貨 */
    private static final String[] TYPE_INVENTORIES = {"Inventories", "存貨"};
    /** 資產負債表：合約負債 */
    private static final String[] TYPE_CONTRACT_LIABILITIES = {
            "ContractLiabilities", "ContractLiabilitiesCurrent",
            "合約負債", "預收款項", "合約負債—流動"};

    /** 季度天數（用於計算存貨週轉天數） */
    private static final double DAYS_PER_QUARTER = 91.25;

    private final FundamentalService fundamentalService;
    private final FinMindClient finMindClient;
    private final FinancialDataRepository financialDataRepository;

    public AdvancedFundamentalService(FundamentalService fundamentalService,
                                      FinMindClient finMindClient,
                                      FinancialDataRepository financialDataRepository) {
        this.fundamentalService = fundamentalService;
        this.finMindClient = finMindClient;
        this.financialDataRepository = financialDataRepository;
    }

    // ════════════════════════════════════════════════════════════
    //  公開 API
    // ════════════════════════════════════════════════════════════

    /**
     * 計算精細化基本面分數（總滿分 20 分）。
     *
     * <table border="1">
     *   <tr><th>模組</th><th>最高分</th><th>說明</th></tr>
     *   <tr><td>月營收連續成長</td><td>8</td><td>原有月營收邏輯，按比例縮短至 8 分</td></tr>
     *   <tr><td>三率三升</td><td>6</td><td>毛利率、營業利益率、淨利率同步上升</td></tr>
     *   <tr><td>存貨週轉天數下降</td><td>3</td><td>週轉加速 → 貨賣得快</td></tr>
     *   <tr><td>合約負債上升</td><td>3</td><td>預收增加 → 未來訂單保證</td></tr>
     * </table>
     *
     * @param revenueHistory 月營收歷史（至少 3 筆）
     * @param quarterHistory 季度財務歷史（至少 2 筆；由 {@link #getQuarterHistory} 取得）
     * @return 0–20 整數分數
     */
    public int calculateRefinedFundamentalScore(List<RevenueData> revenueHistory,
                                                List<FinancialQuarterData> quarterHistory) {
        int score = 0;

        // ── 1. 月營收連續成長邏輯（最高 8 分） ───────────────────────────────────────
        score += calculateRevenueSubScore(revenueHistory);

        if (quarterHistory != null && quarterHistory.size() >= 2) {
            FinancialQuarterData currentQ = quarterHistory.get(quarterHistory.size() - 1);
            FinancialQuarterData prevQ    = quarterHistory.get(quarterHistory.size() - 2);

            // ── 2. 獲利純度：三率三升（最高 6 分） ───────────────────────────────────
            boolean isTripleRise =
                    currentQ.getGrossProfitMargin()      > prevQ.getGrossProfitMargin()
                 && currentQ.getOperatingProfitMargin()  > prevQ.getOperatingProfitMargin()
                 && currentQ.getNetProfitMargin()        > prevQ.getNetProfitMargin();
            if (isTripleRise) {
                score += 6; // 本業獲利品質全面提升
                logger.fine("✅ 三率三升 +6");
            }

            // ── 3. 庫存健康度 + 領先訂單指標（最高 6 分） ────────────────────────────
            // 存貨週轉天數下降 → 去化加速（+3）
            if (currentQ.getInventoryTurnoverDays() > 0
                    && prevQ.getInventoryTurnoverDays() > 0
                    && currentQ.getInventoryTurnoverDays() < prevQ.getInventoryTurnoverDays()) {
                score += 3;
                logger.fine("✅ 存貨週轉天數下降 +3");
            }

            // 合約負債上升 → 未來營收能見度提高（+3）
            if (currentQ.getContractLiabilities() > prevQ.getContractLiabilities()) {
                score += 3;
                logger.fine("✅ 合約負債上升 +3");
            }
        }

        // 總分上限 20，下限 0
        return Math.max(0, Math.min(20, score));
    }

    /**
     * 從 FinMind 財務報表資料計算指定股票最近 N 季的 {@link FinancialQuarterData} 列表。
     *
     * <p>若 FinMind API 無法取得資料（無 token、網路錯誤），回傳空列表；
     * 呼叫端應以 {@code calculateRefinedFundamentalScore(history, List.of())} 降級為純月營收邏輯。
     *
     * @param symbol   股票代號（例如 "2330"）
     * @param quarters 需要的季數（建議 4–8）
     * @return 按季度日期升序排列的財務季報列表
     */
    public List<FinancialQuarterData> getQuarterHistory(String symbol, int quarters) {
        if (symbol == null || symbol.isBlank()) {
            return List.of();
        }

        int safeQuarters = Math.max(2, Math.min(quarters, 16));

        // ── 1. 優先讀取 DB 快取（避免重複打 FinMind API） ──────────────────────────
        List<FinancialQuarterData> cached =
                financialDataRepository.getFinancialQuarterHistory(symbol, safeQuarters);
        if (cached.size() >= safeQuarters) {
            logger.fine("[AdvancedFundamental] DB 快取命中(" + symbol + ")，共 " + cached.size() + " 季");
            return cached;
        }

        // ── 2. 快取不足，呼叫 FinMind API ──────────────────────────────────────────
        // 往前推 (safeQuarters + 1) 季再加 2 個月以確保取到足夠資料
        String startDate = LocalDate.now()
                .minusMonths((long) safeQuarters * 3 + 2)
                .withDayOfMonth(1)
                .toString();

        List<FinMindRawFinancialRow> fsRows = finMindClient.fetchFinancialStatements(symbol, startDate);
        List<FinMindRawFinancialRow> bsRows  = finMindClient.fetchBalanceSheet(symbol, startDate);

        if (fsRows.isEmpty() && bsRows.isEmpty()) {
            logger.info("⚠️  [AdvancedFundamental] 財報資料為空(" + symbol + ")，降級為純月營收模式");
            return cached.isEmpty() ? List.of() : cached;
        }

        // ── 3. 依季度日期分組：date → (type → value) ───────────────────────────────
        Map<String, Map<String, Double>> fsMap = groupByDateAndType(fsRows);
        Map<String, Map<String, Double>> bsMap = groupByDateAndType(bsRows);

        Map<String, FinancialQuarterData> quarterMap = new TreeMap<>();

        for (Map.Entry<String, Map<String, Double>> entry : fsMap.entrySet()) {
            String date               = entry.getKey();
            Map<String, Double> fs   = entry.getValue();
            Map<String, Double> bs   = bsMap.getOrDefault(date, new HashMap<>());

            // ── 三率：優先使用 FinMind 直接百分比欄位，若為 0 再回退至計算 ──────────
            //  直接欄位（如 GrossProfitMargin）的 value 已是百分比（如 58.56），
            //  無需再除以 Revenue。
            double grossProfitMargin = resolveValue(fs, TYPE_GROSS_PROFIT_MARGIN);
            double operatingProfitMargin = resolveValue(fs, TYPE_OPERATING_PROFIT_MARGIN);
            double netProfitMargin = resolveValue(fs, TYPE_NET_PROFIT_MARGIN);

            // 後備：直接欄位不存在時，從原始金額欄位計算
            if (grossProfitMargin == 0.0 || operatingProfitMargin == 0.0 || netProfitMargin == 0.0) {
                double revenue         = resolveValue(fs, TYPE_REVENUE);
                double grossProfit     = resolveValue(fs, TYPE_GROSS_PROFIT);
                double operatingIncome = resolveValue(fs, TYPE_OPERATING_INCOME);
                double netIncome       = resolveValue(fs, TYPE_NET_INCOME);
                if (revenue > 0) {
                    if (grossProfitMargin     == 0.0) grossProfitMargin     = (grossProfit     / revenue) * 100.0;
                    if (operatingProfitMargin == 0.0) operatingProfitMargin = (operatingIncome / revenue) * 100.0;
                    if (netProfitMargin       == 0.0) netProfitMargin       = (netIncome       / revenue) * 100.0;
                }
            }

            // 存貨週轉天數（資產負債表欄位，始終由原始金額推算）
            double revenue     = resolveValue(fs, TYPE_REVENUE);
            double inventories = resolveValue(bs, TYPE_INVENTORIES);
            double contractLiab = resolveValue(bs, TYPE_CONTRACT_LIABILITIES);
            int inventoryTurnoverDays = (revenue > 0 && inventories > 0)
                    ? (int) Math.round((inventories / revenue) * DAYS_PER_QUARTER) : 0;

            FinancialQuarterData qd = new FinancialQuarterData(
                    date,
                    grossProfitMargin,
                    operatingProfitMargin,
                    netProfitMargin,
                    inventoryTurnoverDays,
                    (long) contractLiab
            );
            quarterMap.put(date, qd);
            financialDataRepository.saveFinancialQuarterData(symbol, qd); // 寫入快取
        }

        // 補充僅資產負債表有但損益表無的季度
        for (Map.Entry<String, Map<String, Double>> entry : bsMap.entrySet()) {
            String date = entry.getKey();
            if (quarterMap.containsKey(date)) {
                continue;
            }
            Map<String, Double> bs  = entry.getValue();
            double inventories  = resolveValue(bs, TYPE_INVENTORIES);
            double contractLiab = resolveValue(bs, TYPE_CONTRACT_LIABILITIES);
            if (inventories > 0 || contractLiab > 0) {
                FinancialQuarterData qd = new FinancialQuarterData(
                        date, 0.0, 0.0, 0.0,
                        inventories > 0 ? (int) Math.round(inventories / DAYS_PER_QUARTER) : 0,
                        (long) contractLiab
                );
                quarterMap.put(date, qd);
                financialDataRepository.saveFinancialQuarterData(symbol, qd);
            }
        }

        List<FinancialQuarterData> sorted = new ArrayList<>(quarterMap.values());

        if (sorted.size() <= safeQuarters) {
            return sorted;
        }
        return sorted.subList(sorted.size() - safeQuarters, sorted.size());
    }

    /**
     * 建立基本面細部分析物件，供前端 AI 診斷報告使用。
     *
     * <p>根據已計算完的 {@code refinedScore}、月營收歷史與季報歷史，
     * 判斷以下旗標：
     * <ul>
     *   <li>tripleRiseActive：三率三升是否成立</li>
     *   <li>revenueGrowing：最新月營收 YoY > 0</li>
     *   <li>marginDropping：毛利率 QoQ 下滑（假成長警示）</li>
     *   <li>inventoryHealthy：存貨週轉天數下降</li>
     *   <li>contractLiabilityGrowing：合約負債上升</li>
     *   <li>hasQuarterData：是否有有效的季報資料</li>
     * </ul>
     *
     * @param revenueHistory 月營收歷史列表（至少 1 筆）
     * @param quarterHistory 季度財報歷史列表（至少 2 筆為完整分析）
     * @param refinedScore   已由 {@link #calculateRefinedFundamentalScore} 計算出的分數
     * @return 填充完畢的 {@link RadarScoreResult.FundamentalDetail}
     */
    public RadarScoreResult.FundamentalDetail buildFundamentalDetail(
            List<RevenueData> revenueHistory,
            List<FinancialQuarterData> quarterHistory,
            int refinedScore) {

        RadarScoreResult.FundamentalDetail detail = new RadarScoreResult.FundamentalDetail();
        detail.rawScore = refinedScore;

        // ── 月營收 YoY 成長旗標 ─────────────────────────────────────────────────
        if (revenueHistory != null && !revenueHistory.isEmpty()) {
            RevenueData latest = revenueHistory.get(revenueHistory.size() - 1);
            detail.revenueGrowing = latest.getYoy() > 0;
        }

        // ── 季報分析旗標（需要至少 2 季資料） ──────────────────────────────────────
        if (quarterHistory != null && quarterHistory.size() >= 2) {
            detail.hasQuarterData = true;
            FinancialQuarterData currentQ = quarterHistory.get(quarterHistory.size() - 1);
            FinancialQuarterData prevQ    = quarterHistory.get(quarterHistory.size() - 2);

            // 三率三升
            detail.tripleRiseActive =
                    currentQ.getGrossProfitMargin()     > prevQ.getGrossProfitMargin()
                 && currentQ.getOperatingProfitMargin() > prevQ.getOperatingProfitMargin()
                 && currentQ.getNetProfitMargin()       > prevQ.getNetProfitMargin();

            // 毛利率下滑（假成長警示）
            detail.marginDropping = currentQ.getGrossProfitMargin() < prevQ.getGrossProfitMargin();

            // 存貨週轉天數下降（庫存去化加速）
            detail.inventoryHealthy =
                    currentQ.getInventoryTurnoverDays() > 0
                 && prevQ.getInventoryTurnoverDays()    > 0
                 && currentQ.getInventoryTurnoverDays() < prevQ.getInventoryTurnoverDays();

            // 合約負債上升（未來訂單能見度提升）
            detail.contractLiabilityGrowing =
                    currentQ.getContractLiabilities() > prevQ.getContractLiabilities();
        }

        return detail;
    }

    // ════════════════════════════════════════════════════════════
    //  月營收子評分（最高 8 分，依原始 20 分邏輯按比例縮放）
    // ════════════════════════════════════════════════════════════

    /**
     * 月營收子評分，最高 8 分。
     * 依照原有 {@link FundamentalService#calculateRevenueScore} 邏輯等比縮放（0→0，20→8）。
     */
    int calculateRevenueSubScore(List<RevenueData> revenueHistory) {
        int raw = fundamentalService.calculateRevenueScore(revenueHistory);
        // 原始滿分 20 → 本引擎滿分 8，等比縮放
        return (int) Math.round(raw / 20.0 * 8.0);
    }

    // ════════════════════════════════════════════════════════════
    //  內部工具方法
    // ════════════════════════════════════════════════════════════

    /**
     * 將原始財報列表依「季度日期 → 科目類型 → 金額」分組。
     */
    private Map<String, Map<String, Double>> groupByDateAndType(List<FinMindRawFinancialRow> rows) {
        Map<String, Map<String, Double>> map = new HashMap<>();
        for (FinMindRawFinancialRow row : rows) {
            if (row.getDate() == null || row.getType() == null) {
                continue;
            }
            map.computeIfAbsent(row.getDate(), k -> new HashMap<>())
               .put(row.getType(), row.getValue());
        }
        return map;
    }

    /**
     * 依優先順序嘗試多個 type 名稱，返回第一個匹配到的數值（找不到回 0.0）。
     */
    private double resolveValue(Map<String, Double> typeMap, String[] candidateTypes) {
        for (String key : candidateTypes) {
            Double val = typeMap.get(key);
            if (val != null) {
                return val;
            }
        }
        return 0.0;
    }

    // ════════════════════════════════════════════════════════════
    //  三率三升過濾引擎（核心業務邏輯）
    // ════════════════════════════════════════════════════════════

    /**
     * 分析最新一季財報是否符合「三率三升」條件。
     *
     * <p><b>原理</b>: FinMind 回傳的資料按欄位單筆發放，本方法將同季度的三率打包成一個季度物件，
     * 然後比較最新季度與前一季度，判定三個利率是否全面上升。
     *
     * <p><b>評分規則</b>:
     * <ul>
     *   <li>若最新季度的毛利率、營業利益率、淨利率<b>全部</b>大於前一季度 → 8 分</li>
     *   <li>否則 → 0 分</li>
     * </ul>
     *
     * @param rawFinancials FinMind 抓回來的原始損益表清單（按欄位單筆組織）
     * @return 三率三升得分（8 或 0）
     */
    public int checkTripleRiseScore(List<FinMindFinancialData> rawFinancials) {
        if (rawFinancials == null || rawFinancials.isEmpty()) {
            logger.fine("⚠️  原始財報資料為空，無法判定三率三升");
            return 0;
        }

        // 1️⃣ 將資料依「日期(季度)」群組化，把同季度的三率打包在一起
        Map<String, List<FinMindFinancialData>> groupedByDate = rawFinancials.stream()
                .collect(Collectors.groupingBy(FinMindFinancialData::getDate));

        // 2️⃣ 轉換為有序的季度資料地圖 (TreeMap 會自動按時間排序)
        TreeMap<String, QuarterMargin> quarterMap = new TreeMap<>();

        groupedByDate.forEach((date, list) -> {
            QuarterMargin qMargin = new QuarterMargin();
            for (FinMindFinancialData data : list) {
                String type = data.getType();
                if (type == null) continue;

                // 依 type 填充對應的利率欄位
                if (isGrossProfitMarginType(type)) {
                    qMargin.grossMargin = data.getValue();
                } else if (isOperatingProfitMarginType(type)) {
                    qMargin.operatingMargin = data.getValue();
                } else if (isNetProfitMarginType(type)) {
                    qMargin.netMargin = data.getValue();
                }
            }
            quarterMap.put(date, qMargin);
            logger.fine("📊 季度 " + date + " 打包完成: " + qMargin);
        });

        if (quarterMap.size() < 2) {
            logger.fine("⚠️  季度資料不足（少於 2 季），無法進行 QoQ 比較");
            return 0;
        }

        // 3️⃣ 取得最新一季 與 前一季 的資料
        List<String> sortedDates = new ArrayList<>(quarterMap.keySet());
        String currentQuarterDate = sortedDates.get(sortedDates.size() - 1);
        String prevQuarterDate = sortedDates.get(sortedDates.size() - 2);

        QuarterMargin currentQ = quarterMap.get(currentQuarterDate);
        QuarterMargin prevQ = quarterMap.get(prevQuarterDate);

        logger.fine("比較季度: 前期 " + prevQuarterDate + " [" + prevQ + "]" +
                " vs 本期 " + currentQuarterDate + " [" + currentQ + "]");

        // 4️⃣ 三率三升核心邏輯判定 (最新一季全面大於前一季)
        boolean isGrossRise = currentQ.grossMargin > prevQ.grossMargin;
        boolean isOperatingRise = currentQ.operatingMargin > prevQ.operatingMargin;
        boolean isNetRise = currentQ.netMargin > prevQ.netMargin;

        logger.fine(String.format("三率漲跌: 毛利[%s] 營業[%s] 淨利[%s]",
                isGrossRise ? "↑" : "↓", isOperatingRise ? "↑" : "↓", isNetRise ? "↑" : "↓"));

        if (isGrossRise && isOperatingRise && isNetRise) {
            logger.info("🔥 偵測到三率三升黑馬股！季度：" + currentQuarterDate +
                    " | 毛利 " + prevQ.grossMargin + "% → " + currentQ.grossMargin + "%" +
                    " | 營業 " + prevQ.operatingMargin + "% → " + currentQ.operatingMargin + "%" +
                    " | 淨利 " + prevQ.netMargin + "% → " + currentQ.netMargin + "%");
            return 8; // 財報得分拿滿
        }

        logger.fine("⚠️  未滿足三率三升條件（需全三項同步上升）");
        return 0;
    }

    /**
     * 判定字串是否代表毛利率 type。
     */
    private boolean isGrossProfitMarginType(String type) {
        for (String candidate : TYPE_GROSS_PROFIT_MARGIN) {
            if (candidate.equalsIgnoreCase(type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判定字串是否代表營業利益率 type。
     */
    private boolean isOperatingProfitMarginType(String type) {
        for (String candidate : TYPE_OPERATING_PROFIT_MARGIN) {
            if (candidate.equalsIgnoreCase(type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判定字串是否代表淨利率 type。
     */
    private boolean isNetProfitMarginType(String type) {
        for (String candidate : TYPE_NET_PROFIT_MARGIN) {
            if (candidate.equalsIgnoreCase(type)) {
                return true;
            }
        }
        return false;
    }
}

