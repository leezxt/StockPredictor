package org.gtalent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 策略型掃描控制器
 *
 * <p>提供全市場自動化掃描功能，用於識別符合特定策略條件的股票（如三率三升黑馬股）。
 * 此控制器旨在跨越批量查詢場景，適合定期或一次性掃描任務。
 *
 * @author StockPredictor Team
 * @version 1.0
 */
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"})
@RestController
@RequestMapping("/api/strategy")
public class StrategyController {

    private static final Logger logger = Logger.getLogger(StrategyController.class.getName());

    /** 免費 API 限流間隔（毫秒），避免被阻擋 */
    private static final long API_RATE_LIMIT_MS = 200;

    /** 財報查詢起始日期（涵蓋前季 Q4 與本季 Q1） */
    private static final String FINANCIAL_QUERY_START_DATE = "2025-10-01";

    @Autowired
    private MarketService marketService;

    @Autowired
    private FinMindClient finMindClient;

    @Autowired
    private AdvancedFundamentalService advancedFundamentalService;

    // ════════════════════════════════════════════════════════════
    //  一級 API：全市場黑馬股掃描
    // ════════════════════════════════════════════════════════════

    /**
     * 全市場 Q1 三率三升黑馬股一鍵掃描。
     *
     * <p><b>工作流程</b>:
     * <ol>
     *   <li>取得全市場當前交易股票代號清單</li>
     *   <li>遍歷每檔股票，從 FinMind 抓取最新季報</li>
     *   <li>調用 {@link AdvancedFundamentalService#checkTripleRiseScore} 判定三率三升</li>
     *   <li>收集符合條件的黑馬股清單</li>
     *   <li>返回結果 JSON</li>
     * </ol>
     *
     * <p><b>API 限流</b>: 每筆查詢間隔 {@code API_RATE_LIMIT_MS} ms，避免被 FinMind 阻擋。
     *
     * @return 包含掃描結果統計與黑馬股清單的 ResponseEntity
     *
     * <p><b>響應範例</b>:
     * <pre>
     * {
     *   "status": "success",
     *   "totalScanned": 1500,
     *   "tripleRoseFound": 47,
     *   "scanDurationMs": 312000,
     *   "blackHorseList": ["2330", "2454", "3008", ...],
     *   "message": "掃描完成！共發現 47 隻符合三率三升條件的黑馬股"
     * }
     * </pre>
     */
    @GetMapping("/q1-black-horse")
    public ResponseEntity<?> scanQ1BlackHorses() {
        logger.info("🚀 啟動全市場 Q1 黑馬股掃描任務...");
        long startTime = System.currentTimeMillis();

        try {
            // 1️⃣ 取得全市場股票代碼清單
            List<String> allSymbols = marketService.getAllSymbols();
            if (allSymbols == null || allSymbols.isEmpty()) {
                logger.warning("❌ 無法取得市場股票清單");
                return ResponseEntity.ok(buildErrorResponse("無法取得市場股票清單"));
            }

            logger.info("📊 取得全市場股票清單: 共 " + allSymbols.size() + " 檔");

            // 2️⃣ 掃描並收集黑馬股
            List<String> blackHorseList = new ArrayList<>();
            AtomicInteger processedCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);

            for (String symbol : allSymbols) {
                try {
                    // API 限流
                    Thread.sleep(API_RATE_LIMIT_MS);

                    // 從 FinMind 抓取財報資料（原始 Row 格式）
                    List<FinMindRawFinancialRow> rawRows =
                        finMindClient.fetchFinancialStatements(symbol, FINANCIAL_QUERY_START_DATE);

                    // 轉換為 FinMindFinancialData 格式
                    List<FinMindFinancialData> financials = convertToFinancialData(rawRows);

                    // 調用三率三升判定引擎
                    int score = advancedFundamentalService.checkTripleRiseScore(financials);

                    if (score > 0) {
                        blackHorseList.add(symbol);
                        logger.info("✅ [" + processedCount.incrementAndGet() + "/" + allSymbols.size() + "] "
                                + symbol + " 符合三率三升 (得分: " + score + ")");
                    } else {
                        processedCount.incrementAndGet();
                    }

                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.warning("⚠️  掃描被中斷: " + ie.getMessage());
                    break;
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    logger.warning("⚠️  掃描 " + symbol + " 失敗: " + e.getMessage());
                    processedCount.incrementAndGet();
                    // 繼續掃描下一檔
                }
            }

            long endTime = System.currentTimeMillis();
            long durationMs = endTime - startTime;

            // 3️⃣ 構建成功響應
            return ResponseEntity.ok(buildSuccessResponse(
                    allSymbols.size(),
                    blackHorseList.size(),
                    errorCount.get(),
                    durationMs,
                    blackHorseList
            ));

        } catch (Exception e) {
            logger.severe("❌ 掃描任務異常終止: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500)
                    .body(buildErrorResponse("掃描任務失敗: " + e.getMessage()));
        }
    }

