package org.gtalent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockDiagnosticsControllerTest {
    @Mock
    private ScanHistoryRepository historyRepository;

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private DatabaseMetaData metadata;

    @Mock
    private StockDataRepository stockDataRepository;

    @Test
    void shouldReturnEmptyBacktestReportWhenNoHistoryExists() {
        when(historyRepository.findByScanDateAndScoreGreaterThan(any(LocalDate.class), eq(59)))
                .thenReturn(Collections.emptyList());
        StockDiagnosticsController controller =
                new StockDiagnosticsController(historyRepository, dataSource, stockDataRepository);

        Map<String, Object> result = controller.getBacktestReport();

        assertEquals(0.0, result.get("avgReturn"));
        assertEquals(0, result.get("sampleSize"));
        assertEquals("--", result.get("targetDate"));
        assertEquals("尚無兩週前的掃描紀錄，請先執行市場掃描。", result.get("note"));
    }

    @Test
    void shouldCalculateBacktestReportUsingFirstAvailableDate() {
        LocalDate targetDate = LocalDate.now().minusDays(14);
        ScanHistory first = new ScanHistory("2330", 80, 100.0, 50.0);
        ScanHistory second = new ScanHistory("2317", 70, 200.0, 50.0);
        when(historyRepository.findByScanDateAndScoreGreaterThan(targetDate, 59))
                .thenReturn(List.of(first, second));
        when(stockDataRepository.getLatestPrice("2330")).thenReturn(110.0);
        when(stockDataRepository.getLatestPrice("2317")).thenReturn(180.0);
        StockDiagnosticsController controller =
                new StockDiagnosticsController(historyRepository, dataSource, stockDataRepository);

        Map<String, Object> result = controller.getBacktestReport();

        assertEquals(0.0, result.get("avgReturn"));
        assertEquals(2, result.get("sampleSize"));
        assertEquals(targetDate.toString(), result.get("targetDate"));
    }

    @Test
    void shouldReportPostgresqlWithoutH2FilePaths() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(metadata.getURL()).thenReturn("jdbc:postgresql://localhost:15432/stockpredictor");
        StockDiagnosticsController controller =
                new StockDiagnosticsController(historyRepository, dataSource, stockDataRepository);

        Map<String, String> result = controller.getRuntimeInfo();

        assertEquals("PostgreSQL", result.get("database"));
        assertEquals("jdbc:postgresql://localhost:15432/stockpredictor", result.get("jdbcUrl"));
        assertFalse(result.containsKey("dataDir"));
        assertFalse(result.containsKey("dbFile"));
    }
}
