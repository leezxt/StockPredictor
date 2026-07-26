package org.gtalent;

import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stocks")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"})
public class StockAdviceController {
    private final InstitutionalService institutionalService;
    private final StockDiagnosisService stockDiagnosisService;
    private final StockKdAnalysisService stockKdAnalysisService;
    private final StockHistoryService stockHistoryService;
    private final IndicatorCalculator indicatorCalculator;

    public StockAdviceController(InstitutionalService institutionalService,
                                 StockDiagnosisService stockDiagnosisService,
                                 StockKdAnalysisService stockKdAnalysisService,
                                 StockHistoryService stockHistoryService,
                                 IndicatorCalculator indicatorCalculator) {
        this.institutionalService = institutionalService;
        this.stockDiagnosisService = stockDiagnosisService;
        this.stockKdAnalysisService = stockKdAnalysisService;
        this.stockHistoryService = stockHistoryService;
        this.indicatorCalculator = indicatorCalculator;
    }

    @GetMapping("/{symbol}/advices")
    public List<String> getStockAdvices(@PathVariable("symbol") String symbol) {
        String clean = symbol == null ? "" : symbol.trim();
        List<String> advices = new ArrayList<>();
        Map<String, Object> diagnosis = stockDiagnosisService.diagnose(clean);
        advices.add(diagnosis.get("suggestion").toString());

        List<InstitutionalTrade> trades = institutionalService.getRecentInstitutionalTrades(clean, 5);
        if (!trades.isEmpty()) {
            advices.add(trades.get(0).getTotalNetBuy() > 0
                    ? "投信近期買超，顯示機構看好。"
                    : "投信近期賣超，需注意籌碼風險。");
        }

        List<MACDResult> macd = indicatorCalculator.calculateMACDSeries(clean, 60);
        KDInfo kdInfo = stockKdAnalysisService.analyze(clean, 60);
        double dif = macd.isEmpty() ? 0 : macd.get(macd.size() - 1).dif;
        double dea = macd.isEmpty() ? 0 : macd.get(macd.size() - 1).dea;
        advices.add(dif > dea ? "MACD 黃金交叉，短期內可能上漲。" : "MACD 死亡交叉，需提防回檔風險。");

        List<KDResult> kd = kdInfo.getKdSeries() == null ? List.of() : kdInfo.getKdSeries();
        KDAnalyzer analyzer = new KDAnalyzer();
        if (analyzer.isLowPassivation(kd.stream().map(KDResult::getK).toList())) {
            advices.add("KD 指標低檔鈍化，可能出現反彈機會。");
        }
        if (analyzer.isBottomDivergence(
                kd.stream().map(KDResult::getD).toList(),
                stockHistoryService.getRecentHistory(clean, 60).stream().map(point -> point.c).toList())) {
            advices.add("KD 指標底部背離，顯示潛在反轉訊號。");
        }
        return advices;
    }
}