    // ════════════════════════════════════════════════════════════
    //  進階 API：按條件篩選掃描
    // ════════════════════════════════════════════════════════════

    /**
     * 指定股票代號列表的黑馬股掃描（用於快速驗證或自訂篩選）。
     *
     * @param symbols 由逗號分隔的股票代號 (e.g., "2330,2454,3008")
     * @return 掃描結果
     *
     * <p><b>使用方式</b>:
     * <pre>
     * GET /api/strategy/q1-black-horse-custom?symbols=2330,2454,3008,6415
     * </pre>
     */
    @GetMapping("/q1-black-horse-custom")
    public ResponseEntity<?> scanCustomSymbols(
            @RequestParam(name = "symbols") String symbols) {

        logger.info("🚀 啟動自訂符號掃描任務: " + symbols);
        long startTime = System.currentTimeMillis();

        try {
            // 解析符號列表
            List<String> symbolList = Arrays.stream(symbols.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());

            if (symbolList.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(buildErrorResponse("符號列表為空"));
            }

            logger.info("📊 開始掃描 " + symbolList.size() + " 檔股票");

            // 掃描邏輯（與全市場相同）
            List<String> blackHorseList = new ArrayList<>();
            int errorCount = 0;

            for (String symbol : symbolList) {
                try {
                    Thread.sleep(API_RATE_LIMIT_MS);

                    // 從 FinMind 抓取財報資料（原始 Row 格式）
                    List<FinMindRawFinancialRow> rawRows =
                            finMindClient.fetchFinancialStatements(symbol, FINANCIAL_QUERY_START_DATE);

                    // 轉換為 FinMindFinancialData 格式
                    List<FinMindFinancialData> financials = convertToFinancialData(rawRows);

                    int score = advancedFundamentalService.checkTripleRiseScore(financials);

                    if (score > 0) {
                        blackHorseList.add(symbol);
                        logger.info("✅ " + symbol + " 符合三率三升 (得分: " + score + ")");
                    }

                } catch (Exception e) {
                    errorCount++;
                    logger.warning("⚠️  掃描 " + symbol + " 失敗: " + e.getMessage());
                }
            }

            long durationMs = System.currentTimeMillis() - startTime;

            return ResponseEntity.ok(buildSuccessResponse(
                    symbolList.size(),
                    blackHorseList.size(),
                    errorCount,
                    durationMs,
                    blackHorseList
            ));

        } catch (Exception e) {
            logger.severe("❌ 自訂掃描失敗: " + e.getMessage());
            return ResponseEntity.status(500)
                    .body(buildErrorResponse("掃描失敗: " + e.getMessage()));
        }
    }

    // ════════════════════════════════════════════════════════════
    //  響應建構工具
    // ════════════════════════════════════════════════════════════

    /**
     * 構建成功響應 DTO。
     */
    private Map<String, Object> buildSuccessResponse(
            int totalScanned,
            int tripleRoseFound,
            int errorCount,
            long durationMs,
            List<String> blackHorseList) {

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("totalScanned", totalScanned);
        response.put("tripleRoseFound", tripleRoseFound);
        response.put("errorCount", errorCount);
        response.put("scanDurationMs", durationMs);
        response.put("successRate", String.format("%.1f%%",
                (totalScanned - errorCount) * 100.0 / totalScanned));
        response.put("blackHorseList", blackHorseList);
        response.put("message", String.format(
                "✅ 掃描完成！共掃描 %d 檔，發現 %d 隻符合三率三升條件的黑馬股（耗時 %.1f 秒）",
                totalScanned, tripleRoseFound, durationMs / 1000.0));

        logger.info(String.format(
                "✅ 掃描統計: 總計 %d, 成功 %d, 失敗 %d, 黑馬 %d, 耗時 %.1fs",
                totalScanned, totalScanned - errorCount, errorCount, tripleRoseFound, durationMs / 1000.0));

        return response;
    }

    /**
     * 構建錯誤響應 DTO。
     */
    private Map<String, Object> buildErrorResponse(String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "error");
        response.put("message", message);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }

    // ════════════════════════════════════════════════════════════
    //  資料轉換工具
    // ════════════════════════════════════════════════════════════

    /**
     * 將 FinMindRawFinancialRow 轉換為 FinMindFinancialData 格式。
     *
     * @param rawRows 原始財報行資料
     * @return 轉換後的財務資料物件
     */
    private List<FinMindFinancialData> convertToFinancialData(List<FinMindRawFinancialRow> rawRows) {
        if (rawRows == null) {
            return List.of();
        }

        return rawRows.stream()
                .map(row -> new FinMindFinancialData(
                        row.getDate(),
                        row.getStockId(),
                        row.getType(),
                        row.getValue()
                ))
                .collect(Collectors.toList());
    }
}

