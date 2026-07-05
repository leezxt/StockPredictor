package org.gtalent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/market")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"})
public class MarketController {

    private final MarketBreadthService breadthService;

    @Autowired
    public MarketController(MarketBreadthService breadthService) {
        this.breadthService = breadthService;
    }

    @GetMapping("/breadth")
    public Map<String, Object> getMarketBreadth() {
        MarketBreadthResult result = breadthService.calculateMarketBreadth();

        Map<String, Object> response = new HashMap<>();
        response.put("breadth", result.getBreadth());
        response.put("eligibleCount", result.getEligibleCount());
        response.put("bullishCount", result.getBullishCount());
        return response;
    }

    @GetMapping("/breadth/history")
    public List<MarketBreadthSnapshot> getMarketBreadthHistory(@RequestParam(name = "days", defaultValue = "120") int days) {
        return breadthService.getMarketBreadthHistory(days);
    }
}

