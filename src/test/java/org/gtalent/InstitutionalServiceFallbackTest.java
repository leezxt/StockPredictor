package org.gtalent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.util.List;

/**
 * 容錯降級機制測試
 * 用於驗證 TWSE 失敗時自動切換到 FinMind 的功能
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Ignore("外部資料源整合測試：會連線 TWSE/FinMind，預設測試流程不執行，避免 CI/本機測試不穩定。")
public class InstitutionalServiceFallbackTest {

    @Autowired
    private InstitutionalService institutionalService;

    /**
     * 測試1：取得每日籌碼資料（自動容錯）
     */
    @Test
    public void testGetDailyChipDataWithFallback() {
        System.out.println("========================================");
        System.out.println("測試1: 取得每日籌碼資料（自動容錯）");
        System.out.println("========================================");

        String symbol = "2330";
        String date = LocalDate.now().toString();

        List<InstitutionalTrade> result = institutionalService
            .getDailyChipDataWithFallback(symbol, date);

        System.out.println("\n📊 結果摘要:");
        System.out.println("  股票代號: " + symbol);
        System.out.println("  日期: " + date);
        System.out.println("  返回筆數: " + result.size());

        if (!result.isEmpty()) {
            InstitutionalTrade trade = result.get(0);
            System.out.println("\n  ✅ 數據詳情:");
            System.out.println("    - 日期: " + trade.getDate());
            System.out.println("    - 投信買超: " + trade.getTrustBuy());
            System.out.println("    - 外資買超: " + trade.getForeignBuy());
            System.out.println("    - 自營商買超: " + trade.getDealerBuy());
            System.out.println("    - 日成交量: " + trade.getDailyVolume());
            System.out.println("    - 三大法人合計買超: " + trade.getTotalNetBuy());
        } else {
            System.out.println("\n  ⚠️  無法取得數據（可能是市場休市或兩個源都失敗）");
        }
        System.out.println();
    }

    /**
     * 測試2：取得最近N天的籌碼資料（自動容錯）
     */
    @Test
    public void testGetRecentInstitutionalTrades() {
        System.out.println("========================================");
        System.out.println("測試2: 取得最近30天籌碼資料（自動容錯）");
        System.out.println("========================================");

        String symbol = "2330";
        int days = 30;

        List<InstitutionalTrade> result = institutionalService
            .getRecentInstitutionalTrades(symbol, days);

        System.out.println("\n📊 結果摘要:");
        System.out.println("  股票代號: " + symbol);
        System.out.println("  查詢天數: " + days);
        System.out.println("  返回筆數: " + result.size());

        if (!result.isEmpty()) {
            System.out.println("\n  ✅ 前5筆數據:");
            for (int i = 0; i < Math.min(result.size(), 5); i++) {
                InstitutionalTrade trade = result.get(i);
                System.out.printf("    %d. %s: 投信買超=%,d, 外資買超=%,d, 自營商買超=%,d\n",
                    i + 1,
                    trade.getDate(),
                    trade.getTrustBuy(),
                    trade.getForeignBuy(),
                    trade.getDealerBuy()
                );
            }
        }
        System.out.println();
    }

    /**
     * 測試3：計算投信鎖碼評分（使用容錯降級方法）
     */
    @Test
    public void testCalculateTrustLockScore() {
        System.out.println("========================================");
        System.out.println("測試3: 計算投信鎖碼評分");
        System.out.println("========================================");

        String symbol = "2330";

        int score = institutionalService.calculateTrustLockScore(symbol);
        double ratio = institutionalService.getLockRatio(symbol);
        int continuousDays = institutionalService.getContinuousBuyDays(symbol);

        System.out.println("\n📊 評分結果:");
        System.out.println("  股票代號: " + symbol);
        System.out.println("  投信鎖碼評分: " + score + "/100");
        System.out.println("  投信籌碼佔比: " + String.format("%.2f%%", ratio));
        System.out.println("  連續買超天數: " + continuousDays + " 天");

        if (score >= 70) {
            System.out.println("  評級: 🟢 強勢信號");
        } else if (score >= 40) {
            System.out.println("  評級: 🟡 中性信號");
        } else {
            System.out.println("  評級: 🔴 弱勢信號");
        }
        System.out.println();
    }

    /**
     * 測試4：模擬 TWSE 失敗場景
     * （需要手動測試：臨時關閉網路或修改 TWSE URL 使其失敗）
     */
    @Test
    public void testTWSEFailoverScenario() {
        System.out.println("========================================");
        System.out.println("測試4: TWSE 失敗容錯測試");
        System.out.println("========================================");

        System.out.println("\n⚠️  此測試需要手動設置 TWSE 為不可達");
        System.out.println("可通過以下方式模擬:");
        System.out.println("  1. 暫時斷開網路連線");
        System.out.println("  2. 修改 TWSE URL 為無效地址");
        System.out.println("  3. 查看日誌輸出，驗證是否自動切換到 FinMind");

        String symbol = "2330";
        String date = LocalDate.now().toString();

        System.out.println("\n正在執行容錯測試...");
        List<InstitutionalTrade> result = institutionalService
            .getDailyChipDataWithFallback(symbol, date);

        System.out.println("\n結果: " + (result.isEmpty() ? "❌ 無數據" : "✅ 成功取得 " + result.size() + " 筆數據"));
        System.out.println("✅ 如果看到上方日誌中有 '[方案B]' 的內容，表示容錯機制已成功激活");
        System.out.println();
    }

    /**
     * 測試5：多個股票的批量查詢（容錯能力測試）
     */
    @Test
    public void testBatchQueryWithFallback() {
        System.out.println("========================================");
        System.out.println("測試5: 批量查詢多個股票（容錯能力）");
        System.out.println("========================================");

        String[] symbols = {"2330", "2454", "3402", "2412", "2888"};
        String date = LocalDate.now().toString();

        System.out.println("\n正在批量查詢 " + symbols.length + " 個股票...\n");

        int successCount = 0;
        for (String symbol : symbols) {
            List<InstitutionalTrade> result = institutionalService
                .getDailyChipDataWithFallback(symbol, date);

            boolean success = !result.isEmpty();
            successCount += success ? 1 : 0;

            String status = success ? "✅" : "⚠️ ";
            String details = success ?
                String.format("投信買超=%,d", result.get(0).getTrustBuy()) :
                "無數據";

            System.out.printf("%s %s: %s\n", status, symbol, details);
        }

        System.out.println("\n📊 統計:");
        System.out.println("  查詢股票數: " + symbols.length);
        System.out.println("  成功取得數: " + successCount);
        System.out.println("  成功率: " + String.format("%.1f%%", successCount * 100.0 / symbols.length));
        System.out.println();
    }
}

