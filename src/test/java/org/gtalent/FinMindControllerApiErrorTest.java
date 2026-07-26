package org.gtalent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FinMindControllerApiErrorTest {
    private FinMindClient finMindClient;
    private EnhancedInstitutionalService enhancedInstitutionalService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        finMindClient = mock(FinMindClient.class);
        enhancedInstitutionalService = mock(EnhancedInstitutionalService.class);
        FinMindController controller = new FinMindController(
                finMindClient, enhancedInstitutionalService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalApiExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnSafeUnifiedErrorWhenFinMindClientFails() throws Exception {
        when(finMindClient.fetchLatestChipData("2330"))
                .thenThrow(new IllegalStateException("FinMind token secret"));

        mockMvc.perform(get("/api/finmind/latest").param("symbol", "2330"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("系統暫時無法處理此請求，請稍後再試。"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret"))))
                .andExpect(jsonPath("$.path").value("/api/finmind/latest"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldReturnSafeUnifiedErrorWhenInstitutionalServiceFails() throws Exception {
        when(enhancedInstitutionalService.calculateChipConcentrationScore("2330"))
                .thenThrow(new IllegalStateException("資料庫密碼 secret"));

        mockMvc.perform(get("/api/finmind/chip-score").param("symbol", "2330"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/finmind/chip-score"));
    }

    @Test
    void shouldReturnUnifiedBadRequestWhenRequiredParameterIsMissing() throws Exception {
        mockMvc.perform(get("/api/finmind/date-range")
                        .param("symbol", "2330")
                        .param("startDate", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"))
                .andExpect(jsonPath("$.message").value("缺少必要參數：endDate。"))
                .andExpect(jsonPath("$.path").value("/api/finmind/date-range"));
    }
}
