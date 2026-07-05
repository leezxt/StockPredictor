package org.gtalent;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FinMindShareholdingData {
    private String date;

    @JsonProperty("stock_id")
    private String stockId;

    @JsonAlias({"HoldingFactor", "holding_factor", "holding_factor_level"})
    private int holdingFactor;

    @JsonAlias({"shareholder_count", "people"})
    private int shareholderCount;

    private long shares;

    private double percentage;

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getStockId() {
        return stockId;
    }

    public void setStockId(String stockId) {
        this.stockId = stockId;
    }

    public int getHoldingFactor() {
        return holdingFactor;
    }

    public void setHoldingFactor(int holdingFactor) {
        this.holdingFactor = holdingFactor;
    }

    public int getShareholderCount() {
        return shareholderCount;
    }

    public void setShareholderCount(int shareholderCount) {
        this.shareholderCount = shareholderCount;
    }

    public long getShares() {
        return shares;
    }

    public void setShares(long shares) {
        this.shares = shares;
    }

    public double getPercentage() {
        return percentage;
    }

    public void setPercentage(double percentage) {
        this.percentage = percentage;
    }
}

