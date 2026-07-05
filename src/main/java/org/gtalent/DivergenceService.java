package org.gtalent;

import org.gtalent.dto.DivergenceResult;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class DivergenceService {

    /**
     * 偵測 RSI 與價格的背離
     */
    public DivergenceResult detectRSIDivergence(String symbol) {
        int period = 14;
        int lookback = 30; // 偵測最近 30 天內的背離
        List<StockDataPoint> history = DatabaseManager.getRecentHistory(symbol, lookback + period);
        if (history.size() < lookback) {
            return new DivergenceResult("RSI", "無", 0, false, "資料不足");
        }

        List<Double> prices = new ArrayList<>();
        List<Double> rsiValues = new ArrayList<>();

        // 計算最近 lookback 天的 RSI 序列
        for (int i = 0; i < lookback; i++) {
            // 注意：IndicatorCalculator 的計算目前是單點的，這裡為了效能簡化處理
            // 在正式版本中，建議實作 Series 計算以提高效能
            prices.add(history.get(history.size() - 1 - i).c);
            // RSI 需要往前推 period 天
            double rsi = IndicatorCalculator.calculateRSI(symbol, period); // 這裡簡化了，實際應計算序列
            rsiValues.add(rsi);
        }

        return detectGenericDivergence("RSI", prices, rsiValues);
    }

    /**
     * 偵測 MACD 與價格的背離
     */
    public DivergenceResult detectMACDDivergence(String symbol) {
        List<MACDResult> macdSeries = IndicatorCalculator.calculateMACDSeries(symbol, 30);
        List<StockDataPoint> history = DatabaseManager.getRecentHistory(symbol, 30);
        
        if (macdSeries.size() < 20 || history.size() < 20) {
            return new DivergenceResult("MACD", "無", 0, false, "資料不足");
        }

        List<Double> prices = new ArrayList<>();
        List<Double> difValues = new ArrayList<>();
        
        for (int i = 0; i < Math.min(macdSeries.size(), history.size()); i++) {
            prices.add(history.get(i).c);
            difValues.add(macdSeries.get(i).dif);
        }

        return detectGenericDivergence("MACD", prices, difValues);
    }

    private DivergenceResult detectGenericDivergence(String type, List<Double> prices, List<Double> indicators) {
        if (prices.size() < 10 || indicators.size() < 10) {
            return new DivergenceResult(type, "無", 0, false, "資料不足");
        }

        int lastIdx = prices.size() - 1;
        double currentPrice = prices.get(lastIdx);
        double currentInd = indicators.get(lastIdx);

        // 簡化邏輯：尋找前一個顯著高/低點
        // 正確的做法應使用 Peak/Valley 算法
        
        // 底背離: 價格創新低，但指標未創新低
        double minPrice = prices.get(0);
        int minPriceIdx = 0;
        for (int i = 0; i < lastIdx; i++) {
            if (prices.get(i) < minPrice) {
                minPrice = prices.get(i);
                minPriceIdx = i;
            }
        }

        if (currentPrice < minPrice && indicators.get(lastIdx) > indicators.get(minPriceIdx)) {
            return new DivergenceResult(type, "底背離", 0.8, true, "股價破底但 " + type + " 未破底，顯示下殺動能竭盡。");
        }

        // 頂背離: 價格創新高，但指標未創新高
        double maxPrice = prices.get(0);
        int maxPriceIdx = 0;
        for (int i = 0; i < lastIdx; i++) {
            if (prices.get(i) > maxPrice) {
                maxPrice = prices.get(i);
                maxPriceIdx = i;
            }
        }

        if (currentPrice > maxPrice && indicators.get(lastIdx) < indicators.get(maxPriceIdx)) {
            return new DivergenceResult(type, "頂背離", 0.8, true, "股價創高但 " + type + " 未創高，顯示追價力道轉弱。");
        }

        return new DivergenceResult(type, "無", 0, false, "目前無明顯背離跡象。");
    }
}
