package org.gtalent;

import org.gtalent.dto.VolumeAnomalyResult;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class VolumeAnalysisService {

    /**
     * 偵測成交量異常
     */
    public VolumeAnomalyResult detectVolumeAnomaly(String symbol) {
        // 取最近 6 筆數據 (今日 + 過去 5 日均量)
        List<StockDataPoint> history = DatabaseManager.getRecentHistory(symbol, 6);
        if (history.size() < 6) {
            return new VolumeAnomalyResult("資料不足", 1.0, 0, "歷史資料不足 6 筆，無法計算均量");
        }

        StockDataPoint today = history.get(history.size() - 1);
        double todayVolume = today.volume;
        double todayClose = today.c;
        double prevClose = history.get(history.size() - 2).c;
        double priceChangePct = (prevClose == 0) ? 0 : (todayClose - prevClose) / prevClose * 100.0;

        // 計算前 5 日平均成交量
        double sumVol5 = 0;
        for (int i = 0; i < 5; i++) {
            sumVol5 += history.get(i).volume;
        }
        double avgVol5 = sumVol5 / 5.0;
        double volumeRatio = (avgVol5 == 0) ? 0 : todayVolume / avgVol5;

        String signal = "正常";
        String desc = "成交量與價格變動均在正常範圍內";

        if (volumeRatio >= 1.5 && priceChangePct >= 2.0) {
            signal = "量增價漲";
            desc = "成交量大於 5 日均量 " + String.format("%.1f", volumeRatio) + " 倍，且股價上漲 " + String.format("%.1f%%", priceChangePct) + "，為攻擊訊號。";
        } else if (volumeRatio <= 0.5 && priceChangePct >= -0.5) {
            signal = "量縮價不跌";
            desc = "成交量萎縮至均量 " + String.format("%.1f", volumeRatio) + " 倍，但股價維持穩定，可能為洗盤結束或籌碼穩定。";
        } else if (volumeRatio >= 2.0 && priceChangePct <= -3.0) {
            signal = "量增價跌";
            desc = "成交量異常放大且股價重挫，需提防主力出貨或空頭攻擊。";
        }

        return new VolumeAnomalyResult(signal, volumeRatio, priceChangePct, desc);
    }
}
