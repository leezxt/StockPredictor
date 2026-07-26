package org.gtalent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StockUniverseRepositoryTest {
    private StockUniverseRepository repository;
    private String url;

    @BeforeEach
    void setUp() {
        url = "jdbc:h2:mem:universe-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        DatabaseSchemaInitializer.initialize(() -> DriverManager.getConnection(url, "sa", ""));
        repository = new StockUniverseRepository(() -> DriverManager.getConnection(url, "sa", ""));
    }

    @Test
    void savesUpdatesAndReadsUniverseEntries() {
        assertEquals(2, repository.saveStockUniverse(List.of(
                new StockUniverseEntry("2330", "台積電", "TWSE", "STOCK", false, true),
                new StockUniverseEntry("0050", "元大台灣50", "TWSE", "ETF", true, true)
        )));
        repository.saveStockUniverse(List.of(
                new StockUniverseEntry("2330", "台積電", "TWSE", "STOCK", false, false)
        ));

        StockUniverseEntry entry = repository.getStockUniverseEntry("2330");

        assertNotNull(entry);
        assertFalse(entry.isActive());
        assertEquals(1, repository.getStockUniverseCount());
        assertEquals(List.of("0050"), repository.getAllSymbols());
    }

    @Test
    void filtersActiveSymbolsCaseInsensitively() {
        repository.saveStockUniverse(List.of(
                new StockUniverseEntry("2330", "台積電", "TWSE", "STOCK", false, true),
                new StockUniverseEntry("6488", "環球晶", "TPEX", "STOCK", false, true),
                new StockUniverseEntry("0050", "元大台灣50", "TWSE", "ETF", true, true)
        ));

        assertEquals(List.of("2330"), repository.getAllSymbols("stock", "twse"));
        assertEquals(List.of("0050"), repository.getAllSymbols(" ETF ", null));
    }

    @Test
    void appliesRecordThresholdAndIncludesBareStockDataSymbols() throws Exception {
        repository.saveStockUniverse(List.of(
                new StockUniverseEntry("2330", "台積電", "TWSE", "STOCK", false, true),
                new StockUniverseEntry("6488", "環球晶", "TPEX", "STOCK", false, false)
        ));
        insertPrices("2330", 3);
        insertPrices("6488", 3);
        insertPrices("9999", 2);

        assertEquals(List.of("2330", "9999"),
                repository.getAllSymbolsWithData(2, null, null));
        assertEquals(List.of("2330"),
                repository.getAllSymbolsWithData(3, "stock", "twse"));
    }

    private void insertPrices(String symbol, int count) throws Exception {
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT INTO STOCK_DATA(symbol, trade_date, close_price) VALUES (?, ?, ?)")) {
            for (int i = 0; i < count; i++) {
                pstmt.setString(1, symbol);
                pstmt.setDate(2, java.sql.Date.valueOf("2026-07-" + (20 + i)));
                pstmt.setDouble(3, 100 + i);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }
    }
}
