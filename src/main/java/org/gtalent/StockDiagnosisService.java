package org.gtalent;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class StockDiagnosisService {
    private final StockHistoryService stockHistoryService;
    private final StockDataRepository stockDataRepository;
    private final IndicatorCalculator indicatorCalculator;

    public StockDiagnosisService(StockHistoryService stockHistoryService,
                                 StockDataRepository stockDataRepository,
                                 IndicatorCalculator indicatorCalculator) {
        this.stockHistoryService = stockHistoryService;
        this.stockDataRepository = stockDataRepository;
        this.indicatorCalculator = indicatorCalculator;
    }

    public Map<String, Object> diagnose(String symbol) {
        String cleanSymbol = normalizeSymbol(symbol);
        stockHistoryService.ensureRecentHistory(cleanSymbol, 120);

        Map<String, Object> response = new HashMap<>();
        double currentPrice = stockDataRepository.getLatestPrice(cleanSymbol);
        double ma5 = stockDataRepository.calculateMA(cleanSymbol, 5);
        double ma20 = stockDataRepository.calculateMA(cleanSymbol, 20);
        double rsi = indicatorCalculator.calculateRSI(cleanSymbol, 14);

        String status;
        String color;
        String suggestion;
        String riskLevel;
        boolean isBullish = currentPrice > ma5 && ma5 > ma20;

        if (isBullish) {
            if (rsi > 75) {
                status = "多頭過熱";
                color = "#ff8c00";
                suggestion = "目前處於強勢多頭，但 RSI 已達 " + Math.round(rsi) + "，顯示嚴重超買，建議「暫不加碼」並設好停利。";
                riskLevel = "高";
            } else if (rsi < 40) {
                status = "多頭背離";
                color = "#6f42c1";
                suggestion = "均線維持多頭，但 RSI 低於 40，出現動能背離現象，需小心趨勢反轉。";
                riskLevel = "中高";
            } else {
                status = "多頭穩健";
                color = "#ff4d4d";
                suggestion = "趨勢與動能配合良好，建議續抱。";
                riskLevel = "低";
            }
        } else if (currentPrice < ma20) {
            if (rsi < 25) {
                status = "空頭超買 (超賣)";
                color = "#007bff";
                suggestion = "股價處於空頭，但 RSI 僅 " + Math.round(rsi) + "，已進入極度超賣區，隨時可能出現強烈反彈。";
                riskLevel = "中";
            } else {
                status = "空頭趨勢";
                color = "#28a745";
                suggestion = "趨勢偏空，RSI 尚無轉強訊號，建議觀望。";
                riskLevel = "高";
            }
        } else {
            status = "盤整中";
            color = "#6c757d";
            suggestion = "趨勢不明朗，建議等待量能突破或 RSI 回到 50 以上。";
            riskLevel = "低";
        }

        String rsiStatus;
        if (rsi >= 70) {
            rsiStatus = "⚠️ RSI 過熱 (超買)，小心回檔風險。";
        } else if (rsi <= 30) {
            rsiStatus = "💎 RSI 低檔 (超賣)，具備反彈潛力。";
        } else {
            rsiStatus = "RSI 數值正常。";
        }

        response.put("symbol", cleanSymbol);
        response.put("price", round2(currentPrice));
        response.put("ma5", round2(ma5));
        response.put("ma20", round2(ma20));
        response.put("rsi", round2(rsi));
        response.put("rsiStatus", rsiStatus);
        response.put("status", status);
        response.put("color", color);
        response.put("suggestion", suggestion);
        response.put("riskLevel", riskLevel);
        response.put("icon", "📊");
        response.put("action", suggestion);
        return response;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim();
    }
}
