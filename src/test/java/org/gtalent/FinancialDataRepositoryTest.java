package org.gtalent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinancialDataRepositoryTest {
    private FinancialDataRepository repository;

    @BeforeEach
    void setUp() {
        String url = "jdbc:h2:mem:financial-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        DatabaseSchemaInitializer.initialize(() -> DriverManager.getConnection(url, "sa", ""));
        repository = new FinancialDataRepository(() -> DriverManager.getConnection(url, "sa", ""));
    }

    @Test
    void returnsLatestQuartersInAscendingOrder() {
        repository.saveFinancialQuarterData("2330", quarter("2025-12-31", 58, 47, 39, 35, 100));
        repository.saveFinancialQuarterData("2330", quarter("2026-03-31", 59, 48, 40, 34, 110));
        repository.saveFinancialQuarterData("2330", quarter("2026-06-30", 60, 49, 41, 33, 120));

        List<FinancialQuarterData> history =
                repository.getFinancialQuarterHistory("2330", 2);

        assertEquals(2, history.size());
        assertEquals("2026-03-31", history.get(0).getQuarterDate());
        assertEquals("2026-06-30", history.get(1).getQuarterDate());
        assertEquals(120L, history.get(1).getContractLiabilities());
    }

    @Test
    void upsertReplacesTheSameQuarter() {
        repository.saveFinancialQuarterData("2330", quarter("2026-06-30", 50, 40, 30, 40, 100));
        repository.saveFinancialQuarterData("2330", quarter("2026-06-30", 60, 50, 40, 30, 200));

        FinancialQuarterData quarter =
                repository.getFinancialQuarterHistory("2330", 1).get(0);

        assertEquals(60.0, quarter.getGrossProfitMargin());
        assertEquals(50.0, quarter.getOperatingProfitMargin());
        assertEquals(40.0, quarter.getNetProfitMargin());
        assertEquals(30, quarter.getInventoryTurnoverDays());
        assertEquals(200L, quarter.getContractLiabilities());
    }

    private static FinancialQuarterData quarter(
            String date, double gross, double operating, double net, int inventoryDays, long liabilities) {
        return new FinancialQuarterData(
                date, gross, operating, net, inventoryDays, liabilities);
    }
}
