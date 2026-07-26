package org.gtalent;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stocks")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"})
public class StockDiagnosticsController {
    private final ScanHistoryRepository historyRepository;
    private final DataSource dataSource;
    private final StockDataRepository stockDataRepository;

    public StockDiagnosticsController(ScanHistoryRepository historyRepository,
                                      DataSource dataSource,
                                      StockDataRepository stockDataRepository) {
        this.historyRepository = historyRepository;
        this.dataSource = dataSource;
        this.stockDataRepository = stockDataRepository;
    }

    @GetMapping("/backtest-report")
    public Map<String, Object> getBacktestReport() {
        Map<String, Object> result = new HashMap<>();
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

        List<Double> returns = new ArrayList<>();
        for (ScanHistory history : historyList) {
            double priceAtScan = history.getPriceAtScan();
            if (priceAtScan <= 0) {
                continue;
            }
            double currentPrice = stockDataRepository.getLatestPrice(history.getSymbol());
            if (currentPrice <= 0) {
                continue;
            }
            returns.add((currentPrice - priceAtScan) / priceAtScan * 100.0);
        }

        double average = returns.isEmpty()
                ? 0.0
                : returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        result.put("avgReturn", Math.round(average * 100.0) / 100.0);
        result.put("sampleSize", returns.size());
        result.put("targetDate", targetDate.toString());
        return result;
    }

    @GetMapping("/runtime-info")
    public Map<String, String> getRuntimeInfo() {
        Map<String, String> info = new HashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            String database = connection.getMetaData().getDatabaseProductName();
            String jdbcUrl = connection.getMetaData().getURL();
            info.put("database", database);
            info.put("jdbcUrl", jdbcUrl);
            if ("H2".equalsIgnoreCase(database)) {
                String dataDir = AppRuntime.resolveDataDirectory().toString();
                info.put("dataDir", dataDir);
                info.put("dbFile",
                        AppRuntime.resolveDataDirectory().resolve("stockdb.mv.db").toString());
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to read database runtime information", exception);
        }
        return info;
    }
}
