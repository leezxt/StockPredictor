package org.gtalent;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FinMindDayTradingData {
    private String date;

    @JsonProperty("stock_id")
    private String stockId;

    @JsonAlias({"BuyAmount", "buy_amount", "day_trading_buy_amount"})
    private long buyAmount;

    @JsonAlias({"SellAmount", "sell_amount", "day_trading_sell_amount"})
    private long sellAmount;

    @JsonAlias({"DayTradingVolume", "day_trading_volume", "day_trading_trading_volume"})
    private long dayTradingVolume;

    @JsonAlias({"DayTradingRate", "day_trading_rate"})
    private double dayTradingRate;

    public FinMindDayTradingData() {
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

    public long getBuyAmount() {
        return buyAmount;
    }

    public void setBuyAmount(long buyAmount) {
        this.buyAmount = buyAmount;
    }

    public long getSellAmount() {
        return sellAmount;
    }

    public void setSellAmount(long sellAmount) {
        this.sellAmount = sellAmount;
    }

    public long getDayTradingVolume() {
        return dayTradingVolume;
    }

    public void setDayTradingVolume(long dayTradingVolume) {
        this.dayTradingVolume = dayTradingVolume;
    }

    public double getDayTradingRate() {
        return dayTradingRate;
    }

    public void setDayTradingRate(double dayTradingRate) {
        this.dayTradingRate = dayTradingRate;
    }

    public long getDayTradingLots() {
        return Math.round(dayTradingVolume / 1000.0);
    }
}

