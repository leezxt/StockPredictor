package org.gtalent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ScheduledService {

    private static final String STOCK_CODE = "2330";
    private static final int BATCH_YEAR = 2026;
    private static final int BATCH_MONTH = 1;
    private static final String INITIAL_BACKUP_META_KEY = "initial_backup_done";
    private static final String FULL_BACKUP_KEY = "__FULL__";

    public enum BackupStatus { IDLE, RUNNING, DONE, FAILED }

    public record BackupState(BackupStatus status, String symbol, String message, long startedAt) {}

    private final ConcurrentHashMap<String, BackupState> backupStates = new ConcurrentHashMap<>();

    private final ScannerService scannerService;
    private final MarketBreadthService marketBreadthService;
    private final FundamentalService fundamentalService;
    private final InstitutionalService institutionalService;
    private final BulkFetchService bulkFetchService;
    private final TwseService twseService;
    private final StockUniverseRepository stockUniverseRepository;
    private final AppMetaRepository appMetaRepository;

    @Value("${app.scheduling.enabled:true}")
    private boolean schedulingEnabled;

    public ScheduledService(ScannerService scannerService,
                            MarketBreadthService marketBreadthService,
                            FundamentalService fundamentalService,
                            InstitutionalService institutionalService,
                            BulkFetchService bulkFetchService,
                            TwseService twseService,
                            StockUniverseRepository stockUniverseRepository,
                            AppMetaRepository appMetaRepository) {
        this.scannerService = scannerService;
        this.marketBreadthService = marketBreadthService;
        this.fundamentalService = fundamentalService;
        this.institutionalService = institutionalService;
        this.bulkFetchService = bulkFetchService;
        this.twseService = twseService;
        this.stockUniverseRepository = stockUniverseRepository;
        this.appMetaRepository = appMetaRepository;
    }

    /**
     * 每天凌晨 1:30 同步一次全市場股票池，供掃描/廣度/批次任務使用。
     */
    @Scheduled(cron = "0 30 1 * * *")
    public void dailyStockUniverseRefresh() {
        if (!schedulingEnabled) return;
        System.out.println("[ScheduledService] 開始同步全市場股票池...");
        try {
            int saved = twseService.syncMarketUniverse();
            int total = stockUniverseRepository.getStockUniverseCount();
            System.out.println("[ScheduledService] 股票池同步完成：saved=" + saved + ", total=" + total);
        } catch (Exception e) {
            System.err.println("[ScheduledService] 股票池同步失敗: " + e.getMessage());
        }
    }

    /**
     * 每天凌晨 2:00 使用 TWSE STOCK_DAY_ALL + TPEX daily quotes
     * 一次性更新全市場所有上市/上櫃股票當日收盤資料。
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void dailyDataFetch() {
        if (!schedulingEnabled) return;
        System.out.println("[ScheduledService] 開始全市場每日收盤資料更新...");
        try {
            int saved = bulkFetchService.bulkSaveTodayData();
            System.out.printf("[ScheduledService] 全市場每日更新完成：共寫入 %d 筆%n", saved);
        } catch (Exception e) {
            System.err.println("[ScheduledService] 全市場每日更新失敗: " + e.getMessage());
        }
    }

    /**
     * 每天凌晨 3:00 批次補抓所有股票的法人資料
     * 與掃描主流程分離，避免使用者觸發掃描時逾時
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void dailyInstitutionalRefresh() {
        if (!schedulingEnabled) return;
        System.out.println("[ScheduledService] 開始批次補抓法人資料...");
        try {
            scannerService.refreshInstitutionalData();
            System.out.println("[ScheduledService] 法人資料補抓完成！");
        } catch (Exception e) {
            System.err.println("[ScheduledService] 法人資料補抓失敗: " + e.getMessage());
        }
    }

    /**
     * 每天凌晨 3:30 產生一筆市場廣度快照
     */
    @Scheduled(cron = "0 30 3 * * *")
    public void dailyMarketBreadthSnapshot() {
        if (!schedulingEnabled) return;
        System.out.println("[ScheduledService] 開始更新 MARKET_BREADTH 快照...");
        try {
            MarketBreadthResult result = marketBreadthService.calculateMarketBreadth();
            System.out.println("[ScheduledService] MARKET_BREADTH 更新完成：breadth=" + result.getBreadth());
        } catch (Exception e) {
            System.err.println("[ScheduledService] MARKET_BREADTH 更新失敗: " + e.getMessage());
        }
    }

    /**
     * 每天早上 9:10 嘗試刷新一次最新月營收公告
     */
    @Scheduled(cron = "0 10 9 * * *")
    public void dailyRevenueRefresh() {
        if (!schedulingEnabled) return;
        System.out.println("[ScheduledService] 開始更新 MONTHLY_REVENUE 快照...");
        try {
            int saved = fundamentalService.refreshLatestRevenueData();
            System.out.println("[ScheduledService] MONTHLY_REVENUE 更新完成：saved=" + saved);
        } catch (Exception e) {
            System.err.println("[ScheduledService] MONTHLY_REVENUE 更新失敗: " + e.getMessage());
        }
    }

    /**
     * 台灣集保股權分散每週五盤後更新，因此固定每週五 17:00 批次抓一次。
     */
    @Scheduled(cron = "0 0 17 * * FRI", zone = "Asia/Taipei")
    public void weeklyLargeHolderRefresh() {
        if (!schedulingEnabled) return;
        System.out.println("[ScheduledService] 開始每週股權分散更新...");
        try {
            int saved = institutionalService.refreshWeeklyLargeHolderShareholding();
            System.out.println("[ScheduledService] 每週股權分散更新完成：savedRows=" + saved);
        } catch (Exception e) {
            System.err.println("[ScheduledService] 每週股權分散更新失敗: " + e.getMessage());
        }
    }

    /**
     * 應用啟動時執行：僅首次啟動會進行完整年度備份，之後跳過。
     */
    public void onStartup() {
        if (!schedulingEnabled) {
            System.out.println("[ScheduledService] 排程已停用，跳過首次啟動自動備份");
            return;
        }
        if (appMetaRepository.isMetaFlagSet(INITIAL_BACKUP_META_KEY)) {
            System.out.println("[ScheduledService] 非首次啟動，跳過自動備份（可透過 UI 手動觸發）");
            return;
        }
        System.out.println("[ScheduledService] 首次啟動 - 開始年度數據備份...");
        try {
            int universeSaved = twseService.syncMarketUniverse();
            twseService.fetchYearlyData(STOCK_CODE, BATCH_YEAR, BATCH_MONTH);
            marketBreadthService.calculateMarketBreadth();
            fundamentalService.refreshLatestRevenueData();
            appMetaRepository.setMetaFlag(INITIAL_BACKUP_META_KEY, "true");
            System.out.println("[ScheduledService] 年度備份完成！股票池 saved=" + universeSaved + ", total=" + stockUniverseRepository.getStockUniverseCount());
        } catch (Exception e) {
            System.err.println("[ScheduledService] 年度備份失敗: " + e.getMessage());
        }
    }

    /**
     * 觸發單檔股票的年度資料備份（背景非同步執行）。
     */
    public Map<String, Object> startSingleStockBackup(String symbol) {
        Map<String, Object> result = new HashMap<>();
        BackupState existing = backupStates.get(symbol);
        if (existing != null && existing.status() == BackupStatus.RUNNING) {
            result.put("started", false);
            result.put("status", "RUNNING");
            result.put("message", "備份中，請稍候...");
            return result;
        }
        backupStates.put(symbol, new BackupState(BackupStatus.RUNNING, symbol, "備份中...", System.currentTimeMillis()));
        new Thread(() -> {
            try {
                twseService.fetchYearlyData(symbol, BATCH_YEAR, BATCH_MONTH);
                backupStates.put(symbol, new BackupState(BackupStatus.DONE, symbol, "備份完成", System.currentTimeMillis()));
                System.out.println("[ScheduledService] 單檔備份完成: " + symbol);
            } catch (Exception e) {
                backupStates.put(symbol, new BackupState(BackupStatus.FAILED, symbol, "備份失敗: " + e.getMessage(), System.currentTimeMillis()));
                System.err.println("[ScheduledService] 單檔備份失敗 " + symbol + ": " + e.getMessage());
            }
        }, "backup-" + symbol).start();
        result.put("started", true);
        result.put("status", "RUNNING");
        result.put("message", "單檔備份已啟動（背景執行）: " + symbol);
        return result;
    }

    /**
     * 查詢單檔備份狀態。
     */
    public Map<String, Object> getSingleStockBackupStatus(String symbol) {
        BackupState state = backupStates.getOrDefault(symbol,
                new BackupState(BackupStatus.IDLE, symbol, "尚未備份", 0));
        Map<String, Object> result = new HashMap<>();
        result.put("symbol", symbol);
        result.put("status", state.status().name());
        result.put("message", state.message());
        result.put("startedAt", state.startedAt());
        return result;
    }

    /**
     * 觸發完整備份（背景非同步執行），可手動重新執行首次備份流程。
     */
    public Map<String, Object> startFullBackup() {
        Map<String, Object> result = new HashMap<>();
        BackupState existing = backupStates.get(FULL_BACKUP_KEY);
        if (existing != null && existing.status() == BackupStatus.RUNNING) {
            result.put("started", false);
            result.put("status", "RUNNING");
            result.put("message", "完整備份已在執行中...");
            return result;
        }
        backupStates.put(FULL_BACKUP_KEY, new BackupState(BackupStatus.RUNNING, FULL_BACKUP_KEY, "完整備份中...", System.currentTimeMillis()));
        new Thread(() -> {
            try {
                int universeSaved = twseService.syncMarketUniverse();
                twseService.fetchYearlyData(STOCK_CODE, BATCH_YEAR, BATCH_MONTH);
                marketBreadthService.calculateMarketBreadth();
                fundamentalService.refreshLatestRevenueData();
                appMetaRepository.setMetaFlag(INITIAL_BACKUP_META_KEY, "true");
                String msg = "完整備份完成！股票池 saved=" + universeSaved + ", total=" + stockUniverseRepository.getStockUniverseCount();
                backupStates.put(FULL_BACKUP_KEY, new BackupState(BackupStatus.DONE, FULL_BACKUP_KEY, msg, System.currentTimeMillis()));
                System.out.println("[ScheduledService] " + msg);
            } catch (Exception e) {
                backupStates.put(FULL_BACKUP_KEY, new BackupState(BackupStatus.FAILED, FULL_BACKUP_KEY, "完整備份失敗: " + e.getMessage(), System.currentTimeMillis()));
                System.err.println("[ScheduledService] 完整備份失敗: " + e.getMessage());
            }
        }, "backup-full").start();
        result.put("started", true);
        result.put("status", "RUNNING");
        result.put("message", "完整備份已啟動（背景執行）");
        return result;
    }

    /**
     * 查詢完整備份狀態。
     */
    public Map<String, Object> getFullBackupStatus() {
        BackupState state = backupStates.getOrDefault(FULL_BACKUP_KEY,
                new BackupState(BackupStatus.IDLE, FULL_BACKUP_KEY, "尚未執行備份", 0));
        Map<String, Object> result = new HashMap<>();
        result.put("status", state.status().name());
        result.put("message", state.message());
        result.put("startedAt", state.startedAt());
        return result;
    }
}
