package org.gtalent;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/config")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"})
public class ConfigController {

    private final ScoreEngine scoreEngine;
    private final InstitutionalService institutionalService;

    public ConfigController(ScoreEngine scoreEngine, InstitutionalService institutionalService) {
        this.scoreEngine = scoreEngine;
        this.institutionalService = institutionalService;
    }

    @PostMapping("/reload")
    public Map<String, Object> reloadScoringConfig() {
        return scoreEngine.reloadScoringConfig();
    }

    @GetMapping("/features")
    public Map<String, Object> getFeatureToggle() {
        return Map.of(
            "forceFinMind", institutionalService.isForceFinMind()
        );
    }

    @PostMapping("/features/toggle")
    public Map<String, Object> toggleFeature() {
        boolean current = institutionalService.isForceFinMind();
        institutionalService.setForceFinMind(!current);
        return Map.of(
            "forceFinMind", !current,
            "message", "一鍵開關切換成功！當前強制 FinMind 模式：" + (!current ? "啟用" : "關閉")
        );
    }
}

