package org.gtalent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InstitutionalDataRepositoryTest {
    private InstitutionalDataRepository repository;
    private String url;

    @BeforeEach
    void setUp() {
        url = "jdbc:h2:mem:institutional-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        DatabaseSchemaInitializer.initialize(() -> DriverManager.getConnection(url, "sa", ""));
        repository = new InstitutionalDataRepository(() -> DriverManager.getConnection(url, "sa", ""));
    }

    @Test
    void savesAndAnalyzesInstitutionalTrades() throws Exception {
        insertVolume("2330", "2026-07-20", 50_000L);
        assertEquals(3, repository.saveInstitutionalTrades("2330", List.of(
                new InstitutionalTrade("2026-07-17", 100, 20, -10),
                new InstitutionalTrade("2026-07-20", 200, 30, 10),
                new InstitutionalTrade("2026-07-21", -50, 40, 5)
        )));

        List<InstitutionalTrade> history = repository.getRecentInstitutionalTrades("2330", 2);

        assertEquals(2, history.size());
        assertEquals("2026-07-20", history.get(0).getDate());
        assertEquals(50_000L, history.get(0).getDailyVolume());
        assertEquals(117.5, repository.getAverageInstitutionalNetBuy("2330", 2));
        assertEquals(3, repository.countConsecutiveTrustBuyDays("2330", 5));
    }

    @Test
    void onlyPersistsLargeHolderFactorFifteen() {
        FinMindShareholdingData largeHolder = shareholding("2026-07-17", 15, 123, 45.6);
        FinMindShareholdingData ignored = shareholding("2026-07-17", 14, 999, 99.9);

        assertEquals(1, repository.saveLargeHolderShareholding("2330", List.of(largeHolder, ignored)));
        List<FinMindShareholdingData> history =
                repository.getLargeHolderShareholdingHistory("2330", 5);

        assertEquals(1, history.size());
        assertEquals(15, history.get(0).getHoldingFactor());
        assertEquals(45.6, history.get(0).getPercentage());
    }

    @Test
    void savesAndReturnsDayTradingInAscendingOrder() {
        assertEquals(2, repository.saveDayTradingData("2330", List.of(
                dayTrading("2026-07-21", 200, 180, 20_000, 12.5),
                dayTrading("2026-07-20", 100, 90, 10_000, 8.5)
        )));

        List<FinMindDayTradingData> history = repository.getDayTradingHistory("2330", 2);

        assertEquals(2, history.size());
        assertEquals("2026-07-20", history.get(0).getDate());
        assertEquals(20_000L, history.get(1).getDayTradingVolume());
    }

    private void insertVolume(String symbol, String date, long volume) throws Exception {
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT INTO STOCK_DATA(symbol, trade_date, close_price, volume) VALUES (?, ?, ?, ?)")) {
            pstmt.setString(1, symbol);
            pstmt.setDate(2, java.sql.Date.valueOf(date));
            pstmt.setDouble(3, 100);
            pstmt.setLong(4, volume);
            pstmt.executeUpdate();
        }
    }

    private static FinMindShareholdingData shareholding(
            String date, int factor, int count, double percentage) {
        FinMindShareholdingData item = new FinMindShareholdingData();
        item.setDate(date);
        item.setHoldingFactor(factor);
        item.setShareholderCount(count);
        item.setShares(1_000);
        item.setPercentage(percentage);
        return item;
    }

    private static FinMindDayTradingData dayTrading(
            String date, long buy, long sell, long volume, double rate) {
        FinMindDayTradingData item = new FinMindDayTradingData();
        item.setDate(date);
        item.setBuyAmount(buy);
        item.setSellAmount(sell);
        item.setDayTradingVolume(volume);
        item.setDayTradingRate(rate);
        return item;
    }
}
