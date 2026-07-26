package org.gtalent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketDataRepositoryTest {
    private MarketDataRepository repository;

    @BeforeEach
    void setUp() {
        String url = "jdbc:h2:mem:market-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        DatabaseSchemaInitializer.initialize(() -> DriverManager.getConnection(url, "sa", ""));
        Clock fixedClock = Clock.fixed(Instant.parse("2026-07-23T00:00:00Z"), ZoneOffset.UTC);
        repository = new MarketDataRepository(
                () -> DriverManager.getConnection(url, "sa", ""), fixedClock);
    }

    @Test
    void savesAndUpdatesDailyMarketBreadth() {
        repository.saveDailyMarketBreadth(new MarketBreadthResult(55.5, 100, 55));
        repository.saveDailyMarketBreadth(new MarketBreadthResult(60.0, 120, 72));

        List<MarketBreadthSnapshot> history = repository.getMarketBreadthHistory(10);

        assertEquals(1, history.size());
        assertEquals("2026-07-23", history.get(0).getTradeDate());
        assertEquals(60.0, history.get(0).getBreadth());
        assertEquals(120, history.get(0).getEligibleCount());
        assertEquals(72, history.get(0).getBullishCount());
    }

    @Test
    void returnsLatestRevenueMonthsInAscendingOrder() {
        repository.saveRevenueData("2330", new RevenueData("2026-05", 100, 1, 10));
        repository.saveRevenueData("2330", new RevenueData("2026-06", 120, 20, 15));
        repository.saveRevenueData("2330", new RevenueData("2026-07", 110, -8.3, 5));

        List<RevenueData> history = repository.getRevenueHistory("2330", 2);

        assertEquals(2, history.size());
        assertEquals("2026-06", history.get(0).getYearMonth());
        assertEquals("2026-07", history.get(1).getYearMonth());
        assertEquals(110.0, history.get(1).getRevenue());
    }

    @Test
    void revenueUpsertReplacesSameMonth() {
        repository.saveRevenueData("2330", new RevenueData("2026-07", 100, 1, 2));
        repository.saveRevenueData("2330", new RevenueData("2026-07", 150, 3, 4));

        RevenueData revenue = repository.getRevenueHistory("2330", 1).get(0);

        assertEquals(150.0, revenue.getRevenue());
        assertEquals(3.0, revenue.getMom());
        assertEquals(4.0, revenue.getYoy());
    }
}
