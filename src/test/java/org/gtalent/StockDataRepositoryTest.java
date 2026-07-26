package org.gtalent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockDataRepositoryTest {
    private StockDataRepository repository;

    @BeforeEach
    void setUp() {
        String url = "jdbc:h2:mem:stock-repository-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        DatabaseSchemaInitializer.ConnectionProvider schemaConnections =
                () -> DriverManager.getConnection(url, "sa", "");
        DatabaseSchemaInitializer.initialize(schemaConnections);
        repository = new StockDataRepository(() -> DriverManager.getConnection(url, "sa", ""));
    }

    @Test
    void savesTwseRowsAndReturnsTradingDaysInAscendingOrder() {
        repository.saveMonthlyHistory("2330", List.of(
                new String[]{"115/07/17", "1,000", "", "100", "110", "95", "108"},
                new String[]{"115/07/18", "2,000", "", "108", "112", "105", "110"},
                new String[]{"115/07/20", "3,000", "", "110", "115", "109", "114"}
        ));

        List<StockDataPoint> history = repository.getFullHistory("2330", 2);

        assertEquals(2, history.size());
        assertEquals("2026-07-17", history.get(0).date);
        assertEquals("2026-07-20", history.get(1).date);
        assertEquals(114.0, history.get(1).c);
        assertEquals(3_000L, history.get(1).volume);
    }

    @Test
    void recentHistoryFillsMissingOhlcWithClosePrice() {
        repository.saveMonthlyHistory("0050", Collections.singletonList(
                new String[]{"115/07/20", "50.5"}
        ));

        StockDataPoint point = repository.getRecentHistory("0050", 1).get(0);

        assertEquals(50.5, point.o);
        assertEquals(50.5, point.h);
        assertEquals(50.5, point.l);
        assertEquals(50.5, point.c);
    }

    @Test
    void databaseFailuresAreNotConvertedToEmptyHistory() {
        StockDataRepository failingRepository =
                new StockDataRepository(() -> {
                    throw new java.sql.SQLException("database unavailable");
                });

        assertThrows(IllegalStateException.class,
                () -> failingRepository.getRecentHistory("2330", 5));
    }

    @Test
    void savesSimpleCloseAndCalculatesLatestPriceAndMovingAverage() {
        assertTrue(repository.saveSimpleData("2317", "2026-07-20", "100"));
        assertTrue(repository.saveSimpleData("2317", "2026-07-21", "110"));
        assertFalse(repository.saveSimpleData("2317", "not-a-date", "120"));

        assertEquals(110.0, repository.getLatestPrice("2317"));
        assertEquals(105.0, repository.calculateMA("2317", 2));
        assertEquals(0L, repository.getLatestVolume("2317"));
    }

    @Test
    void bulkOhlcvUsesFallbackValuesAndCalculatesVolumeAverage() {
        repository.saveBulkOhlcvRow(
                "0050", "2026-07-20", "", "invalid", null, "200", "1000");
        repository.saveBulkOhlcvRow(
                "0050", "2026-07-21", "201", "203", "199", "202", "3000");

        List<StockDataPoint> history = repository.getFullHistory("0050", 2);

        assertEquals(2, history.size());
        assertEquals(200.0, history.get(0).o);
        assertEquals(200.0, history.get(0).h);
        assertEquals(200.0, history.get(0).l);
        assertEquals(3_000L, repository.getLatestVolume("0050"));
        assertEquals(2_000L, repository.calculateVolumeMA("0050", 2));
    }

    @Test
    void bollingerRequiresTwentyPricesAndReturnsPopulationBands() {
        assertNull(repository.calculateBollinger("2882"));
        LocalDate start = LocalDate.of(2026, 6, 1);
        for (int index = 0; index < 20; index++) {
            String price = Integer.toString(index + 1);
            repository.saveBulkOhlcvRow(
                    "2882", start.plusDays(index).toString(),
                    price, price, price, price, "100");
        }

        Map<String, Double> bands = repository.calculateBollinger("2882");

        assertNotNull(bands);
        assertEquals(10.5, bands.get("mid"), 0.000001);
        assertEquals(22.032562594670797, bands.get("upper"), 0.000001);
        assertEquals(-1.0325625946707966, bands.get("lower"), 0.000001);
    }
}
