package org.gtalent;

import java.util.List;
import java.util.Map;

public class KDInfo {
    private List<KDResult> kdSeries;
    private boolean lowPassivation;
    private boolean bottomDivergence;
    private int advancedScore;
    private List<String> advancedSignals;
    private Map<String, Object> extendedIndicators;
    private String diagnosis;
    private double latestClosePrice;

    public KDInfo() {}

    public KDInfo(List<KDResult> kdSeries, boolean lowPassivation, boolean bottomDivergence, String diagnosis) {
        this.kdSeries = kdSeries;
        this.lowPassivation = lowPassivation;
        this.bottomDivergence = bottomDivergence;
        this.diagnosis = diagnosis;
    }

    public List<KDResult> getKdSeries() { return kdSeries; }
    public void setKdSeries(List<KDResult> kdSeries) { this.kdSeries = kdSeries; }
    public boolean isLowPassivation() { return lowPassivation; }
    public void setLowPassivation(boolean lowPassivation) { this.lowPassivation = lowPassivation; }
    public boolean isBottomDivergence() { return bottomDivergence; }
    public void setBottomDivergence(boolean bottomDivergence) { this.bottomDivergence = bottomDivergence; }
    public int getAdvancedScore() { return advancedScore; }
    public void setAdvancedScore(int advancedScore) { this.advancedScore = advancedScore; }
    public List<String> getAdvancedSignals() { return advancedSignals; }
    public void setAdvancedSignals(List<String> advancedSignals) { this.advancedSignals = advancedSignals; }
    public Map<String, Object> getExtendedIndicators() { return extendedIndicators; }
    public void setExtendedIndicators(Map<String, Object> extendedIndicators) { this.extendedIndicators = extendedIndicators; }
    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }
    public double getLatestClosePrice() { return latestClosePrice; }
    public void setLatestClosePrice(double latestClosePrice) { this.latestClosePrice = latestClosePrice; }
}
