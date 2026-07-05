package org.gtalent;

import java.util.List;

public class KDAnalyzer {

    // 偵測低檔鈍化
    public boolean isLowPassivation(List<Double> kValues) {
        if (kValues.size() < 3) return false;
        // 檢查最近三天 K 值是否都 < 20
        return kValues.subList(kValues.size() - 3, kValues.size())
                      .stream().allMatch(k -> k < 20);
    }

    // 偵測底背離 (簡化邏輯)
    public boolean isBottomDivergence(List<Double> prices, List<Double> dValues) {
        if (prices.size() < 20 || dValues.size() < 20) return false;

        double currentPrice = prices.get(prices.size() - 1);
        double prevPrice = prices.get(prices.size() - 20); // 取 20 天前的低點對比

        double currentD = dValues.get(dValues.size() - 1);
        double prevD = dValues.get(dValues.size() - 20);

        // 股價創新低，但 D 值未創新低
        return (currentPrice < prevPrice) && (currentD > prevD);
    }
}

