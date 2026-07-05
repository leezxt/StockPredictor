package org.gtalent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
public class InstitutionalService {
    private static final Logger logger = Logger.getLogger(InstitutionalService.class.getName());
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    private final TwseService twseService = new TwseService();
    
    @Autowired
    private FinMindClient finMindClient;

    private boolean forceFinMind = false;

    public boolean isForceFinMind() {
        return this.forceFinMind;
    }

    public void setForceFinMind(boolean forceFinMind) {
        this.forceFinMind = forceFinMind;
    }

    /** 計算投信鎖碼綜合評分（連買天數 + 籌碼佔比） */
    public int calculateTrustLockScore(String symbol) {
        TrustStats stats = computeStats(symbol);
        int score = 0;

        if (stats.continuousDays >= 5)      score += 25;
        else if (stats.continuousDays >= 3) score += 10;

        if (stats.lockRatio > 0.15)      score += 20;
        else if (stats.lockRatio > 0.08) score += 10;

        return score;
    }

    /** 回傳投信籌碼佔比（百分比，例如 12.5 表示 12.5%），無法計算則回傳 0 */
    public double getLockRatio(String symbol) {
        return computeStats(symbol).lockRatio * 100.0;
    }

    /** 回傳最新連續買超天數 */
    public int getContinuousBuyDays(String symbol) {
        return computeStats(symbol).continuousDays;
    }

    /**
     * 取得每日籌碼資料 - 自動容錯降級機制
     * 首先嘗試從 TWSE 抓取，失敗時自動切換到 FinMind 備援
     * 
     * @param symbol 股票代號
     * @param date 指定日期（格式："yyyy-MM-dd"）
     * @return 該日期的籌碼資料列表 InstitutionalTrade
     */
    public List<InstitutionalTrade> getDailyChipDataWithFallback(String symbol, String date) {
        List<InstitutionalTrade> result = new ArrayList<>();
        
        // 驗證輸入
        if (symbol == null || symbol.isBlank() || date == null || date.isBlank()) {
            logger.warning("⚠️  無效的輸入: symbol=" + symbol + ", date=" + date);
            return result;
        }

        if (forceFinMind) {
            logger.warning("⚠️  [強制降級] 系統一鍵開關已啟用：強制 FinMind 模式，跳過 TWSE 查詢...");
            try {
                logger.info("🔄 [方案B] 正在嘗試從 FinMind API 抓取籌碼資料 [" + symbol + "] @ " + date);
                List<FinMindChipData> backupData = finMindClient.fetchChipDataBackup(symbol, date);
                if (!backupData.isEmpty()) {
                    logger.info("✅ [方案B成功] FinMind API 返回 " + backupData.size() + " 筆籌碼資料");
                    result = convertFinMindToInternalFormat(backupData);
                    return result;
                }
            } catch (Exception finmindException) {
                logger.severe("🚨 [方案B也失敗] FinMind 備援通道也出現問題！無法取得籌碼資料: " + finmindException.getMessage());
            }
            return result;
        }
        
        try {
            // ✅ A 方案：嘗試從 TWSE 官網直接抓取
            logger.info("🔄 [方案A] 正在嘗試從 TWSE 抓取籌碼資料 [" + symbol + "] @ " + date);
            LocalDate parseDate = LocalDate.parse(date, DATE_FORMATTER);
            InstitutionalTrade twseData = twseService.fetchInstitutionalDataByDate(symbol, parseDate);
            
            if (twseData != null) {
                logger.info("✅ [方案A成功] TWSE 返回籌碼資料: trustBuy=" + twseData.getTrustBuy());
                result.add(twseData);
                return result;
            }
            
            // 如果 TWSE 無資料，嘗試備援
            logger.warning("⚠️  [方案A無資料] TWSE 沒有返回該日期的資料，嘗試備援...");
            throw new Exception("TWSE 無資料");
            
        } catch (Exception twseException) {
            // ❌ A 方案失敗，進入 B 方案：自動啟用 FinMind 備援
            logger.warning("❌ [方案A失敗] TWSE 伺服器報錯、超時或無資料！啟動防爆機制，切換至 FinMind 備援通道...");
            logger.warning("   原因: " + twseException.getClass().getSimpleName() + " - " + twseException.getMessage());
            
            try {
                // 🔄 B 方案：呼叫 FinMind 備援
                logger.info("🔄 [方案B] 正在嘗試從 FinMind API 抓取籌碼資料 [" + symbol + "] @ " + date);
                List<FinMindChipData> backupData = finMindClient.fetchChipDataBackup(symbol, date);

                if (!backupData.isEmpty()) {
                    logger.info("✅ [方案B成功] FinMind API 返回 " + backupData.size() + " 筆籌碼資料");
                    // 🔄 將 FinMind 的資料格式轉換為系統內部統一籌碼格式
                    result = convertFinMindToInternalFormat(backupData);
                    return result;
                } else {
                    logger.warning("⚠️  [方案B無資料] FinMind API 無該日期的資料");
                }
                
            } catch (Exception finmindException) {
                logger.severe("🚨 [方案B也失敗] FinMind 備援通道也出現問題！無法取得籌碼資料");
                logger.severe("   FinMind 錯誤: " + finmindException.getClass().getSimpleName() + " - " + finmindException.getMessage());
            }
        }
        
        // 兩個方案都失敗，回傳空列表
        logger.warning("⚠️  [完全失敗] 無法從任何數據源取得籌碼資料");
        return result;
    }

