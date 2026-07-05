package org.gtalent;

public class StockDataPoint {
    // Candlestick-style short keys
    public String t; // 時間 (X軸)
    public double o; // Open 開盤
    public double h; // High 最高
    public double l; // Low 最低
    public double c; // Close 收盤

    // Backward-compatible fields used by current APIs/UI
    public String date;
    public double price;
    public long volume;

    public StockDataPoint(String date, double price) {
        this(date, price, price, price, price, 0L);
    }

    public StockDataPoint(String date, double price, long volume) {
        this(date, price, price, price, price, volume);
    }

    public StockDataPoint(String date, double o, double h, double l, double c) {
        this(date, o, h, l, c, 0L);
    }

    public StockDataPoint(String date, double o, double h, double l, double c, long volume) {
        this.t = date;
        this.o = o;
        this.h = h;
        this.l = l;
        this.c = c;

        // Keep legacy payload fields aligned
        this.date = date;
        this.price = c;
        this.volume = volume;
    }
}
