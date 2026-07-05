package org.gtalent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FinMindChipData {
    private String date;

    @JsonProperty("stock_id")
    private String stockId;

    private String name;
    private long buy;
    private long sell;

    public FinMindChipData() {
    }

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getBuy() {
        return buy;
    }

    public void setBuy(long buy) {
        this.buy = buy;
    }

    public long getSell() {
        return sell;
    }

    public void setSell(long sell) {
        this.sell = sell;
    }
}