    /**
     * 將 FinMind 資料格式轉換為系統內部統一籌碼格式
     * FinMind 提供的 buy/sell 數據轉換為 InstitutionalTrade 的三大法人買賣超格式
     * 
     * @param backupData FinMind 返回的籌碼資料列表
     * @return 轉換後的 InstitutionalTrade 列表
     */
    private List<InstitutionalTrade> convertFinMindToInternalFormat(List<FinMindChipData> backupData) {
        List<InstitutionalTrade> results = new ArrayList<>();
        
        if (backupData == null || backupData.isEmpty()) {
            logger.warning("⚠️  備援資料為空，無法轉換");
            return results;
        }
        
        for (FinMindChipData chipData : backupData) {
            try {
                // 從 FinMind 數據中提取信息
                String date = chipData.getDate();
                long buy = chipData.getBuy();
                long sell = chipData.getSell();
                
                // 建立 InstitutionalTrade 物件
                // FinMind 的買賣超數據映射到投信購買超
                InstitutionalTrade trade = new InstitutionalTrade(
                        date,
                        0,           // foreignBuy - FinMind不分類，設為0
                        buy,         // trustBuy - FinMind 的 buy 映射為投信買超
                        0,           // dealerBuy - FinMind不分類，設為0
                        buy + sell   // dailyVolume - 總交易量
                );
                
                results.add(trade);
                logger.fine("📦 轉換數據: date=" + date + ", buy=" + buy + ", sell=" + sell);
                
            } catch (Exception e) {
                logger.warning("⚠️  轉換單筆數據失敗: " + e.getMessage());
            }
        }
        
        logger.info("✅ 成功轉換 " + results.size() + " 筆 FinMind 資料為內部格式");
        return results;
    }

