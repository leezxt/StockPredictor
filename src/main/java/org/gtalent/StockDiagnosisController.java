package org.gtalent;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/stocks")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"})
public class StockDiagnosisController {
    private final StockDiagnosisService stockDiagnosisService;

    public StockDiagnosisController(StockDiagnosisService stockDiagnosisService) {
        this.stockDiagnosisService = stockDiagnosisService;
    }

    @GetMapping("/{symbol}/diagnosis")
    public Map<String, Object> getIntegratedDiagnosis(@PathVariable("symbol") String symbol) {
        return stockDiagnosisService.diagnose(symbol);
    }
}
