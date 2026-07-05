package org.gtalent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "SCAN_HISTORY")
public class ScanHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String symbol;

    @Column(name = "scan_date", nullable = false)
    private LocalDate scanDate;

    @Column(nullable = false)
    private Integer score;

    @Column(name = "price_at_scan", nullable = false)
    private Double priceAtScan;

    @Column(name = "rsi_at_scan")
    private Double rsiAtScan;

    public ScanHistory() {
        // JPA required
    }

    public ScanHistory(String symbol, Integer score, Double priceAtScan, Double rsiAtScan) {
        this.symbol = symbol;
        this.scanDate = LocalDate.now();
        this.score = score;
        this.priceAtScan = priceAtScan;
        this.rsiAtScan = rsiAtScan;
    }

    public Long getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public LocalDate getScanDate() {
        return scanDate;
    }

    public void setScanDate(LocalDate scanDate) {
        this.scanDate = scanDate;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public Double getPriceAtScan() {
        return priceAtScan;
    }

    public void setPriceAtScan(Double priceAtScan) {
        this.priceAtScan = priceAtScan;
    }

    public Double getRsiAtScan() {
        return rsiAtScan;
    }

    public void setRsiAtScan(Double rsiAtScan) {
        this.rsiAtScan = rsiAtScan;
    }
}

