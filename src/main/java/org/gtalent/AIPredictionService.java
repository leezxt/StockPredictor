package org.gtalent;

import org.gtalent.dto.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AIPredictionService {

    private final EnhancedInstitutionalService enhancedInstitutionalService;
    private final VolumeAnalysisService volumeAnalysisService;
    private final DivergenceService divergenceService;
    private final StockDataRepository stockDataRepository;
    private final IndicatorCalculator indicatorCalculator;

    public AIPredictionService(EnhancedInstitutionalService enhancedInstitutionalService,
                               VolumeAnalysisService volumeAnalysisService,
                               DivergenceService divergenceService,
                               StockDataRepository stockDataRepository,
                               IndicatorCalculator indicatorCalculator) {
        this.enhancedInstitutionalService = enhancedInstitutionalService;
        this.volumeAnalysisService = volumeAnalysisService;
        this.divergenceService = divergenceService;
        this.stockDataRepository = stockDataRepository;
        this.indicatorCalculator = indicatorCalculator;
    }

    /**
     * 執行 AI 預測分析
     */
    public PredictionResult predict(String symbol) {
        List<String> factors = new ArrayList<>();
        Map<String, Double> scoreDetails = new HashMap<>();
        
        // 1. 技術面得分 (40%)
        double technicalScore = calculateTechnicalScore(symbol, factors);
        scoreDetails.put("technical", technicalScore);

        // 2. 籌碼與量能得分 (30%)
        double chipScore = calculateChipScore(symbol, factors);
        scoreDetails.put("chip", chipScore);

        // 3. 反轉與動能訊號 (30%)
        double signalScore = calculateSignalScore(symbol, factors);
        scoreDetails.put("signal", signalScore);

        // 總分計算 (0-100)
        double totalScore = (technicalScore * 0.4) + (chipScore * 0.3) + (signalScore * 0.3);
        
        // 映射至機率 (簡單線性映射，可調整)
        double probability = 20 + (totalScore * 0.6); // 基礎 20% + 得分加成
        
        String recommendation;
        double confidence = 0.7; // 預設信心

        if (totalScore >= 80) {
            recommendation = "強烈買進 (Strong Buy)";
            confidence = 0.9;
        } else if (totalScore >= 65) {
            recommendation = "買進 (Buy)";
            confidence = 0.8;
        } else if (totalScore >= 45) {
            recommendation = "持有 (Hold)";
            confidence = 0.6;
        } else if (totalScore >= 30) {
            recommendation = "減碼 (Reduce)";
            confidence = 0.7;
        } else {
            recommendation = "強烈賣出 (Strong Sell)";
            confidence = 0.85;
        }

        if (factors.isEmpty()) {
            factors.add("缺乏明顯訊號，維持觀望。");
        }

        return new PredictionResult(Math.round(probability * 10) / 10.0, recommendation, confidence, factors, scoreDetails);
    }

    private double calculateTechnicalScore(String symbol, List<String> factors) {
        double score = 50; // 中性起步
        
        // 布林通道
        BollingerResult bb = indicatorCalculator.calculateBollinger(symbol);
        double price = stockDataRepository.getLatestPrice(symbol);
        if (bb.getUpperBand() > 0) {
            if (price >= bb.getUpperBand()) {
                score -= 10;
                factors.add("技術面：股價觸及布林上軌，短期存在超買回檔壓力。");
            } else if (price <= bb.getLowerBand()) {
                score += 20;
                factors.add("技術面：股價觸及布林下軌，具備反彈潛力。");
            } else if (price > bb.getMiddleBand()) {
                score += 10;
                factors.add("技術面：股價維持在中軌之上，趨勢偏多。");
            }
        }

        // 一目均衡表
        IchimokuResult ichi = indicatorCalculator.calculateIchimoku(symbol);
        if (ichi.getTenkanSen() > 0) {
            if (ichi.getTenkanSen() > ichi.getKijunSen()) {
                score += 15;
                factors.add("技術面：一目均衡表出現轉折向上突破基準線（黃金交叉）。");
            }
            if (price > ichi.getSenkouSpanA() && price > ichi.getSenkouSpanB()) {
                score += 10;
                factors.add("技術面：股價站上雲帶，長期趨勢轉強。");
            }
        }

        return Math.min(100, Math.max(0, score));
    }

    private double calculateChipScore(String symbol, List<String> factors) {
        double score = 50;
        
        // 法人合力
        InstitutionalSyncResult sync = enhancedInstitutionalService.calculateInstitutionalSync(symbol, 5);
        if (sync.isSyncBuying()) {
            score += 30;
            factors.add("籌碼面：外資與投信近期同步買超，大戶結構紮實。");
        } else if (sync.getCombinedScore() > 60) {
            score += 15;
            factors.add("籌碼面：法人買超動能增加，籌碼趨於集中。");
        } else if (sync.getCombinedScore() < 30) {
            score -= 20;
            factors.add("籌碼面：法人持續調節，需提防主力撤退。");
        }

        // 成交量異常
        VolumeAnomalyResult vol = volumeAnalysisService.detectVolumeAnomaly(symbol);
        if ("量增價漲".equals(vol.getSignalType())) {
            score += 20;
            factors.add("量能面：出現量增價漲攻擊訊號，多頭進攻意願強。");
        } else if ("量縮價不跌".equals(vol.getSignalType())) {
            score += 10;
            factors.add("量能面：縮量整理但不破低點，籌碼鎖定度高。");
        }

        return Math.min(100, Math.max(0, score));
    }

    private double calculateSignalScore(String symbol, List<String> factors) {
        double score = 50;
        
        // 背離偵測
        DivergenceResult rsiDiv = divergenceService.detectRSIDivergence(symbol);
        if ("底背離".equals(rsiDiv.getDivergenceType())) {
            score += 30;
            factors.add("訊號面：RSI 指標出現底背離，為強烈反轉看漲訊號。");
        } else if ("頂背離".equals(rsiDiv.getDivergenceType())) {
            score -= 30;
            factors.add("訊號面：RSI 指標出現頂背備離，需提防高檔反轉。");
        }

        DivergenceResult macdDiv = divergenceService.detectMACDDivergence(symbol);
        if ("底背離".equals(macdDiv.getDivergenceType())) {
            score += 20;
            factors.add("訊號面：MACD DIF 出現底背離，趨勢底部確立。");
        }

        return Math.min(100, Math.max(0, score));
    }
}
