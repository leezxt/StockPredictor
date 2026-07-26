package org.gtalent;

public class StockAnalysis {
    public String symbol;
    public double ma5;
    public double ma20;
    public String advice;

    public StockAnalysis(String symbol, double ma5, double ma20) {
        this.symbol = symbol;
        this.ma5 = ma5;
        this.ma20 = ma20;
        this.advice = (ma5 > ma20) ? "黃金交叉：看多" : "趨勢偏空";
    }
}
