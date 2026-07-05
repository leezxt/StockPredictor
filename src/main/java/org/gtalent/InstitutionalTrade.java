package org.gtalent;

public class InstitutionalTrade {
    private String date;
    private long foreignBuy;
    private long trustBuy;
    private long dealerBuy;
    private long dailyVolume;

    public InstitutionalTrade() {
    }

    public InstitutionalTrade(String date, long foreignBuy, long trustBuy, long dealerBuy) {
        this(date, foreignBuy, trustBuy, dealerBuy, 0L);
    }

    public InstitutionalTrade(String date, long foreignBuy, long trustBuy, long dealerBuy, long dailyVolume) {
        this.date = date;
        this.foreignBuy = foreignBuy;
        this.trustBuy = trustBuy;
        this.dealerBuy = dealerBuy;
        this.dailyVolume = dailyVolume;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public long getForeignBuy() {
        return foreignBuy;
    }

    public void setForeignBuy(long foreignBuy) {
        this.foreignBuy = foreignBuy;
    }

    public long getTrustBuy() {
        return trustBuy;
    }

    public void setTrustBuy(long trustBuy) {
        this.trustBuy = trustBuy;
    }

    public long getDealerBuy() {
        return dealerBuy;
    }

    public void setDealerBuy(long dealerBuy) {
        this.dealerBuy = dealerBuy;
    }

    public long getDailyVolume() {
        return dailyVolume;
    }

    public void setDailyVolume(long dailyVolume) {
        this.dailyVolume = dailyVolume;
    }

    public long getTotalNetBuy() {
        return foreignBuy + trustBuy + dealerBuy;
    }
}

