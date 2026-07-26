package org.gtalent;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.logging.Logger;

/**
 * 市場服務 - 提供全市場股票代碼與市場資訊
 *
 * <p><b>職責</b>:
 * <ul>
 *   <li>從 STOCK_UNIVERSE 讀取目前有效的交易標的</li>
 *   <li>支援按類型篩選（如：上市公司、上櫃公司、ETF 等）</li>
 *   <li>提供 Q1 財報掃描所需的個股清單</li>
 * </ul>
 *
 * <p><b>資料來源</b>:
 * <ul>
 *   <li>權威讀取來源：{@link StockUniverseRepository}</li>
 *   <li>資料由 TWSE／TPEX 匯入流程寫入 STOCK_UNIVERSE</li>
 * </ul>
 *
 * @author StockPredictor Team
 * @version 1.0
 */
@Service
public class MarketService {

    private static final Logger logger = Logger.getLogger(MarketService.class.getName());
    private final StockUniverseRepository stockUniverseRepository;

    public MarketService(StockUniverseRepository stockUniverseRepository) {
        this.stockUniverseRepository = stockUniverseRepository;
    }

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
     * <p>只回傳 STOCK_UNIVERSE 中 active 的 STOCK 類型，結果由 Repository 排序。
     *
     * @return 股票代號清單（例如 ["2330", "2454", "3008", ...]）
     */
    public List<String> getAllSymbols() {
        logger.info("📊 取得全市場股票代號清單...");
        List<String> symbols = stockUniverseRepository.getAllSymbols("STOCK", null);
        logger.info("✅ 從 STOCK_UNIVERSE 取得 " + symbols.size() + " 檔股票代號");
        return symbols;
    }

    /**
     * 按類型篩選股票（進階功能）。
     *
     * @param type 股票類型（"STOCK"、"ETF"、"ALL"）
     * @return 篩選後的股票清單
     */
    public List<String> getSymbolsByType(String type) {
        String normalizedType = type == null ? "ALL" : type.trim().toUpperCase(Locale.ROOT);
        return switch (normalizedType) {
            case "ALL" -> stockUniverseRepository.getAllSymbols();
            case "STOCK", "ETN" -> stockUniverseRepository.getAllSymbols(normalizedType, null);
            case "ETF" -> getEtfSymbols();
            default -> List.of();
        };
    }

    private List<String> getEtfSymbols() {
        TreeSet<String> symbols = new TreeSet<>(stockUniverseRepository.getAllSymbols("ETF", null));
        symbols.addAll(stockUniverseRepository.getAllSymbols("BOND_ETF", null));
        return new ArrayList<>(symbols);
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

