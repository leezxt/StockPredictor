package org.gtalent.dto;

import java.util.List;
import java.util.Map;

/**
 * AI 漲跌預測結果
 */
public class PredictionResult {
    private double probability;      // 上漲機率 (0-100)
    private String recommendation;   // 建議動作
    private double confidence;       // 信心指數 (0-1)
    private List<String> factors;    // 影響因子列表
    private Map<String, Double> scoreDetails; // 各維度得分細節

    public PredictionResult() {}

    public PredictionResult(double probability, String recommendation, double confidence, List<String> factors, Map<String, Double> scoreDetails) {
        this.probability = probability;
        this.recommendation = recommendation;
        this.confidence = confidence;
        this.factors = factors;
        this.scoreDetails = scoreDetails;
    }

    // Getters and Setters
    public double getProbability() { return probability; }
    public void setProbability(double probability) { this.probability = probability; }

    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public List<String> getFactors() { return factors; }
    public void setFactors(List<String> factors) { this.factors = factors; }

    public Map<String, Double> getScoreDetails() { return scoreDetails; }
    public void setScoreDetails(Map<String, Double> scoreDetails) { this.scoreDetails = scoreDetails; }
}
