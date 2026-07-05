package org.gtalent;

public class BacktestResult {
    public String symbol;
    public String strategy;
    public int shortWindow;
    public int longWindow;
    public int lookbackDays;

    public String startDate;
    public String endDate;

    public double initialCapital;
    public double finalCapital;
    public double totalReturnPct;
    public double buyAndHoldReturnPct;
    public double excessReturnPct;

    public int tradeCount;
    public int winCount;
    public double winRate;

    // 每筆交易平均獲利 / 平均虧損（用於 Kelly 計算）
    public double avgProfit;
    public double avgLoss;
    public double sortinoRatio;
    public double sharpeRatio;
    public double calmarRatio;
    public double ulcerIndex;
    public double beta;
    public double treynorRatio;
    public double informationRatio;

    // Kelly 公式建議倉位
    public double kellyPercent;     // Half-Kelly 建議倉位百分比（0~50）
    public double fullKellyPercent; // 完整 Kelly（僅供參考）

    public String lastSignal;
    public String note;
}
