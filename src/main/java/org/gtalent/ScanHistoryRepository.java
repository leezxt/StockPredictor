package org.gtalent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ScanHistoryRepository extends JpaRepository<ScanHistory, Long> {

    // 找出特定日期且高分的歷史記錄
    List<ScanHistory> findByScanDateAndScoreGreaterThan(LocalDate date, int score);

    // 進階：直接在資料庫對比兩週後的漲跌幅
    @Query(value = "SELECT h.symbol, h.score, h.price_at_scan, d.close_price " +
            "FROM SCAN_HISTORY h " +
            "JOIN STOCK_DATA d ON h.symbol = d.symbol " +
            "WHERE h.scan_date = :targetDate AND d.trade_date = CURRENT_DATE()",
            nativeQuery = true)
    List<Object[]> findBacktestComparison(@Param("targetDate") LocalDate targetDate);
}

