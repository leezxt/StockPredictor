package org.gtalent;

import org.gtalent.dto.BollingerResult;
import org.gtalent.dto.IchimokuResult;
import org.gtalent.dto.InstitutionalSyncResult;
import org.gtalent.dto.VolumeAnomalyResult;
import org.gtalent.dto.DivergenceResult;
import org.gtalent.dto.PredictionResult;
import org.gtalent.dto.SymbolProfileResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/stocks")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"}) // 讓本機網頁可以順利抓到資料
public class StockController {
    private static final int DEFAULT_HISTORY_DAYS = 100;
    private static final int MAX_BACKFILL_MONTHS = 120;
    private static final int MAX_CONSECUTIVE_EMPTY_MONTHS = 6;
    private final TwseService twseService = new TwseService();
    private final ScannerService scannerService;
    private final ScanTaskService scanTaskService;
    private final InstitutionalService institutionalService;
    private final EnhancedInstitutionalService enhancedInstitutionalService;
    private final VolumeAnalysisService volumeAnalysisService;
    private final DivergenceService divergenceService;
    private final AIPredictionService aiPredictionService;
    private final SymbolProfileService symbolProfileService;
    private final ScanHistoryRepository historyRepository;
    private final RadarService radarService;
    private final FundamentalService fundamentalService;
    private final DayTradingService dayTradingService;
    private final BulkFetchService bulkFetchService;
    private final KdAdvancedService kdAdvancedService;
    private final ScheduledService scheduledService;

    @Autowired
    public StockController(ScannerService scannerService,
                           ScanTaskService scanTaskService,
                           InstitutionalService institutionalService,
                           EnhancedInstitutionalService enhancedInstitutionalService,
                           VolumeAnalysisService volumeAnalysisService,
                           DivergenceService divergenceService,
                           AIPredictionService aiPredictionService,
                           SymbolProfileService symbolProfileService,
                           ScanHistoryRepository historyRepository,
                           RadarService radarService,
                           FundamentalService fundamentalService,
                           DayTradingService dayTradingService,
                           BulkFetchService bulkFetchService,
                           KdAdvancedService kdAdvancedService,
                           ScheduledService scheduledService) {
        this.scannerService = scannerService;
        this.scanTaskService = scanTaskService;
        this.institutionalService = institutionalService;
        this.enhancedInstitutionalService = enhancedInstitutionalService;
        this.volumeAnalysisService = volumeAnalysisService;
        this.divergenceService = divergenceService;
        this.aiPredictionService = aiPredictionService;
        this.symbolProfileService = symbolProfileService;
        this.historyRepository = historyRepository;
        this.radarService = radarService;
        this.fundamentalService = fundamentalService;
        this.dayTradingService = dayTradingService;
        this.bulkFetchService = bulkFetchService;
        this.kdAdvancedService = kdAdvancedService;
        this.scheduledService = scheduledService;
    }

    @GetMapping("/{symbol}/ma")
    public StockAnalysis getAnalysis(@PathVariable("symbol") String symbol) {
        String cleanSymbol = normalizeSymbol(symbol);
        ensureSymbolData(cleanSymbol, 20);
        double ma5 = DatabaseManager.calculateMA(cleanSymbol, 5);
        double ma20 = DatabaseManager.calculateMA(cleanSymbol, 20);
        return new StockAnalysis(cleanSymbol, ma5, ma20);
    }

    @GetMapping("/{symbol}/profile")
    public SymbolProfileResult getSymbolProfile(@PathVariable("symbol") String symbol) {
        String cleanSymbol = normalizeSymbol(symbol);
        ensureSymbolData(cleanSymbol, 120);
        return symbolProfileService.getProfile(cleanSymbol);
    }

    @GetMapping("/{symbol}/indicators/advanced")
    public Map<String, Object> getAdvancedIndicators(@PathVariable("symbol") String symbol) {
        String cleanSymbol = normalizeSymbol(symbol);
        ensureSymbolData(cleanSymbol, 120);

        Map<String, Object> response = new HashMap<>();
        response.put("symbol", cleanSymbol);
        response.put("bollinger", IndicatorCalculator.calculateBollinger(cleanSymbol));
        response.put("ichimoku", IndicatorCalculator.calculateIchimoku(cleanSymbol));
        response.put("vwap", IndicatorCalculator.calculateVWAP(cleanSymbol));
        response.put("timestamp", System.currentTimeMillis());

        return response;
    }

    @GetMapping("/{symbol}/chip/analysis")
    public Map<String, Object> getChipAnalysis(@PathVariable("symbol") String symbol) {
        String cleanSymbol = normalizeSymbol(symbol);
        ensureSymbolData(cleanSymbol, 10);

        Map<String, Object> response = new HashMap<>();
        response.put("symbol", cleanSymbol);
        response.put("institutionalSync", enhancedInstitutionalService.calculateInstitutionalSync(cleanSymbol, 10));
        response.put("volumeAnomaly", volumeAnalysisService.detectVolumeAnomaly(cleanSymbol));
        response.put("timestamp", System.currentTimeMillis());

        return response;
    }

    @GetMapping("/scan/ma-patterns")
    public List<ScannedResult> scanMAPatterns(@RequestParam(name = "type", defaultValue = "BULLISH") String type) {
        return scannerService.scanMAPatterns(type);
    }

    @GetMapping("/{symbol}/divergence")
    public Map<String, Object> getDivergence(@PathVariable("symbol") String symbol) {
        String cleanSymbol = normalizeSymbol(symbol);
        ensureSymbolData(cleanSymbol, 60);

        Map<String, Object> response = new HashMap<>();
        response.put("symbol", cleanSymbol);
        response.put("rsiDivergence", divergenceService.detectRSIDivergence(cleanSymbol));
        response.put("macdDivergence", divergenceService.detectMACDDivergence(cleanSymbol));
        response.put("timestamp", System.currentTimeMillis());

        return response;
    }

    @GetMapping("/{symbol}/prediction")
    public PredictionResult getPrediction(@PathVariable("symbol") String symbol) {
        String cleanSymbol = normalizeSymbol(symbol);
        ensureSymbolData(cleanSymbol, 60);
        return aiPredictionService.predict(cleanSymbol);
    }

