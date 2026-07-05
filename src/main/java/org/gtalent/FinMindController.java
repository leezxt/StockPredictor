package org.gtalent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FinMind API 控制器示例
 * 暴露 REST 端點來調用 FinMind 數據
 *
 * API 端點示例：
 * - GET /api/finmind/chip-data?symbol=2330&date=2024-01-01
 * - GET /api/finmind/latest?symbol=2330
 * - GET /api/finmind/date-range?symbol=2330&startDate=2024-01-01&endDate=2024-03-31
 * - GET /api/finmind/chip-score?symbol=2330
 * - GET /api/finmind/chip-trend?symbol=2330
 */
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"})
@RestController
@RequestMapping("/api/finmind")
public class FinMindController {

    @Autowired
    private FinMindClient finMindClient;

    @Autowired
    private EnhancedInstitutionalService enhancedInstitutionalService;

    /**
     * 獲取籌碼資料
     *
     * @param symbol    股票代號（例如："2330"）
     * @param startDate 開始日期（格式："2024-01-01"）
     * @return 籌碼資料列表
     */
    @GetMapping("/chip-data")
    public ResponseEntity<?> getChipData(
            @RequestParam(name = "symbol") String symbol,
            @RequestParam(name = "startDate") String startDate) {

        try {
            List<FinMindChipData> data = finMindClient.fetchChipData(symbol, startDate);

            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol);
            response.put("startDate", startDate);
            response.put("dataCount", data.size());
            response.put("data", data);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "無法獲取數據: " + e.getMessage()));
        }
    }

    /**
     * 獲取最新籌碼資料
     *
     * @param symbol 股票代號
     * @return 最新籌碼資料列表
     */
    @GetMapping("/latest")
    public ResponseEntity<?> getLatestChipData(@RequestParam(name = "symbol") String symbol) {
        try {
            List<FinMindChipData> data = finMindClient.fetchLatestChipData(symbol);

            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol);
            response.put("dataCount", data.size());
            response.put("data", data);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "無法獲取數據: " + e.getMessage()));
        }
    }

    /**
     * 獲取日期範圍內的籌碼資料
     *
     * @param symbol    股票代號
     * @param startDate 開始日期
     * @param endDate   結束日期
     * @return 指定日期範圍內的籌碼資料
     */
    @GetMapping("/date-range")
    public ResponseEntity<?> getChipDataByDateRange(
            @RequestParam(name = "symbol") String symbol,
            @RequestParam(name = "startDate") String startDate,
            @RequestParam(name = "endDate") String endDate) {

        try {
            List<FinMindChipData> data = finMindClient.fetchChipDataByDateRange(symbol, startDate, endDate);

            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol);
            response.put("startDate", startDate);
            response.put("endDate", endDate);
            response.put("dataCount", data.size());
            response.put("data", data);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "無法獲取數據: " + e.getMessage()));
        }
    }

    /**
     * 帶重試機制的籌碼資料獲取
     *
     * @param symbol  股票代號
     * @param date    開始日期
     * @param retries 重試次數
     * @return 籌碼資料列表
     */
    @GetMapping("/with-retry")
    public ResponseEntity<?> getChipDataWithRetry(
            @RequestParam(name = "symbol") String symbol,
            @RequestParam(name = "date") String date,
            @RequestParam(name = "retries", defaultValue = "3") int retries) {

        try {
            List<FinMindChipData> data = finMindClient.fetchChipDataWithRetry(symbol, date, retries);

            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol);
            response.put("date", date);
            response.put("retries", retries);
            response.put("dataCount", data.size());
            response.put("data", data);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "無法獲取數據: " + e.getMessage()));
        }
    }

    /**
     * 獲取籌碼集中度分數
     *
     * @param symbol 股票代號
     * @return 分數（0-100）
     */
    @GetMapping("/chip-score")
    public ResponseEntity<?> getChipConcentrationScore(@RequestParam(name = "symbol") String symbol) {
        try {
            int score = enhancedInstitutionalService.calculateChipConcentrationScore(symbol);

            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol);
            response.put("score", score);
            response.put("interpretation", interpretScore(score));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "無法計算分數: " + e.getMessage()));
        }
    }

    /**
     * 獲取籌碼趨勢分析
     *
     * @param symbol 股票代號
     * @return 趨勢分析結果
     */
    @GetMapping("/chip-trend")
    public ResponseEntity<?> analyzeChipTrend(@RequestParam(name = "symbol") String symbol) {
        try {
            Object analysis = enhancedInstitutionalService.analyzeChipTrend(symbol);
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "無法分析趨勢: " + e.getMessage()));
        }
    }

    /**
     * 獲取機構投資者數據（帶多源備份）
     *
     * @param symbol 股票代號
     * @param days   查詢天數
     * @return 機構投資者交易紀錄
     */
    @GetMapping("/institutional-data")
    public ResponseEntity<?> getInstitutionalDataWithFallback(
            @RequestParam(name = "symbol") String symbol,
            @RequestParam(name = "days", defaultValue = "10") int days) {

        try {
            List<InstitutionalTrade> data =
                    enhancedInstitutionalService.getInstitutionalDataWithFallback(symbol, days);

            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol);
            response.put("days", days);
            response.put("dataCount", data.size());
            response.put("data", data);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "無法獲取機構數據: " + e.getMessage()));
        }
    }

    // ── 輔助方法 ──────────────────────────────────────────

    /**
     * 解釋籌碼集中度分數
     */
    private String interpretScore(int score) {
        if (score >= 70) return "📈 非常強勢 - 籌碼集中度極高";
        if (score >= 50) return "📊 強勢 - 籌碼集中度高";
        if (score >= 30) return "📉 中等 - 籌碼集中度中等";
        if (score >= 10) return "😐 弱勢 - 籌碼集中度低";
        return "💤 非常弱勢 - 籌碼分散";
    }
}

