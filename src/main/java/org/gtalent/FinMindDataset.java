package org.gtalent;

/**
 * FinMind 數據集列舉
 * 統一管理所有 FinMind API 數據源
 *
 * 六大核心數據庫，涵蓋法人級量化交易所需的全部維度
 *
 * @author StockPredictor Team
 * @version 1.0
 */
public enum FinMindDataset {
    // ── 已鎖碼相關 ──────────────────────────
    /** 法人買賣超 - 三大法人（外資、投信、自營商）買賣超數據 */
    CHIP("TaiwanStockInstitutionalInvestorsBuySell", "外資/投信/自營商買賣超"),

    // ── 基本面相關 ──────────────────────────
    /** 月營收 - 每月營收成長指標 */
    REVENUE("TaiwanStockMonthRevenue", "月營收數據"),

    /** 綜合損益表 - 毛利率、營業利益率、淨利率（三率三升） */
    FINANCIAL_STATEMENTS("TaiwanStockFinancialStatements", "綜合損益表"),

    /** 資產負債表 - 存貨、合約負債（領先指標） */
    BALANCE_SHEET("TaiwanStockBalanceSheet", "資產負債表"),

    /** 除權息 - 現金股利、股票股利、殖利率 */
    DIVIDEND("TaiwanStockDividend", "股利政策與除權息"),

    // ── 籌碼相關 ─────────────────────────────
    /** 股權分散 - 千張大戶集中度、散戶比例 */
    SHAREHOLDING("TaiwanStockShareholding", "股權分散與大戶控盤度"),

    /** 融資融券 - 散戶槓桿、空頭壓力、軋空預警 */
    MARGIN("TaiwanStockMarginPurchaseShortSale", "融資融券與借券賣出"),

    // ── 技術面相關 ──────────────────────────
    /** 當沖統計 - 當沖比例、市場熱度、技術指標失真風險 */
    DAY_TRADE("TaiwanStockDayTrading", "當沖交易統計與市場熱度"),

    /** 台股新聞 - 新聞標題/內容/來源，用於消息面反應分析 */
    NEWS("TaiwanStockNews", "台股新聞資料庫");

    private final String datasetName;
    private final String description;

    /**
     * 列舉構造函數
     *
     * @param datasetName FinMind API 的 Dataset 名稱
     * @param description 中文描述
     */
    FinMindDataset(String datasetName, String description) {
        this.datasetName = datasetName;
        this.description = description;
    }

    /**
     * 獲取 FinMind API Dataset 名稱
     *
     * @return Dataset 名稱（用於 API 調用）
     */
    public String getDatasetName() {
        return datasetName;
    }

    /**
     * 獲取數據集描述
     *
     * @return 中文描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 根據 Dataset 名稱查找對應的列舉值
     *
     * @param datasetName FinMind API Dataset 名稱
     * @return 對應的 FinMindDataset 列舉值，找不到則返回 null
     */
    public static FinMindDataset fromDatasetName(String datasetName) {
        for (FinMindDataset dataset : FinMindDataset.values()) {
            if (dataset.datasetName.equals(datasetName)) {
                return dataset;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return String.format("%s(%s)", this.name(), this.description);
    }
}

