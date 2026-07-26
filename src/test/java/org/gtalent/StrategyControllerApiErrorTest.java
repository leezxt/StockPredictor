package org.gtalent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StrategyControllerApiErrorTest {
    private MarketService marketService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        marketService = mock(MarketService.class);
        StrategyController controller = new StrategyController(
                marketService,
                mock(FinMindClient.class),
                mock(AdvancedFundamentalService.class));

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalApiExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnUnifiedBadRequestForEmptyCustomSymbols() throws Exception {
        mockMvc.perform(get("/api/strategy/q1-black-horse-custom")
                        .param("symbols", " , "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("symbols 至少需要一個股票代號。"))
                .andExpect(jsonPath("$.path").value("/api/strategy/q1-black-horse-custom"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldReturnUnifiedBadRequestWhenSymbolsParameterIsMissing() throws Exception {
        mockMvc.perform(get("/api/strategy/q1-black-horse-custom"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"))
                .andExpect(jsonPath("$.message").value("缺少必要參數：symbols。"))
                .andExpect(jsonPath("$.path").value("/api/strategy/q1-black-horse-custom"));
    }

    @Test
    void shouldReturnServiceUnavailableWhenMarketSymbolsCannotBeLoaded() throws Exception {
        when(marketService.getAllSymbols()).thenReturn(List.of());

        mockMvc.perform(get("/api/strategy/q1-black-horse"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.code").value("MARKET_DATA_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("目前無法取得市場股票清單，請稍後再試。"))
                .andExpect(jsonPath("$.path").value("/api/strategy/q1-black-horse"));
    }
}