    public List<InstitutionalTrade> getRecentInstitutionalTrades(String symbol, int days) {
        List<InstitutionalTrade> localTrades = DatabaseManager.getRecentInstitutionalTrades(symbol, days);
        if (!localTrades.isEmpty()) {
            return localTrades;
        }

        if (symbol == null || symbol.isBlank() || days <= 0) {
            return localTrades;
        }

        int fetchDays = 30;
        if (days < fetchDays) {
            fetchDays = days;
        }

        if (forceFinMind) {
            logger.warning("⚠️  [強制降級] 系統一鍵開關已啟用：強制 FinMind 模式，跳過 TWSE 查詢...");
            try {
                logger.info("🔄 [方案B] 正在嘗試從 FinMind API 抓取籌碼資料範圍 [" + symbol + "]");
                LocalDate endDate = LocalDate.now();
                LocalDate startDate = endDate.minusDays(fetchDays);
                List<FinMindChipData> finmindData = finMindClient.fetchChipDataByDateRange(
                        symbol, 
                        startDate.format(DATE_FORMATTER), 
                        endDate.format(DATE_FORMATTER)
                );
                if (!finmindData.isEmpty()) {
                    logger.info("✅ FinMind 成功返回 " + finmindData.size() + " 筆資料");
                    List<InstitutionalTrade> convertedTrades = convertFinMindToInternalFormat(finmindData);
                    if (!convertedTrades.isEmpty()) {
                        DatabaseManager.saveInstitutionalTrades(symbol, convertedTrades);
                        List<InstitutionalTrade> refreshed = DatabaseManager.getRecentInstitutionalTrades(symbol, days);
                        return refreshed.isEmpty() ? convertedTrades : refreshed;
                    }
                }
            } catch (Exception e) {
                logger.severe("🚨 FinMind 備援也失敗: " + e.getMessage());
            }
            return localTrades;
        }
        
        try {
            // 首先嘗試 TWSE
            logger.info("🔄 嘗試從 TWSE 抓取 " + symbol + " 的最近 " + fetchDays + " 日資料");
            List<InstitutionalTrade> fetchedTrades = twseService.fetchRecentInstitutionalData(symbol, fetchDays);
            if (fetchedTrades != null && !fetchedTrades.isEmpty()) {
                logger.info("✅ TWSE 成功返回 " + fetchedTrades.size() + " 筆資料");
                DatabaseManager.saveInstitutionalTrades(symbol, fetchedTrades);
                List<InstitutionalTrade> refreshed = DatabaseManager.getRecentInstitutionalTrades(symbol, days);
                return refreshed.isEmpty() ? fetchedTrades : refreshed;
            }
        } catch (Exception e) {
            logger.warning("⚠️  TWSE 抓取失敗: " + e.getMessage());
        }
        
        // TWSE 失敗，進行 FinMind 備援
        try {
            logger.info("🔄 TWSE 失敗，自動啟用 FinMind 備援方案...");
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(fetchDays);
            
            List<FinMindChipData> finmindData = finMindClient.fetchChipDataByDateRange(
                    symbol, 
                    startDate.format(DATE_FORMATTER), 
                    endDate.format(DATE_FORMATTER)
            );
            
            if (!finmindData.isEmpty()) {
                logger.info("✅ FinMind 成功返回 " + finmindData.size() + " 筆資料");
                List<InstitutionalTrade> convertedTrades = convertFinMindToInternalFormat(finmindData);
                if (!convertedTrades.isEmpty()) {
                    DatabaseManager.saveInstitutionalTrades(symbol, convertedTrades);
                    List<InstitutionalTrade> refreshed = DatabaseManager.getRecentInstitutionalTrades(symbol, days);
                    return refreshed.isEmpty() ? convertedTrades : refreshed;
                }
            }
        } catch (Exception e) {
            logger.severe("🚨 FinMind 備援也失敗: " + e.getMessage());
        }

        return localTrades;
    }

    /**
     * 大戶籌碼動態評分：僅評估 HoldingFactor=15（1000 張以上）資料。
     * 規則：
     * 1) 連續兩週上升 +15 分；單週上升 +8 分。
     * 2) 最新週大戶持股比例 > 60% 再加 +5 分。
     */
    public int calculateBigHolderScore(List<FinMindShareholdingData> history) {
        if (history == null || history.isEmpty()) {
            return 0;
        }

        List<FinMindShareholdingData> thousandHolders = history.stream()
                .filter(d -> d != null && d.getHoldingFactor() == 15 && d.getDate() != null && !d.getDate().isBlank())
                .sorted(Comparator.comparing(FinMindShareholdingData::getDate))
                .collect(Collectors.toList());

        if (thousandHolders.size() < 4) {
            return 0;
        }

        int len = thousandHolders.size();
        FinMindShareholdingData w0 = thousandHolders.get(len - 1);
        FinMindShareholdingData w1 = thousandHolders.get(len - 2);
        FinMindShareholdingData w2 = thousandHolders.get(len - 3);

        int score = 0;
        if (w0.getPercentage() > w1.getPercentage() && w1.getPercentage() > w2.getPercentage()) {
            score += 15;
        } else if (w0.getPercentage() > w1.getPercentage()) {
            score += 8;
        }

        if (w0.getPercentage() > 60.0) {
            score += 5;
        }

        return score;
    }

    /**
     * 取得並計算大戶籌碼分數（預設讀最近 8 週，實際按可得資料計分）。
     * 會優先讀本地快取，沒有再打 FinMind API，並把 factor=15 寫回 DB。
     */
    public int calculateBigHolderScore(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return 0;
        }

        List<FinMindShareholdingData> history = getLargeHolderShareholdingHistory(symbol, 8);

