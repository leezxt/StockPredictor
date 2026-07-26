package org.gtalent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ScoreEngine.class)
@TestPropertySource(properties = {
        "scoring.fundamental.bonus.yoy-30=13",
        "scoring.fundamental.bonus.mom-10=7",
        "scoring.fundamental.penalty.yoy-negative=11",
        "scoring.fundamental.penalty.mom-negative-10=5"
})
class ScoreEngineTest {

    @Autowired
    private ScoreEngine scoreEngine;

    @Test
    void shouldApplyInjectedFundamentalBonuses() {
        assertEquals(70, scoreEngine.scoreFundamental(10, 35.0, 15.0));
    }

    @Test
    void shouldApplyInjectedFundamentalPenalties() {
        assertEquals(34, scoreEngine.scoreFundamental(10, -5.0, -15.0));
    }
}
