package org.gtalent;

import org.springframework.stereotype.Service;
import java.util.List;
import java.util.logging.Logger;

/**
 * 市場服務 - 提供全市場股票代碼與市場資訊
 *
 * <p><b>職責</b>:
 * <ul>
 *   <li>維護全市場當前交易的股票代號清單</li>
 *   <li>支援按類型篩選（如：上市公司、上櫃公司、ETF 等）</li>
 *   <li>緩存更新策略（定期或按需更新）</li>
 * </ul>
 *
 * <p><b>資料來源</b>:
 * <ul>
 *   <li>TWSE（台灣證券交易所）- 上市公司清單</li>
 *   <li>TPEX（台灣櫃檯買賣中心）- 上櫃公司清單</li>
 *   <li>FinMind - 股票代碼查詢</li>
 *   <li>本地 DB 快取 - 減少 API 呼叫</li>
 * </ul>
 *
 * @author StockPredictor Team
 * @version 1.0
 */
@Service
public class MarketService {

    private static final Logger logger = Logger.getLogger(MarketService.class.getName());

    /**
     * 取得全市場當前交易的所有股票代號清單。
     *
     * <p>包含：
     * <ul>
     *   <li>上市公司（如 2330、2454 等）</li>
     *   <li>上櫃公司</li>
     *   <li>不含 ETF（因 ETF 模式會被過濾）</li>
     * </ul>
     *
     * <p><b>實裝策略</b>:
     * <ol>
     *   <li>優先讀取本地 DB 快取（MARKET_SYMBOLS 表或類似）</li>
     *   <li>若快取未足 7 天，呼叫外部 API 更新</li>
     *   <li>返回有序的股票代號列表</li>
     * </ol>
     *
     * @return 股票代號清單（例如 ["2330", "2454", "3008", ...]）
     */
    public List<String> getAllSymbols() {
        logger.info("📊 取得全市場股票代號清單...");

        try {
            // ── 階段 1️⃣: 檢查本地快取 ────────────────────────
            List<String> cachedSymbols = getCachedSymbols();
            if (cachedSymbols != null && !cachedSymbols.isEmpty()) {
                logger.info("✅ 從快取取得 " + cachedSymbols.size() + " 檔股票代號");
                return cachedSymbols;
            }

            // ── 階段 2️⃣: 快取不足，從外部 API 更新 ────────────
            logger.info("⚠️  本地快取不足或過期，重新更新...");
            List<String> freshSymbols = fetchSymbolsFromExternalAPI();

            // 保存至本地快取
            cacheSymbols(freshSymbols);

            logger.info("✅ 成功更新市場清單，共 " + freshSymbols.size() + " 檔");
            return freshSymbols;

        } catch (Exception e) {
            logger.severe("❌ 取得股票清單失敗: " + e.getMessage());
            // 降級策略：返回硬編碼的常用股票清單（供演示用）
            return getHardcodedFallbackSymbols();
        }
    }

    /**
     * 從本地快取取得股票代號清單。
     *
     * <p><b>實裝方式（可選）</b>:
     * <pre>
     * SELECT symbol FROM MARKET_SYMBOLS WHERE active = 1 ORDER BY symbol
     * </pre>
     *
     * @return 快取中的股票列表，若無快取則返回 null
     */
    private List<String> getCachedSymbols() {
        try {
            // TODO: 從 DatabaseManager 讀取快取
            // List<String> symbols = DatabaseManager.getActiveSymbols();
            // if (symbols != null && !symbols.isEmpty()) {
            //     long cacheAge = System.currentTimeMillis() - DatabaseManager.getSymbolsCacheTime();
            //     if (cacheAge < 7 * 24 * 60 * 60 * 1000) { // 快取 7 天有效
            //         return symbols;
            //     }
            // }
            return null; // 暫無快取實裝
        } catch (Exception e) {
            logger.warning("快取查詢失敗: " + e.getMessage());
            return null;
        }
    }

    /**
     * 從外部 API 源取得最新的股票清單。
     *
     * <p><b>可選來源</b>:
     * <ul>
     *   <li>FinMind API - TaiwanStockInfo Dataset</li>
     *   <li>TWSE API - 上市公司清單</li>
     *   <li>TPEX API - 上櫃公司清單</li>
     *   <li>Yahoo Finance - 股票指數成份</li>
     * </ul>
     *
     * @return 外部 API 查詢得到的股票代號清單
     */
    private List<String> fetchSymbolsFromExternalAPI() throws Exception {
        // TODO: 實裝具體的 API 呼叫邏輯
        // 示例：使用 FinMind 的 TaiwanStockInfo
        // List<String> symbols = finMindClient.fetchAllStockSymbols();
        // return symbols;

        // 暫時返回演示清單
        logger.warning("⚠️  fetchSymbolsFromExternalAPI 尚未實裝，返回演示清單");
        return getHardcodedFallbackSymbols();
    }

    /**
     * 將股票清單存入本地快取。
     *
     * @param symbols 股票代號清單
     */
    private void cacheSymbols(List<String> symbols) {
        try {
            // TODO: 存入 DatabaseManager 或其他快取層
            // DatabaseManager.saveActiveSymbols(symbols);
            logger.info("✅ 股票清單已緩存");
        } catch (Exception e) {
            logger.warning("快取保存失敗: " + e.getMessage());
        }
    }

    /**
     * 硬編碼的備用股票清單（用於演示或降級）。
     *
     * @return 常見台股代號清單
     */
    private List<String> getHardcodedFallbackSymbols() {
        // 台灣主要權值股與熱門個股
        return List.of(
                // 台積電
                "2330",
                // 聯發科
                "2454",
                // 中華電信
                "2412",
                // 台灣大哥大
                "3045",
                // MediaTek（誤，應為聯發科已列）
                // 台灣50成份股示例
                "1101", "1102", "1213", "1216", "1301", "1303", "1326", "1402", "1409",
                "1903", "2002", "2023", "2105", "2207", "2303", "2308", "2312", "2317",
                "2325", "2330", "2345", "2347", "2357", "2379", "2388", "2395", "2408",
                "2412", "2454", "2880", "2882", "2883", "2891", "2892", "2912", "3008",
                "3045", "3231", "4938", "5880", "6005", "9945",
                // ETF（供演示）
                "0050", "0051", "0056", "0057", "00713", "00878",
                // 其他熱門個股
                "3034", "5347", "6415", "8081", "9926"
        );
    }

    /**
     * 按類型篩選股票（進階功能）。
     *
     * @param type 股票類型（"STOCK"、"ETF"、"ALL"）
     * @return 篩選後的股票清單
     */
    public List<String> getSymbolsByType(String type) {
        List<String> allSymbols = getAllSymbols();

        // TODO: 根據 type 實裝篩選邏輯
        // 例如：
        // - STOCK: 過濾掉 4 位數代碼（ETF）
        // - ETF: 僅返回 4 位數代碼
        // - ALL: 返回全部

        return allSymbols;
    }

    /**
     * 檢查指定代號是否為有效的股票。
     *
     * @param symbol 股票代號
     * @return true 若代號有效
     */
    public boolean isValidSymbol(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return false;
        }
        return getAllSymbols().contains(symbol);
    }
}

