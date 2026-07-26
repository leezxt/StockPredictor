package org.gtalent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CrossDiagnosisControllerApiErrorTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CrossDiagnosisController controller = new CrossDiagnosisController(
                mock(MarketCrossScannerService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalApiExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnUnifiedBadRequestForEmptySymbols() throws Exception {
        mockMvc.perform(post("/api/cross/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbols\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.path").value("/api/cross/batch"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldRejectSymbolsContainingOnlyBlankValues() throws Exception {
        mockMvc.perform(post("/api/cross/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbols\":[\" \",null]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("symbols 至少需要一個非空白股票代號。"));
    }

    @Test
    void shouldReturnUnifiedBadRequestForMalformedBody() throws Exception {
        mockMvc.perform(post("/api/cross/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbols\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST_BODY"))
                .andExpect(jsonPath("$.message").value("請求內容缺失或 JSON 格式不正確。"))
                .andExpect(jsonPath("$.path").value("/api/cross/batch"));
    }
}
