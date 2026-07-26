package org.gtalent;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class StockKdAnalysisService {
    private final KdAdvancedService kdAdvancedService;
    private final StockDataRepository stockDataRepository;
    private final IndicatorCalculator indicatorCalculator;

    public StockKdAnalysisService(KdAdvancedService kdAdvancedService,
                                  StockDataRepository stockDataRepository,
                                  IndicatorCalculator indicatorCalculator) {
        this.kdAdvancedService = kdAdvancedService;
        this.stockDataRepository = stockDataRepository;
        this.indicatorCalculator = indicatorCalculator;
    }

    public KDInfo analyze(String symbol, int limit) {
        List<KDResult> kdSeries = indicatorCalculator.calculateKD(symbol, limit);
        KDInfo info = new KDInfo();
        info.setKdSeries(kdSeries);
        info.setAdvancedScore(0);
        info.setAdvancedSignals(java.util.Collections.emptyList());
        info.setExtendedIndicators(java.util.Collections.emptyMap());
        info.setLatestClosePrice(stockDataRepository.getLatestPrice(symbol));

        if (kdSeries == null || kdSeries.isEmpty()) {
            info.setLowPassivation(false);
            info.setBottomDivergence(false);
            info.setDiagnosis("暫無 KD 資料（歷史資料不足，請稍後再試）");
            return info;
        }

        KDAnalyzer kdAnalyzer = new KDAnalyzer();
        List<Double> kValues = kdSeries.stream().map(KDResult::getK).collect(java.util.stream.Collectors.toList());
        List<Double> dValues = kdSeries.stream().map(KDResult::getD).collect(java.util.stream.Collectors.toList());
        List<Double> closes = stockDataRepository.getRecentHistory(symbol, limit).stream()
                .map(dp -> dp.c > 0 ? dp.c : dp.price)
                .collect(java.util.stream.Collectors.toList());

        List<KdData> advancedSeries = buildKdAdvancedSeries(kdSeries, closes);
        KdAdvancedService.AnalysisResult advanced = kdAdvancedService.analyze(advancedSeries);
        info.setAdvancedScore(advanced.getScore());
        info.setAdvancedSignals(advanced.getSignals());
        enrichKdExtendedIndicators(symbol, info, closes);

        boolean lowPassivation = kdAnalyzer.isLowPassivation(kValues);
        boolean bottomDivergence = kdAnalyzer.isBottomDivergence(dValues, closes);
        info.setLowPassivation(lowPassivation);
        info.setBottomDivergence(bottomDivergence);

        if (bottomDivergence) {
            info.setDiagnosis("KD 指標底部背離，顯示潛在反轉訊號。");
        } else if (lowPassivation) {
            info.setDiagnosis("KD 指標低檔鈍化，可能出現反彈機會。");
        } else if (advanced.getScore() >= 10) {
            info.setDiagnosis("KD 高級動能偏多：" + String.join("；", advanced.getSignals()));
        } else if (advanced.getScore() <= -10) {
            info.setDiagnosis("KD 高級動能偏空：" + String.join("；", advanced.getSignals()));
        } else {
            info.setDiagnosis(buildKdAnalystDiagnosis(info));
        }

        return info;
    }

    private void enrichKdExtendedIndicators(String symbol, KDInfo info, List<Double> closes) {
        if (info == null || symbol == null || symbol.isBlank()) {
            return;
        }

        double rsi = indicatorCalculator.calculateRSI(symbol, 14);
        MACDResult macd = indicatorCalculator.calculateMACD(symbol);
        double atr = indicatorCalculator.calculateATR(symbol, 14);
        double obvStrength = indicatorCalculator.calculateOBV(symbol, 20);
        double mfi = indicatorCalculator.calculateMFI(symbol, 14);
        double cmf = indicatorCalculator.calculateCMF(symbol, 20);
        double cci = indicatorCalculator.calculateCCI(symbol, 20);
        double williamsR = indicatorCalculator.calculateWilliamsR(symbol, 14);
        double aroonOscillator = indicatorCalculator.calculateAroonOscillator(symbol, 25);
        int superTrendDirection = indicatorCalculator.calculateSuperTrendDirection(symbol, 10, 3.0);
        double donchianPosition = indicatorCalculator.calculateDonchianPosition(symbol, 20);

        double latestClose = (closes == null || closes.isEmpty()) ? 0.0 : closes.get(closes.size() - 1);
        double atrPct = (latestClose > 0 && atr > 0) ? (atr / latestClose) * 100.0 : 0.0;
        double obvStrengthPct = obvStrength * 100.0;

        Map<String, Object> indicators = new LinkedHashMap<>();
        indicators.put("rsi", round2(rsi));
        indicators.put("macdDif", round2(macd.dif));
        indicators.put("macdDea", round2(macd.dea));
        indicators.put("macdHistogram", round2(macd.histogram));
        indicators.put("atr", round2(atr));
        indicators.put("atrPct", round2(atrPct));
        indicators.put("obvStrength", round2(obvStrengthPct));
        indicators.put("mfi", round2(mfi));
        indicators.put("cmf", round2(cmf));
        indicators.put("cci", round2(cci));
        indicators.put("williamsR", round2(williamsR));
        indicators.put("aroonOscillator", round2(aroonOscillator));
        indicators.put("superTrendDirection", superTrendDirection);
        indicators.put("superTrendLabel", superTrendDirection > 0 ? "多頭" : superTrendDirection < 0 ? "空頭" : "中性");
        indicators.put("donchianPositionPct", round2(donchianPosition * 100.0));
        info.setExtendedIndicators(indicators);

        int extraScore = 0;
        List<String> extraSignals = new ArrayList<>();

        if (rsi <= 30) {
            extraScore += 4;
            extraSignals.add("💎 RSI 低檔（<=30）：可能進入反彈區");
        } else if (rsi >= 75) {
            extraScore -= 4;
            extraSignals.add("⚠️ RSI 過熱（>=75）：短線回檔風險增加");
        }

        if (macd.dif > macd.dea) {
            extraScore += 3;
            extraSignals.add("📈 MACD 多頭結構（DIF > DEA）");
        } else if (macd.dif < macd.dea) {
            extraScore -= 3;
            extraSignals.add("📉 MACD 空頭結構（DIF < DEA）");
        }

        if (superTrendDirection > 0) {
            extraScore += 5;
            extraSignals.add("🟢 SuperTrend 多頭趨勢");
        } else if (superTrendDirection < 0) {
            extraScore -= 5;
            extraSignals.add("🔴 SuperTrend 空頭趨勢");
        }

        if (donchianPosition >= 0.90) {
            extraScore += 3;
            extraSignals.add("🚀 Donchian 靠近上軌：突破力道強");
        } else if (donchianPosition <= 0.10) {
            extraScore -= 3;
            extraSignals.add("🧊 Donchian 靠近下軌：走勢偏弱");
        }

        if (cmf >= 0.08) {
            extraScore += 3;
            extraSignals.add("💰 CMF 正流入（>=0.08）：資金偏多");
        } else if (cmf <= -0.08) {
            extraScore -= 3;
            extraSignals.add("💸 CMF 負流出（<=-0.08）：資金偏空");
        }

        if (mfi <= 20) {
            extraScore += 2;
            extraSignals.add("📊 MFI 低檔（<=20）：留意超賣反彈");
        } else if (mfi >= 80) {
            extraScore -= 2;
            extraSignals.add("📊 MFI 高檔（>=80）：留意超買回落");
        }

        if (cci >= 100) {
            extraScore += 2;
            extraSignals.add("⚡ CCI 強勢（>=100）");
        } else if (cci <= -100) {
            extraScore -= 2;
            extraSignals.add("⚠️ CCI 弱勢（<=-100）");
        }

        if (williamsR <= -80) {
            extraScore += 1;
            extraSignals.add("🔄 Williams %R 低檔（<=-80）：短彈機率提升");
        } else if (williamsR >= -20) {
            extraScore -= 1;
            extraSignals.add("🛑 Williams %R 高檔（>=-20）：追價風險提高");
        }

        if (aroonOscillator >= 35) {
            extraScore += 2;
            extraSignals.add("📶 Aroon Oscillator 偏強（>=35）");
        } else if (aroonOscillator <= -35) {
            extraScore -= 2;
            extraSignals.add("📶 Aroon Oscillator 偏弱（<=-35）");
        }

        if (atrPct >= 6.0) {
            extraScore -= 2;
            extraSignals.add("🌪️ ATR 波動偏大（ATR/Close >= 6%）：風險提高");
        } else if (atrPct > 0 && atrPct <= 2.5) {
            extraScore += 1;
            extraSignals.add("🛡️ ATR 波動穩定（ATR/Close <= 2.5%）");
        }

        if (obvStrengthPct >= 12) {
            extraScore += 2;
            extraSignals.add("📦 OBV 量價結構偏多");
        } else if (obvStrengthPct <= -12) {
            extraScore -= 2;
            extraSignals.add("📦 OBV 量價結構偏空");
        }

        List<String> mergedSignals = new ArrayList<>();
        if (info.getAdvancedSignals() != null) {
            mergedSignals.addAll(info.getAdvancedSignals());
        }
        mergedSignals.addAll(extraSignals);
        info.setAdvancedSignals(mergedSignals);
        info.setAdvancedScore(info.getAdvancedScore() + extraScore);
    }

    private String buildKdAnalystDiagnosis(KDInfo info) {
        if (info == null || info.getKdSeries() == null || info.getKdSeries().isEmpty()) {
            return "KD 資料不足，暫時無法形成完整判讀。";
        }

        List<KDResult> series = info.getKdSeries();
        KDResult latest = series.get(series.size() - 1);
        Map<String, Object> indicators = info.getExtendedIndicators() == null
                ? Collections.emptyMap()
                : info.getExtendedIndicators();

        double score = info.getAdvancedScore();
        double k = latest.getK();
        double d = latest.getD();
        double spread = k - d;
        double rsi = asDouble(indicators.get("rsi"));
        double mfi = asDouble(indicators.get("mfi"));
        double macdDif = asDouble(indicators.get("macdDif"));
        double macdDea = asDouble(indicators.get("macdDea"));
        double macdHistogram = asDouble(indicators.get("macdHistogram"));
        double cmf = asDouble(indicators.get("cmf"));
        double atrPct = asDouble(indicators.get("atrPct"));
        double obvStrength = asDouble(indicators.get("obvStrength"));
        double aroonOscillator = asDouble(indicators.get("aroonOscillator"));
        String superTrendLabel = String.valueOf(indicators.getOrDefault("superTrendLabel", "中性"));

        boolean consolidation = Math.abs(spread) <= 8.0 && k >= 40.0 && k <= 60.0 && Math.abs(score) <= 8.0;
        boolean bullishBias = "多頭".equals(superTrendLabel) || cmf >= 0.08 || macdDif >= macdDea;
        boolean bearishBias = "空頭".equals(superTrendLabel) || cmf <= -0.08 || macdDif < macdDea;

        StringBuilder text = new StringBuilder();
        if (consolidation) {
            text.append("目前來看，市場正處於盤整收斂區，KD 在 ")
                    .append(format2(k))
                    .append(" / ")
                    .append(format2(d))
                    .append(" 附近來回拉扯，代表買賣雙方都還在觀望，短線還沒有明確方向。");
        } else if (k > d) {
            text.append("KD 結構仍偏多，K 值站在 D 值之上，趨勢尚未被破壞。");
        } else {
            text.append("KD 結構偏弱，K 值仍受制於 D 值，反彈先視為技術性修復。");
        }

        text.append(" RSI ")
                .append(formatIndicator(rsi))
                .append("、MFI ")
                .append(formatIndicator(mfi))
                .append("，");
        if ((isNeutralZone(rsi, 45, 55) || Double.isNaN(rsi)) && (isNeutralZone(mfi, 45, 55) || Double.isNaN(mfi))) {
            text.append("動能位階落在中性區。");
        } else if ((rsi >= 75 && !Double.isNaN(rsi)) || (mfi >= 80 && !Double.isNaN(mfi))) {
            text.append("指標已進入偏熱區，追價必須同步設好停利與停損。");
        } else if ((rsi <= 30 && !Double.isNaN(rsi)) || (mfi <= 20 && !Double.isNaN(mfi))) {
            text.append("動能偏低檔，若後續量能與交叉同步轉強，才適合分批觀察。");
        } else {
            text.append("位階介於中性與偏強之間，等待下一次方向表態。");
        }

        if (!Double.isNaN(macdDif) && !Double.isNaN(macdDea)) {
            text.append(" MACD ")
                    .append(macdDif >= macdDea ? "維持多方結構" : "尚未翻多")
                    .append("，柱狀體")
                    .append(Double.isNaN(macdHistogram) ? "未提供" : (macdHistogram >= 0 ? "偏正" : "偏負"))
                    .append("，顯示趨勢仍在整理確認中。");
        }

        if (bullishBias && !bearishBias) {
            text.append(" SuperTrend 與資金流仍偏多，整理較像蓄勢而不是出貨。");
        } else if (bearishBias && !bullishBias) {
            text.append(" 趨勢與資金訊號偏弱，盤整更像是弱勢反彈或換手修復。");
        } else {
            text.append(" 趨勢與資金訊號尚未完全同步，建議等突破箱頂或跌破箱底再定方向。");
        }

        if (!Double.isNaN(atrPct) && atrPct > 0) {
            text.append(" ATR/收盤約 ")
                    .append(format2(atrPct))
                    .append("%，")
                    .append(atrPct >= 6.0 ? "波動偏大，倉位要縮。" : "波動相對收斂，適合耐心等確認。");
        }

        if (!Double.isNaN(obvStrength)) {
            text.append(" OBV 強度約 ")
                    .append(format2(obvStrength))
                    .append("%，")
                    .append(obvStrength >= 12 ? "量價結構偏多。" : obvStrength <= -12 ? "量價結構偏空。" : "量能方向仍未定。");
        }

        if (!Double.isNaN(aroonOscillator)) {
            text.append(" Aroon Oscillator 約 ")
                    .append(format2(aroonOscillator))
                    .append("，")
                    .append(aroonOscillator >= 35 ? "趨勢動能仍在多方。" : aroonOscillator <= -35 ? "趨勢仍偏空方。" : "趨勢仍在均衡帶。");
        }

        text.append(" 操作上，建議以箱體上緣突破或下緣失守作為確認點，不宜在盤整內提前重押。");
        return text.toString();
    }

    private boolean isNeutralZone(double value, double low, double high) {
        return !Double.isNaN(value) && value >= low && value <= high;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String formatIndicator(double value) {
        return Double.isNaN(value) ? "無資料" : format2(value);
    }

    private String format2(double value) {
        return String.format(Locale.TAIWAN, "%.2f", value);
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.NaN;
    }

    private List<KdData> buildKdAdvancedSeries(List<KDResult> kdSeries, List<Double> closes) {
        if (kdSeries == null || closes == null || kdSeries.isEmpty() || closes.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        int size = Math.min(kdSeries.size(), closes.size());
        int kdStart = kdSeries.size() - size;
        int closeStart = closes.size() - size;

        List<KdData> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            KDResult kd = kdSeries.get(kdStart + i);
            double close = closes.get(closeStart + i);
            result.add(new KdData("", close, kd.getK(), kd.getD()));
        }
        return result;
    }

}
