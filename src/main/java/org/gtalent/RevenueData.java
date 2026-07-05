package org.gtalent;

public class RevenueData {
    private String yearMonth; // 例如 2026-04
    private double revenue;   // 營收金額
    private double mom;       // 月增率 (%)
    private double yoy;       // 年增率 (%)

    public RevenueData() {
    }

    public RevenueData(String yearMonth, double revenue, double mom, double yoy) {
        this.yearMonth = yearMonth;
        this.revenue = revenue;
        this.mom = mom;
        this.yoy = yoy;
    }

    public String getYearMonth() {
        return yearMonth;
    }

    public void setYearMonth(String yearMonth) {
        this.yearMonth = yearMonth;
    }

    public double getRevenue() {
        return revenue;
    }

    public void setRevenue(double revenue) {
        this.revenue = revenue;
    }

    public double getMom() {
        return mom;
    }

    public void setMom(double mom) {
        this.mom = mom;
    }

    public double getYoy() {
        return yoy;
    }

    public void setYoy(double yoy) {
        this.yoy = yoy;
    }
}