        return calculateBigHolderScore(history);
    }

    /**
     * 主力連買強度（0~20）：綜合連買天數、買超強度與最新加速情形。
     */
    public int calculateMainForceContinuousBuyStrength(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return 0;
        }

        List<InstitutionalTrade> history = getRecentInstitutionalTrades(symbol, 12);
        if (history.isEmpty()) {
            return 0;
        }

        int continuousDays = 0;
        long streakNetBuy = 0L;
        long streakVolume = 0L;

        for (int i = history.size() - 1; i >= 0; i--) {
            InstitutionalTrade trade = history.get(i);
            long netBuy = trade.getTotalNetBuy();
            if (netBuy <= 0) {
                break;
            }
            continuousDays++;
            streakNetBuy += netBuy;
            streakVolume += Math.max(0L, trade.getDailyVolume());
        }

        if (continuousDays == 0) {
            return 0;
        }

        int score = 0;
        if (continuousDays >= 8) score += 12;
        else if (continuousDays >= 5) score += 9;
        else if (continuousDays >= 3) score += 6;
        else score += 3;

        double buyIntensity = streakVolume > 0 ? (streakNetBuy / (double) streakVolume) : 0.0;
        if (buyIntensity >= 0.03) score += 8;
        else if (buyIntensity >= 0.015) score += 5;
        else if (buyIntensity >= 0.005) score += 2;

        int recentWindow = Math.min(5, history.size());
        double avgRecentNetBuy = history.subList(history.size() - recentWindow, history.size())
                .stream()
                .mapToLong(InstitutionalTrade::getTotalNetBuy)
                .average()
                .orElse(0.0);
        long latestNetBuy = history.get(history.size() - 1).getTotalNetBuy();
        if (continuousDays >= 3 && latestNetBuy > 0 && latestNetBuy >= avgRecentNetBuy) {
            score += 3;
        }

        return Math.max(0, Math.min(20, score));
    }

    /**
     * 取得大戶(1000張以上)週資料。
     * 先讀本地快取，不足再向 FinMind 抓取並回寫 DB。
     */
    public List<FinMindShareholdingData> getLargeHolderShareholdingHistory(String symbol, int weeks) {
        if (symbol == null || symbol.isBlank() || weeks <= 0) {
            return List.of();
        }

        List<FinMindShareholdingData> history = DatabaseManager.getLargeHolderShareholdingHistory(symbol, weeks);
        if (history.size() >= Math.min(weeks, 4)) {
            return history;
        }

        String startDate = LocalDate.now().minusWeeks(Math.max(weeks + 4, 12)).format(DATE_FORMATTER);
        List<FinMindShareholdingData> fetched = finMindClient.fetchLargeHolderShareholding(symbol, startDate);
        if (!fetched.isEmpty()) {
            DatabaseManager.saveLargeHolderShareholding(symbol, fetched);
            history = DatabaseManager.getLargeHolderShareholdingHistory(symbol, weeks);
            if (history.isEmpty()) {
                history = fetched.stream()
                        .filter(row -> row != null && row.getHoldingFactor() == 15)
                        .sorted(Comparator.comparing(FinMindShareholdingData::getDate))
                        .toList();
            }
        }

        return history;
    }

    /**
     * 每週批次刷新一次千張大戶（HoldingFactor=15）資料。
     * 只抓近幾週，避免重複請求過長歷史造成流量浪費。
     */
    public int refreshWeeklyLargeHolderShareholding() {
        List<String> allSymbols = DatabaseManager.getAllSymbols();
        if (allSymbols == null || allSymbols.isEmpty()) {
            return 0;
        }

        int savedRows = 0;
        String startDate = LocalDate.now().minusWeeks(3).format(DATE_FORMATTER);

        for (String symbol : allSymbols) {
            if (symbol == null || symbol.isBlank()) {
                continue;
            }
            try {
                List<FinMindShareholdingData> fetched = finMindClient.fetchLargeHolderShareholding(symbol, startDate);
                if (fetched.isEmpty()) {
                    continue;
                }
                savedRows += DatabaseManager.saveLargeHolderShareholding(symbol, fetched);
            } catch (Exception e) {
                logger.warning("⚠️  週更股權分散失敗 " + symbol + ": " + e.getMessage());
            }
        }

        logger.info("✅ 每週股權分散更新完成，新增/更新筆數=" + savedRows);
        return savedRows;
    }

    // ── 內部輔助 ──────────────────────────────────────────

    private TrustStats computeStats(String symbol) {
        List<InstitutionalTrade> history = getRecentInstitutionalTrades(symbol, 10);
        int days = 0;
        double totalTrust = 0;
        double totalVol   = 0;

        for (InstitutionalTrade trade : history) {
            if (trade.getTrustBuy() > 0) {
                days++;
                totalTrust += trade.getTrustBuy();
                totalVol   += trade.getDailyVolume();
            } else {
                break;
            }
        }

        double ratio = (totalVol > 0) ? totalTrust / totalVol : 0.0;
        return new TrustStats(days, ratio);
    }

    private record TrustStats(int continuousDays, double lockRatio) {}
}