    @GetMapping("/{symbol}/diagnosis")
    public Map<String, Object> getIntegratedDiagnosis(@PathVariable("symbol") String symbol) {
        String cleanSymbol = normalizeSymbol(symbol);
        ensureSymbolData(cleanSymbol, 120);

        Map<String, Object> response = new HashMap<>();

        // 1. 取得基礎數據
        double currentPrice = DatabaseManager.getLatestPrice(cleanSymbol);
        double ma5 = DatabaseManager.calculateMA(cleanSymbol, 5);
        double ma20 = DatabaseManager.calculateMA(cleanSymbol, 20);
        double rsi = IndicatorCalculator.calculateRSI(cleanSymbol, 14);

        // 2. 綜合診斷邏輯
        String status;
        String color;
        String suggestion;
        String riskLevel;

        // 判斷趨勢（均線）
        boolean isBullish = (currentPrice > ma5 && ma5 > ma20);

        // 判斷力道（RSI）
        if (isBullish) {
            if (rsi > 75) {
                status = "多頭過熱";
                color = "#ff8c00";
                suggestion = "目前處於強勢多頭，但 RSI 已達 " + Math.round(rsi) + "，顯示嚴重超買，建議「暫不加碼」並設好停利。";
                riskLevel = "高";
            } else if (rsi < 40) {
                status = "多頭背離";
                color = "#6f42c1";
                suggestion = "均線維持多頭，但 RSI 低於 40，出現動能背離現象，需小心趨勢反轉。";
                riskLevel = "中高";
            } else {
                status = "多頭穩健";
                color = "#ff4d4d";
                suggestion = "趨勢與動能配合良好，建議續抱。";
                riskLevel = "低";
            }
        } else if (currentPrice < ma20) {
            if (rsi < 25) {
                status = "空頭超買 (超賣)";
                color = "#007bff";
                suggestion = "股價處於空頭，但 RSI 僅 " + Math.round(rsi) + "，已進入極度超賣區，隨時可能出現強烈反彈。";
                riskLevel = "中";
            } else {
                status = "空頭趨勢";
                color = "#28a745";
                suggestion = "趨勢偏空，RSI 尚無轉強訊號，建議觀望。";
                riskLevel = "高";
            }
        } else {
            status = "盤整中";
            color = "#6c757d";
            suggestion = "趨勢不明朗，建議等待量能突破或 RSI 回到 50 以上。";
            riskLevel = "低";
        }

        String rsiStatus;
        if (rsi >= 70) {
            rsiStatus = "⚠️ RSI 過熱 (超買)，小心回檔風險。";
        } else if (rsi <= 30) {
            rsiStatus = "💎 RSI 低檔 (超賣)，具備反彈潛力。";
        } else {
            rsiStatus = "RSI 數值正常。";
        }

        // 3. 回傳封裝（含前端相容欄位）
        response.put("symbol", cleanSymbol);
        response.put("price", round2(currentPrice));
        response.put("ma5", round2(ma5));
        response.put("ma20", round2(ma20));
        response.put("rsi", round2(rsi));
        response.put("rsiStatus", rsiStatus);
        response.put("status", status);
        response.put("color", color);
        response.put("suggestion", suggestion);
        response.put("riskLevel", riskLevel);
        response.put("icon", "📊");
        response.put("action", suggestion);

        return response;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    @GetMapping("/{symbol}/name")
    public Map<String, String> getStockName(@PathVariable("symbol") String symbol) {
        String cleanSymbol = normalizeSymbol(symbol);
        Map<String, String> response = new HashMap<>();
        response.put("symbol", cleanSymbol);
        response.put("name", twseService.fetchStockName(cleanSymbol));
        return response;
    }


    @GetMapping("/{symbol}/history")
    public List<StockDataPoint> getHistory(@PathVariable("symbol") String symbol,
                                           @RequestParam(name = "limit", defaultValue = "100") int limit) {
        String cleanSymbol = normalizeSymbol(symbol);
        int safeLimit = limit > 0 ? limit : DEFAULT_HISTORY_DAYS;
        ensureSymbolData(cleanSymbol, safeLimit);
        return DatabaseManager.getRecentHistory(cleanSymbol, safeLimit);
    }

    @GetMapping("/{symbol}/bollinger")
    public Map<String, Object> getBollingerData(@PathVariable("symbol") String symbol,
                                                @RequestParam(name = "limit", defaultValue = "100") int limit) {
        String cleanSymbol = normalizeSymbol(symbol);
        int safeLimit = limit > 0 ? limit : DEFAULT_HISTORY_DAYS;
        ensureSymbolData(cleanSymbol, Math.max(20, safeLimit));

        List<StockDataPoint> history = DatabaseManager.getRecentHistory(cleanSymbol, safeLimit);
        Map<String, Double> bands = DatabaseManager.calculateBollinger(cleanSymbol);

        Map<String, Object> response = new HashMap<>();
        response.put("history", history);
        response.put("bands", bands);
        return response;
    }

    @GetMapping("/compare")
    public Map<String, List<StockDataPoint>> compareStocks(@RequestParam(name = "symbols") String symbols,
                                                            @RequestParam(name = "limit", defaultValue = "100") int limit) {
        Map<String, List<StockDataPoint>> response = new HashMap<>();
        String[] symbolArray = symbols.split(",");
        int safeLimit = limit > 0 ? limit : DEFAULT_HISTORY_DAYS;

        for (String s : symbolArray) {
            String cleanSymbol = s.trim();
            ensureSymbolData(cleanSymbol, safeLimit);
            List<StockDataPoint> data = DatabaseManager.getRecentHistory(cleanSymbol, safeLimit);
            response.put(cleanSymbol, data);
        }
        return response;
    }

    @GetMapping("/{symbol}/backtest")
    public BacktestResult runBacktest(@PathVariable("symbol") String symbol,
                                      @RequestParam(name = "lookbackDays", defaultValue = "250") int lookbackDays,
                                      @RequestParam(name = "capital", defaultValue = "100000") double capital) {
        String cleanSymbol = normalizeSymbol(symbol);
        int safeLookback = lookbackDays > 0 ? lookbackDays : 250;
        double safeCapital = capital > 0 ? capital : 100_000.0;
        ensureFullHistoryData(cleanSymbol, safeLookback + 80);
        return BacktestEngine.runDiagnosisBacktest(cleanSymbol, safeLookback, safeCapital);
    }

    @GetMapping("/{symbol}/full-history")
    public List<StockDataPoint> getFullHistory(@PathVariable("symbol") String symbol,
                                               @RequestParam(name = "limit", defaultValue = "120") int limit) {
        String cleanSymbol = normalizeSymbol(symbol);
        int safeLimit = limit > 0 ? limit : DEFAULT_HISTORY_DAYS;
        ensureFullHistoryData(cleanSymbol, safeLimit);
        return DatabaseManager.getFullHistory(cleanSymbol, safeLimit);
    }

    @GetMapping("/scan")
    public List<ScannedResult> scanMarket(@RequestParam(name = "top", defaultValue = "10") int top,
                                          @RequestParam(name = "assetType", required = false) String assetType,
                                          @RequestParam(name = "market", required = false) String market) {
        // 最多允許 2000 筆（全市場約 1700 支有資料的股票）
        int safeTop = Math.max(1, Math.min(top, 2000));
        String safeAssetType = assetType == null || assetType.isBlank() ? null : assetType.trim().toUpperCase();
        String safeMarket = market == null || market.isBlank() ? null : market.trim().toUpperCase();
        return scannerService.scanAllStocks(safeTop, safeAssetType, safeMarket);
    }

    @PostMapping("/scan/tasks")
    public Map<String, Object> startScanTask(@RequestParam(name = "top", defaultValue = "10") int top,
                                             @RequestParam(name = "assetType", required = false) String assetType,
                                             @RequestParam(name = "market", required = false) String market) {
        int safeTop = Math.max(1, Math.min(top, 2000));
        String safeAssetType = assetType == null || assetType.isBlank() ? null : assetType.trim().toUpperCase();
        String safeMarket = market == null || market.isBlank() ? null : market.trim().toUpperCase();
        String taskId = scanTaskService.startScan(safeTop, safeAssetType, safeMarket);
        return Map.of(
                "taskId", taskId,
                "status", "QUEUED",
                "message", "掃描工作已排入佇列"
        );
    }

    @GetMapping("/scan/tasks/{taskId}")
    public ResponseEntity<ScanTaskService.ScanTaskSnapshot> getScanTask(@PathVariable("taskId") String taskId) {
        ScanTaskService.ScanTaskSnapshot snapshot = scanTaskService.getTask(taskId);
        return snapshot == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(snapshot);
    }

    // ── 全市場批次補抓 ─────────────────────────────────────────────────────

    /**
     * POST /api/stocks/bulk-fetch?months=3
     * 啟動非同步批次補抓所有股票最近 months 個月的歷史資料。
     */
    @PostMapping("/bulk-fetch")
    public Map<String, Object> startBulkFetch(
            @RequestParam(name = "months", defaultValue = "3") int months) {
        Map<String, Object> result = new HashMap<>();
        int safeMonths = Math.max(1, Math.min(months, 24));
        boolean started = bulkFetchService.startBulkBackfill(safeMonths);
        result.put("started", started);
        result.put("months", safeMonths);
        result.put("message", started
                ? "全市場批次補抓已啟動（背景執行），請定期查詢 /api/stocks/bulk-fetch/status 追蹤進度。"
                : "已有補抓作業在執行中，請稍後再試。");
        return result;
    }

    /**
     * GET /api/stocks/bulk-fetch/status
     * 查詢批次補抓目前進度。
     */
    @GetMapping("/bulk-fetch/status")
    public Map<String, Object> getBulkFetchStatus() {
        BulkFetchService.ProgressSnapshot snap = bulkFetchService.getProgress();
        Map<String, Object> result = new HashMap<>();
        result.put("status", snap.status());
        result.put("total", snap.total());
        result.put("completed", snap.completed());
        result.put("savedRows", snap.savedRows());
        int pct = snap.total() <= 0 ? 0
                : (int) Math.round(snap.completed() * 100.0 / snap.total());
        result.put("progressPct", pct);
        return result;
    }

    /**
     * POST /api/stocks/today-data
     * 立即用 STOCK_DAY_ALL + TPEX API 更新今日全市場收盤資料（不抓歷史）。
     */
    @PostMapping("/today-data")
    public Map<String, Object> fetchTodayData() {
        Map<String, Object> result = new HashMap<>();
        int saved = bulkFetchService.bulkSaveTodayData();
        result.put("savedRows", saved);
        result.put("message", "今日全市場資料更新完成，共寫入 " + saved + " 筆。");
        return result;
    }

    // ── 備份管理 ─────────────────────────────────────────────────────────────

    /**
     * POST /api/stocks/{symbol}/backup
     * 觸發單檔股票年度歷史資料備份（背景非同步執行）。
     */
    @PostMapping("/{symbol}/backup")
    public Map<String, Object> startSingleStockBackup(@PathVariable("symbol") String symbol) {
        return scheduledService.startSingleStockBackup(normalizeSymbol(symbol));
    }

    /**
     * GET /api/stocks/{symbol}/backup/status
     * 查詢單檔備份目前狀態。
     */
    @GetMapping("/{symbol}/backup/status")
    public Map<String, Object> getSingleStockBackupStatus(@PathVariable("symbol") String symbol) {
        return scheduledService.getSingleStockBackupStatus(normalizeSymbol(symbol));
    }

    /**
     * POST /api/stocks/backup/full
     * 手動觸發完整備份（背景非同步執行）。
     */
    @PostMapping("/backup/full")
    public Map<String, Object> startFullBackup() {
        return scheduledService.startFullBackup();
    }

    /**
     * GET /api/stocks/backup/full/status
     * 查詢完整備份目前狀態。
     */
    @GetMapping("/backup/full/status")
    public Map<String, Object> getFullBackupStatus() {
        return scheduledService.getFullBackupStatus();
    }

    @GetMapping("/backtest-report")
    public Map<String, Object> getBacktestReport() {
        Map<String, Object> result = new HashMap<>();

        // 嘗試找最近 14~21 天內有掃描紀錄的日期
        LocalDate today = LocalDate.now();
        LocalDate targetDate = null;
        List<ScanHistory> historyList = null;

        for (int daysBack = 14; daysBack <= 21; daysBack++) {
            LocalDate candidate = today.minusDays(daysBack);
            List<ScanHistory> found = historyRepository.findByScanDateAndScoreGreaterThan(candidate, 59);
            if (!found.isEmpty()) {
                targetDate = candidate;
                historyList = found;
                break;
            }
        }

        if (targetDate == null || historyList == null || historyList.isEmpty()) {
            result.put("avgReturn", 0.0);
            result.put("sampleSize", 0);
            result.put("targetDate", "--");
            result.put("note", "尚無兩週前的掃描紀錄，請先執行市場掃描。");
            return result;
        }

        // 計算每支股票的報酬率
        List<Double> returns = new ArrayList<>();
        for (ScanHistory h : historyList) {
            double priceAtScan = h.getPriceAtScan();
            if (priceAtScan <= 0) continue;
            double currentPrice = DatabaseManager.getLatestPrice(h.getSymbol());
            if (currentPrice <= 0) continue;
            double ret = (currentPrice - priceAtScan) / priceAtScan * 100.0;
            returns.add(ret);
        }

        double avg = returns.isEmpty() ? 0.0
                : returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        avg = Math.round(avg * 100.0) / 100.0;

        result.put("avgReturn", avg);
        result.put("sampleSize", returns.size());
        result.put("targetDate", targetDate.toString());
        return result;
    }

    @GetMapping("/runtime-info")
    public Map<String, String> getRuntimeInfo() {
        Map<String, String> info = new HashMap<>();
        String dataDir = AppRuntime.resolveDataDirectory().toString();
        info.put("dataDir", dataDir);
        info.put("dbFile", AppRuntime.resolveDataDirectory().resolve("stockdb.mv.db").toString());
        return info;
    }

    @GetMapping("/trust-locked")
    public List<Map<String, Object>> getTrustLocked(@RequestParam(name = "top", defaultValue = "5") int top) {
        int safeTop = top > 0 ? Math.min(top, 20) : 5; // 限制最多 20 筆
        return scannerService.getTopTrustStocks(safeTop);
    }

    @GetMapping("/{symbol}/macd")
    public List<MACDResult> getMacdSeries(@PathVariable("symbol") String symbol,
                                          @RequestParam(name = "limit", defaultValue = "60") int limit) {
        String clean = normalizeSymbol(symbol);
        ensureSymbolData(clean, Math.max(40, limit + 10));
        return IndicatorCalculator.calculateMACDSeries(clean, limit);
    }

    @GetMapping("/{symbol}/kd")
    public ResponseEntity<KDInfo> getKdDataEndpoint(@PathVariable("symbol") String symbol,
                                    @RequestParam(name = "limit", defaultValue = "120") int limit) {
        String clean = normalizeSymbol(symbol);
        if (clean.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            // KD 計算需要額外緩衝 (rsvPeriod=9)；確保至少取得 limit+40 天歷史資料
            ensureSymbolData(clean, Math.max(60, limit + 40));
            KDInfo info = getKDData(clean, limit);
            info.setLatestClosePrice(DatabaseManager.getLatestPrice(clean));
            return ResponseEntity.ok(info);
        } catch (Exception e) {
            System.err.println("❌ KD 端點例外 [" + clean + "]: " + e.getMessage());
            KDInfo errorInfo = new KDInfo();
            errorInfo.setKdSeries(Collections.emptyList());
            errorInfo.setDiagnosis("KD 資料暫時無法取得，請稍後再試：" + e.getMessage());
            errorInfo.setLatestClosePrice(DatabaseManager.getLatestPrice(clean));
            return ResponseEntity.ok(errorInfo);
        }
    }

    @GetMapping("/{symbol}/volatility")
    public Map<String, Object> getVolatility(@PathVariable("symbol") String symbol) {
        String clean = normalizeSymbol(symbol);
        ensureSymbolData(clean, 80);
        Map<String, Object> resp = new HashMap<>();

        List<StockDataPoint> full = DatabaseManager.getFullHistory(clean, 80);
        if (full.size() < 20) {
            resp.put("bbwScore", 0);
            resp.put("currentBBW", 0);
            resp.put("status", "資料不足");
            return resp;
        }

        // compute bbw history using 20-day windows
        List<Double> bbwHistory = new ArrayList<>();
        for (int i = 19; i < full.size(); i++) {
            double sum = 0.0;
            for (int j = i - 19; j <= i; j++) sum += full.get(j).c > 0 ? full.get(j).c : full.get(j).price;
            double ma20 = sum / 20.0;
            double sdSum = 0.0;
            for (int j = i - 19; j <= i; j++) {
                double close = full.get(j).c > 0 ? full.get(j).c : full.get(j).price;
                sdSum += Math.pow(close - ma20, 2);
            }
            double sd = Math.sqrt(sdSum / 20.0);
            double bbw = ma20 == 0.0 ? 0.0 : ((ma20 + 2 * sd) - (ma20 - 2 * sd)) / ma20 * 100.0;
            bbwHistory.add(bbw);
        }
        VolatilityAnalyzer analyzer = new VolatilityAnalyzer();
        int bbwScore = analyzer.calculateBBWScore(bbwHistory);
        double currentBBW = bbwHistory.isEmpty() ? 0.0 : bbwHistory.get(bbwHistory.size() - 1);

        String status = bbwScore >= 15 ? "擠壓中/可能爆發" : (bbwScore >= 8 ? "動能釋放期" : "波動穩定");

        resp.put("bbwScore", bbwScore);
        resp.put("currentBBW", Math.round(currentBBW * 100.0) / 100.0);
        resp.put("status", status);
        return resp;
    }

    @GetMapping("/{symbol}/bbw")
    public List<Map<String, Object>> getBbwData(@PathVariable("symbol") String symbol,
                                                @RequestParam(name = "limit", defaultValue = "1") int limit) {
        String clean = normalizeSymbol(symbol);
        if (clean.isBlank()) {
            return Collections.emptyList();
        }
        ensureSymbolData(clean, 85);
        List<Double> bbwSeries = IndicatorCalculator.calculateBBWSeries(clean, 60);
        if (bbwSeries.isEmpty()) {
            return Collections.emptyList();
        }
        VolatilityAnalyzer analyzer = new VolatilityAnalyzer();
        int score = analyzer.calculateBBWScore(bbwSeries);
        double currentBBW = bbwSeries.get(bbwSeries.size() - 1);
        Map<String, Object> entry = new HashMap<>();
        entry.put("score", score);
        entry.put("bbw", Math.round(currentBBW * 10000.0) / 10000.0);
        return List.of(entry);
    }

    @GetMapping("/{symbol}/radar")
    public ResponseEntity<RadarScoreResult> getRadarScores(@PathVariable("symbol") String symbol) {
        String clean = normalizeSymbol(symbol);
        if (clean.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            ensureSymbolData(clean, 120);
            RadarScoreResult result = radarService.calculateRadarScores(clean);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            System.err.println("❌ 雷達端點例外 [" + clean + "]: " + e.getMessage());
            RadarScoreResult errorResult = new RadarScoreResult();
            errorResult.conclusion = "雷達資料暫時無法取得，請稍後再試：" + e.getMessage();
            errorResult.label = "ERROR";
            return ResponseEntity.ok(errorResult);
        }
    }

    @GetMapping("/{symbol}/revenue")
    public Map<String, Object> getRevenueHistory(@PathVariable("symbol") String symbol,
                                                 @RequestParam(name = "months", defaultValue = "12") int months) {
        String clean = normalizeSymbol(symbol);
        int safeMonths = Math.max(1, Math.min(months, 120));
        int backfilled = fundamentalService.backfillRevenueHistory(clean, safeMonths);
        List<RevenueData> history = fundamentalService.getRevenueHistory(clean, safeMonths);
        int revenueScore = fundamentalService.calculateRevenueScore(history);
        RevenueData latest = history.isEmpty() ? null : history.get(history.size() - 1);

        Map<String, Object> result = new HashMap<>();
        result.put("symbol", clean);
        result.put("months", safeMonths);
        result.put("history", history);
        result.put("revenueScore", revenueScore);
        result.put("fundamentalScore", scoreFundamental(history, revenueScore));
        result.put("sampleSize", history.size());
        result.put("backfilledCount", backfilled);
        result.put("latest", latest);
        return result;
    }

    @GetMapping("/{symbol}/revenue/history")
    public Map<String, Object> getRevenueHistoryApi(@PathVariable("symbol") String symbol,
                                                    @RequestParam(name = "months", defaultValue = "24") int months) {
        return getRevenueHistory(symbol, months);
    }

    @GetMapping("/{symbol}/institutional")
    public List<InstitutionalTrade> getInstitutional(@PathVariable("symbol") String symbol,
                                                     @RequestParam(name = "limit", defaultValue = "60") int limit) {
        String clean = normalizeSymbol(symbol);
        int safeLimit = limit > 0 ? limit : 60;
        return institutionalService.getRecentInstitutionalTrades(clean, safeLimit);
    }

    @GetMapping("/{symbol}/day-trading")
    public Map<String, Object> getDayTrading(@PathVariable("symbol") String symbol,
                                             @RequestParam(name = "days", defaultValue = "60") int days) {
        String clean = normalizeSymbol(symbol);
        int safeDays = Math.max(1, Math.min(days, 180));
        List<FinMindDayTradingData> history = dayTradingService.getDayTradingHistory(clean, safeDays);

        FinMindDayTradingData latest = history.isEmpty() ? null : history.get(history.size() - 1);
        Map<String, Object> result = new HashMap<>();
        result.put("symbol", clean);
        result.put("days", safeDays);
        result.put("history", history);
        result.put("sampleSize", history.size());
        result.put("latest", latest);
        result.put("dataset", FinMindDataset.DAY_TRADE.getDatasetName());
        return result;
    }

    @GetMapping("/{symbol}/big-holders")
    public List<FinMindShareholdingData> getBigHolders(@PathVariable("symbol") String symbol,
                                                       @RequestParam(name = "weeks", defaultValue = "16") int weeks) {
        String clean = normalizeSymbol(symbol);
        int safeWeeks = Math.max(4, Math.min(weeks, 52));
        return institutionalService.getLargeHolderShareholdingHistory(clean, safeWeeks);
    }

    @GetMapping("/{symbol}/big-holder-diagnosis")
    public Map<String, Object> getBigHolderDiagnosis(@PathVariable("symbol") String symbol,
                                                     @RequestParam(name = "weeks", defaultValue = "16") int weeks) {
        String clean = normalizeSymbol(symbol);
        int safeWeeks = Math.max(4, Math.min(weeks, 52));

        List<FinMindShareholdingData> history = institutionalService.getLargeHolderShareholdingHistory(clean, safeWeeks);
        int score = institutionalService.calculateBigHolderScore(history);

        Map<String, Object> response = new HashMap<>();
        response.put("symbol", clean);
        response.put("score", score);
        response.put("sampleSize", history.size());

        if (history.size() < 3) {
            response.put("signal", "insufficient");
            response.put("conclusion", "大戶週資料不足，暫無法判定集中趨勢。");
            return response;
        }

        FinMindShareholdingData w0 = history.get(history.size() - 1);
        FinMindShareholdingData w1 = history.get(history.size() - 2);
        FinMindShareholdingData w2 = history.get(history.size() - 3);
        boolean holdersUpTrend = w0.getPercentage() > w1.getPercentage() && w1.getPercentage() > w2.getPercentage();

        ensureSymbolData(clean, 35);
        List<StockDataPoint> prices = DatabaseManager.getRecentHistory(clean, 35);
        boolean priceWeak = false;
        if (prices.size() >= 10) {
            double latest = prices.get(prices.size() - 1).price;
            double baseline = prices.get(Math.max(0, prices.size() - 10)).price;
            priceWeak = latest <= baseline;
        }

        boolean advancedDivergence = holdersUpTrend && priceWeak;
        response.put("holdersUpTrend", holdersUpTrend);
        response.put("priceWeak", priceWeak);
        response.put("advancedDivergence", advancedDivergence);
        response.put("latestPercentage", w0.getPercentage());
        response.put("previousPercentage", w1.getPercentage());

        String conclusion;
        if (advancedDivergence) {
            conclusion = "🔥 籌碼進階背離：股價盤整/走弱，但千張大戶持股比連續走高，留意噴發前兆。";
        } else if (holdersUpTrend) {
            conclusion = "📈 大戶持股連續上升，籌碼仍在集中，偏多觀察。";
        } else if (w0.getPercentage() > w1.getPercentage()) {
            conclusion = "🟡 本週大戶持股回升，但趨勢仍需更多週資料確認。";
        } else {
            conclusion = "⚖️ 大戶持股集中趨勢未明，建議搭配價量與法人資料確認。";
        }

        response.put("signal", advancedDivergence ? "burst-warning" : (holdersUpTrend ? "concentrating" : "neutral"));
        response.put("conclusion", conclusion);
        return response;
    }

    @GetMapping("/{symbol}/advices")
    public List<String> getStockAdvices(@PathVariable("symbol") String symbol) {
        String cleanSymbol = normalizeSymbol(symbol);
        List<String> advices = new ArrayList<>();

        // 取得基本面與技術面診斷
        Map<String, Object> diagnosis = getIntegratedDiagnosis(cleanSymbol);
        advices.add(diagnosis.get("suggestion").toString());

        // 取得投信資料
        List<InstitutionalTrade> institutionalTrades = getInstitutionalTrades(cleanSymbol, 5);
        if (!institutionalTrades.isEmpty()) {
            long lastNetBuy = institutionalTrades.get(0).getTotalNetBuy();
            if (lastNetBuy > 0) {
                advices.add("投信近期買超，顯示機構看好。");
            } else {
                advices.add("投信近期賣超，需注意籌碼風險。");
            }
        }

        // 取得 MACD 與 KD 指標
        List<MACDResult> macdSeries = getMACDSeries(cleanSymbol, 60);
        KDInfo kdInfo = getKDData(cleanSymbol, 60);
        double lastMacd = macdSeries.isEmpty() ? 0 : macdSeries.get(macdSeries.size() - 1).dif;
        double lastSignal = macdSeries.isEmpty() ? 0 : macdSeries.get(macdSeries.size() - 1).dea;
        List<KDResult> kdSeries = kdInfo.getKdSeries() == null ? java.util.Collections.emptyList() : kdInfo.getKdSeries();
        double lastK = kdSeries.isEmpty() ? 0 : kdSeries.get(kdSeries.size() - 1).getK();
        double lastD = kdSeries.isEmpty() ? 0 : kdSeries.get(kdSeries.size() - 1).getD();

        if (lastMacd > lastSignal) {
            advices.add("MACD 黃金交叉，短期內可能上漲。");
        } else {
            advices.add("MACD 死亡交叉，需提防回檔風險。");
        }

        KDAnalyzer kdAnalyzer = new KDAnalyzer();
        boolean lowPassivation = kdAnalyzer.isLowPassivation(kdSeries.stream().map(KDResult::getK).collect(java.util.stream.Collectors.toList()));
        boolean bottomDivergence = kdAnalyzer.isBottomDivergence(kdSeries.stream().map(KDResult::getD).collect(java.util.stream.Collectors.toList()),
                                                                  DatabaseManager.getRecentHistory(cleanSymbol, 60).stream().map(dp -> dp.c).collect(java.util.stream.Collectors.toList()));
        if (lowPassivation) {
            advices.add("KD 指標低檔鈍化，可能出現反彈機會。");
        }
        if (bottomDivergence) {
            advices.add("KD 指標底部背離，顯示潛在反轉訊號。");
        }

        return advices;
    }

    private List<InstitutionalTrade> getInstitutionalTrades(String symbol, int days) {
        return institutionalService.getRecentInstitutionalTrades(symbol, days);
    }

    private List<MACDResult> getMACDSeries(String symbol, int limit) {
        return IndicatorCalculator.calculateMACDSeries(symbol, limit);
    }

    private KDInfo getKDData(String symbol, int limit) {
        List<KDResult> kdSeries = IndicatorCalculator.calculateKD(symbol, limit);
        KDInfo info = new KDInfo();
        info.setKdSeries(kdSeries);
        info.setAdvancedScore(0);
        info.setAdvancedSignals(java.util.Collections.emptyList());
        info.setExtendedIndicators(java.util.Collections.emptyMap());
        info.setLatestClosePrice(DatabaseManager.getLatestPrice(symbol));

        if (kdSeries == null || kdSeries.isEmpty()) {
            info.setLowPassivation(false);
            info.setBottomDivergence(false);
            info.setDiagnosis("暫無 KD 資料（歷史資料不足，請稍後再試）");
            return info;
        }

        KDAnalyzer kdAnalyzer = new KDAnalyzer();
        List<Double> kValues = kdSeries.stream().map(KDResult::getK).collect(java.util.stream.Collectors.toList());
        List<Double> dValues = kdSeries.stream().map(KDResult::getD).collect(java.util.stream.Collectors.toList());
        List<Double> closes = DatabaseManager.getRecentHistory(symbol, limit).stream()
                .map(dp -> dp.c > 0 ? dp.c : dp.price)
                .collect(java.util.stream.Collectors.toList());

        List<KdData> advancedSeries = buildKdAdvancedSeries(kdSeries, closes);
        KdAdvancedService.AnalysisResult advanced = kdAdvancedService.analyze(advancedSeries);
        info.setAdvancedScore(advanced.getScore());
        info.setAdvancedSignals(advanced.getSignals());
        enrichKdExtendedIndicators(symbol, info, closes);

        boolean lowPassivation = kdAnalyzer.isLowPassivation(kValues);
        boolean bottomDivergence = kdAnalyzer.isBottomDivergence(dValues, closes);
        info.setLowPassivation(lowPassivation);
        info.setBottomDivergence(bottomDivergence);

        if (bottomDivergence) {
            info.setDiagnosis("KD 指標底部背離，顯示潛在反轉訊號。");
        } else if (lowPassivation) {
            info.setDiagnosis("KD 指標低檔鈍化，可能出現反彈機會。");
        } else if (advanced.getScore() >= 10) {
            info.setDiagnosis("KD 高級動能偏多：" + String.join("；", advanced.getSignals()));
        } else if (advanced.getScore() <= -10) {
            info.setDiagnosis("KD 高級動能偏空：" + String.join("；", advanced.getSignals()));
        } else {
            info.setDiagnosis(buildKdAnalystDiagnosis(info));
        }

        return info;
    }

    private void enrichKdExtendedIndicators(String symbol, KDInfo info, List<Double> closes) {
        if (info == null || symbol == null || symbol.isBlank()) {
            return;
        }

        double rsi = IndicatorCalculator.calculateRSI(symbol, 14);
        MACDResult macd = IndicatorCalculator.calculateMACD(symbol);
        double atr = IndicatorCalculator.calculateATR(symbol, 14);
        double obvStrength = IndicatorCalculator.calculateOBV(symbol, 20);
        double mfi = IndicatorCalculator.calculateMFI(symbol, 14);
        double cmf = IndicatorCalculator.calculateCMF(symbol, 20);
        double cci = IndicatorCalculator.calculateCCI(symbol, 20);
        double williamsR = IndicatorCalculator.calculateWilliamsR(symbol, 14);
        double aroonOscillator = IndicatorCalculator.calculateAroonOscillator(symbol, 25);
        int superTrendDirection = IndicatorCalculator.calculateSuperTrendDirection(symbol, 10, 3.0);
        double donchianPosition = IndicatorCalculator.calculateDonchianPosition(symbol, 20);

        double latestClose = (closes == null || closes.isEmpty()) ? 0.0 : closes.get(closes.size() - 1);
        double atrPct = (latestClose > 0 && atr > 0) ? (atr / latestClose) * 100.0 : 0.0;
        double obvStrengthPct = obvStrength * 100.0;

        Map<String, Object> indicators = new LinkedHashMap<>();
        indicators.put("rsi", round2(rsi));
        indicators.put("macdDif", round2(macd.dif));
        indicators.put("macdDea", round2(macd.dea));
        indicators.put("macdHistogram", round2(macd.histogram));
        indicators.put("atr", round2(atr));
        indicators.put("atrPct", round2(atrPct));
        indicators.put("obvStrength", round2(obvStrengthPct));
        indicators.put("mfi", round2(mfi));
        indicators.put("cmf", round2(cmf));
        indicators.put("cci", round2(cci));
        indicators.put("williamsR", round2(williamsR));
        indicators.put("aroonOscillator", round2(aroonOscillator));
        indicators.put("superTrendDirection", superTrendDirection);
        indicators.put("superTrendLabel", superTrendDirection > 0 ? "多頭" : superTrendDirection < 0 ? "空頭" : "中性");
        indicators.put("donchianPositionPct", round2(donchianPosition * 100.0));
        info.setExtendedIndicators(indicators);

        int extraScore = 0;
        List<String> extraSignals = new ArrayList<>();

        if (rsi <= 30) {
            extraScore += 4;
            extraSignals.add("💎 RSI 低檔（<=30）：可能進入反彈區");
        } else if (rsi >= 75) {
            extraScore -= 4;
            extraSignals.add("⚠️ RSI 過熱（>=75）：短線回檔風險增加");
        }

        if (macd.dif > macd.dea) {
            extraScore += 3;
            extraSignals.add("📈 MACD 多頭結構（DIF > DEA）");
        } else if (macd.dif < macd.dea) {
            extraScore -= 3;
            extraSignals.add("📉 MACD 空頭結構（DIF < DEA）");
        }

        if (superTrendDirection > 0) {
            extraScore += 5;
            extraSignals.add("🟢 SuperTrend 多頭趨勢");
        } else if (superTrendDirection < 0) {
            extraScore -= 5;
            extraSignals.add("🔴 SuperTrend 空頭趨勢");
        }

        if (donchianPosition >= 0.90) {
            extraScore += 3;
            extraSignals.add("🚀 Donchian 靠近上軌：突破力道強");
        } else if (donchianPosition <= 0.10) {
            extraScore -= 3;
            extraSignals.add("🧊 Donchian 靠近下軌：走勢偏弱");
        }

        if (cmf >= 0.08) {
            extraScore += 3;
            extraSignals.add("💰 CMF 正流入（>=0.08）：資金偏多");
        } else if (cmf <= -0.08) {
            extraScore -= 3;
            extraSignals.add("💸 CMF 負流出（<=-0.08）：資金偏空");
        }

        if (mfi <= 20) {
            extraScore += 2;
            extraSignals.add("📊 MFI 低檔（<=20）：留意超賣反彈");
        } else if (mfi >= 80) {
            extraScore -= 2;
            extraSignals.add("📊 MFI 高檔（>=80）：留意超買回落");
        }

        if (cci >= 100) {
            extraScore += 2;
            extraSignals.add("⚡ CCI 強勢（>=100）");
        } else if (cci <= -100) {
            extraScore -= 2;
            extraSignals.add("⚠️ CCI 弱勢（<=-100）");
        }

        if (williamsR <= -80) {
            extraScore += 1;
            extraSignals.add("🔄 Williams %R 低檔（<=-80）：短彈機率提升");
        } else if (williamsR >= -20) {
            extraScore -= 1;
            extraSignals.add("🛑 Williams %R 高檔（>=-20）：追價風險提高");
        }

        if (aroonOscillator >= 35) {
            extraScore += 2;
            extraSignals.add("📶 Aroon Oscillator 偏強（>=35）");
        } else if (aroonOscillator <= -35) {
            extraScore -= 2;
            extraSignals.add("📶 Aroon Oscillator 偏弱（<=-35）");
        }

        if (atrPct >= 6.0) {
            extraScore -= 2;
            extraSignals.add("🌪️ ATR 波動偏大（ATR/Close >= 6%）：風險提高");
        } else if (atrPct > 0 && atrPct <= 2.5) {
            extraScore += 1;
            extraSignals.add("🛡️ ATR 波動穩定（ATR/Close <= 2.5%）");
        }

        if (obvStrengthPct >= 12) {
            extraScore += 2;
            extraSignals.add("📦 OBV 量價結構偏多");
        } else if (obvStrengthPct <= -12) {
            extraScore -= 2;
            extraSignals.add("📦 OBV 量價結構偏空");
        }

        List<String> mergedSignals = new ArrayList<>();
        if (info.getAdvancedSignals() != null) {
            mergedSignals.addAll(info.getAdvancedSignals());
        }
        mergedSignals.addAll(extraSignals);
        info.setAdvancedSignals(mergedSignals);
        info.setAdvancedScore(info.getAdvancedScore() + extraScore);
    }

    private String buildKdAnalystDiagnosis(KDInfo info) {
        if (info == null || info.getKdSeries() == null || info.getKdSeries().isEmpty()) {
            return "KD 資料不足，暫時無法形成完整判讀。";
        }

        List<KDResult> series = info.getKdSeries();
        KDResult latest = series.get(series.size() - 1);
        Map<String, Object> indicators = info.getExtendedIndicators() == null
                ? Collections.emptyMap()
                : info.getExtendedIndicators();

        double score = info.getAdvancedScore();
        double k = latest.getK();
        double d = latest.getD();
        double spread = k - d;
        double rsi = asDouble(indicators.get("rsi"));
        double mfi = asDouble(indicators.get("mfi"));
        double macdDif = asDouble(indicators.get("macdDif"));
        double macdDea = asDouble(indicators.get("macdDea"));
        double macdHistogram = asDouble(indicators.get("macdHistogram"));
        double cmf = asDouble(indicators.get("cmf"));
        double atrPct = asDouble(indicators.get("atrPct"));
        double obvStrength = asDouble(indicators.get("obvStrength"));
        double aroonOscillator = asDouble(indicators.get("aroonOscillator"));
        String superTrendLabel = String.valueOf(indicators.getOrDefault("superTrendLabel", "中性"));

        boolean consolidation = Math.abs(spread) <= 8.0 && k >= 40.0 && k <= 60.0 && Math.abs(score) <= 8.0;
        boolean bullishBias = "多頭".equals(superTrendLabel) || cmf >= 0.08 || macdDif >= macdDea;
        boolean bearishBias = "空頭".equals(superTrendLabel) || cmf <= -0.08 || macdDif < macdDea;

        StringBuilder text = new StringBuilder();
        if (consolidation) {
            text.append("目前來看，市場正處於盤整收斂區，KD 在 ")
                    .append(format2(k))
                    .append(" / ")
                    .append(format2(d))
                    .append(" 附近來回拉扯，代表買賣雙方都還在觀望，短線還沒有明確方向。");
        } else if (k > d) {
            text.append("KD 結構仍偏多，K 值站在 D 值之上，趨勢尚未被破壞。");
        } else {
            text.append("KD 結構偏弱，K 值仍受制於 D 值，反彈先視為技術性修復。");
        }

        text.append(" RSI ")
                .append(formatIndicator(rsi))
                .append("、MFI ")
                .append(formatIndicator(mfi))
                .append("，");
        if ((isNeutralZone(rsi, 45, 55) || Double.isNaN(rsi)) && (isNeutralZone(mfi, 45, 55) || Double.isNaN(mfi))) {
            text.append("動能位階落在中性區。");
        } else if ((rsi >= 75 && !Double.isNaN(rsi)) || (mfi >= 80 && !Double.isNaN(mfi))) {
            text.append("指標已進入偏熱區，追價必須同步設好停利與停損。");
        } else if ((rsi <= 30 && !Double.isNaN(rsi)) || (mfi <= 20 && !Double.isNaN(mfi))) {
            text.append("動能偏低檔，若後續量能與交叉同步轉強，才適合分批觀察。");
        } else {
            text.append("位階介於中性與偏強之間，等待下一次方向表態。");
        }

        if (!Double.isNaN(macdDif) && !Double.isNaN(macdDea)) {
            text.append(" MACD ")
                    .append(macdDif >= macdDea ? "維持多方結構" : "尚未翻多")
                    .append("，柱狀體")
                    .append(Double.isNaN(macdHistogram) ? "未提供" : (macdHistogram >= 0 ? "偏正" : "偏負"))
                    .append("，顯示趨勢仍在整理確認中。");
        }

        if (bullishBias && !bearishBias) {
            text.append(" SuperTrend 與資金流仍偏多，整理較像蓄勢而不是出貨。");
        } else if (bearishBias && !bullishBias) {
            text.append(" 趨勢與資金訊號偏弱，盤整更像是弱勢反彈或換手修復。");
        } else {
            text.append(" 趨勢與資金訊號尚未完全同步，建議等突破箱頂或跌破箱底再定方向。");
        }

        if (!Double.isNaN(atrPct) && atrPct > 0) {
            text.append(" ATR/收盤約 ")
                    .append(format2(atrPct))
                    .append("%，")
                    .append(atrPct >= 6.0 ? "波動偏大，倉位要縮。" : "波動相對收斂，適合耐心等確認。");
        }

        if (!Double.isNaN(obvStrength)) {
            text.append(" OBV 強度約 ")
                    .append(format2(obvStrength))
                    .append("%，")
                    .append(obvStrength >= 12 ? "量價結構偏多。" : obvStrength <= -12 ? "量價結構偏空。" : "量能方向仍未定。");
        }

        if (!Double.isNaN(aroonOscillator)) {
            text.append(" Aroon Oscillator 約 ")
                    .append(format2(aroonOscillator))
                    .append("，")
                    .append(aroonOscillator >= 35 ? "趨勢動能仍在多方。" : aroonOscillator <= -35 ? "趨勢仍偏空方。" : "趨勢仍在均衡帶。");
        }

        text.append(" 操作上，建議以箱體上緣突破或下緣失守作為確認點，不宜在盤整內提前重押。");
        return text.toString();
    }

    private boolean isNeutralZone(double value, double low, double high) {
        return !Double.isNaN(value) && value >= low && value <= high;
    }

    private String formatIndicator(double value) {
        return Double.isNaN(value) ? "無資料" : format2(value);
    }

    private String format2(double value) {
        return String.format(Locale.TAIWAN, "%.2f", value);
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.NaN;
    }

    private List<KdData> buildKdAdvancedSeries(List<KDResult> kdSeries, List<Double> closes) {
        if (kdSeries == null || closes == null || kdSeries.isEmpty() || closes.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        int size = Math.min(kdSeries.size(), closes.size());
        int kdStart = kdSeries.size() - size;
        int closeStart = closes.size() - size;

        List<KdData> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            KDResult kd = kdSeries.get(kdStart + i);
            double close = closes.get(closeStart + i);
            result.add(new KdData("", close, kd.getK(), kd.getD()));
        }
        return result;
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim();
    }

    private int scoreFundamental(List<RevenueData> history, int revenueScore) {
        RevenueData latest = history.isEmpty() ? null : history.get(history.size() - 1);
        double latestYoy = latest == null ? 0.0 : latest.getYoy();
        double latestMom = latest == null ? 0.0 : latest.getMom();
        ScoreEngine scoreEngine = new ScoreEngine();
        return scoreEngine.scoreFundamental(revenueScore, latestYoy, latestMom);
    }

    private void ensureSymbolData(String symbol, int requiredDays) {
        if (symbol.isBlank()) {
            return;
        }

        int safeRequiredDays = Math.max(1, requiredDays);

        List<StockDataPoint> existing = DatabaseManager.getRecentHistory(symbol, safeRequiredDays);
        if (isHistoryReady(existing, safeRequiredDays)) {
            return;
        }

        LocalDate cursor = LocalDate.now().withDayOfMonth(1);
        int consecutiveEmptyMonths = 0;

        // Backfill month-by-month until enough rows and usable volume are available or max attempts reached.
        for (int i = 0; i < MAX_BACKFILL_MONTHS; i++) {
            List<StockDataPoint> current = DatabaseManager.getRecentHistory(symbol, safeRequiredDays);
            if (isHistoryReady(current, safeRequiredDays)) {
                break;
            }

            String dateStr = String.format("%04d%02d01", cursor.getYear(), cursor.getMonthValue());
            List<String[]> monthData = twseService.fetchMonthlyData(symbol, dateStr);
            if (monthData == null || monthData.isEmpty()) {
                consecutiveEmptyMonths++;
                // If a code keeps returning empty months, treat it as unavailable and stop early.
                if (consecutiveEmptyMonths >= MAX_CONSECUTIVE_EMPTY_MONTHS
                        && DatabaseManager.getRecentHistory(symbol, 1).isEmpty()) {
                    break;
                }
            } else {
                consecutiveEmptyMonths = 0;
                DatabaseManager.saveAllToDatabase(symbol, monthData);
            }
            cursor = cursor.minusMonths(1);
        }
    }

    private boolean isHistoryReady(List<StockDataPoint> data, int requiredDays) {
        if (data.size() < requiredDays) {
            return false;
        }

        long nonZeroVolumes = data.stream().filter(point -> point.volume > 0).count();
        long minRequiredNonZero = Math.max(1L, (long) Math.ceil(data.size() * 0.9));
        return nonZeroVolumes >= minRequiredNonZero;
    }

    private void ensureFullHistoryData(String symbol, int requiredDays) {
        if (symbol.isBlank()) {
            return;
        }

        int safeRequiredDays = Math.max(1, requiredDays);
        ensureSymbolData(symbol, safeRequiredDays);

        LocalDate cursor = LocalDate.now().withDayOfMonth(1);
        for (int i = 0; i < MAX_BACKFILL_MONTHS; i++) {
            List<StockDataPoint> full = DatabaseManager.getFullHistory(symbol, safeRequiredDays);
            if (isFullHistoryReady(full, safeRequiredDays)) {
                break;
            }

            String dateStr = String.format("%04d%02d01", cursor.getYear(), cursor.getMonthValue());
            List<String[]> monthData = twseService.fetchMonthlyData(symbol, dateStr);
            if (monthData != null && !monthData.isEmpty()) {
                DatabaseManager.saveAllToDatabase(symbol, monthData);
            }
            cursor = cursor.minusMonths(1);
        }
    }

    private boolean isFullHistoryReady(List<StockDataPoint> data, int requiredDays) {
        if (data.size() < requiredDays) {
            return false;
        }

        long validOhlc = data.stream().filter(point -> point.o > 0 && point.h > 0 && point.l > 0 && point.c > 0).count();
        long minRequiredValid = Math.max(1L, (long) Math.ceil(data.size() * 0.9));
        return validOhlc >= minRequiredValid;
    }
}
