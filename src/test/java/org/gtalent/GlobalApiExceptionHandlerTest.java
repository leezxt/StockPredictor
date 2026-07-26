package org.gtalent;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.MissingServletRequestParameterException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalApiExceptionHandlerTest {
    private final GlobalApiExceptionHandler handler = new GlobalApiExceptionHandler();

    @Test
    void shouldReturnStructuredBadRequestForInvalidArgument() {
        MockHttpServletRequest request = request("/api/stocks/2330/backtest");

        ResponseEntity<ApiErrorResponse> response =
                handler.handleIllegalArgument(new IllegalArgumentException("lookback 必須大於 0"), request);

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("INVALID_ARGUMENT", response.getBody().code());
        assertEquals("lookback 必須大於 0", response.getBody().message());
        assertEquals("/api/stocks/2330/backtest", response.getBody().path());
        assertNotNull(response.getBody().timestamp());
    }

    @Test
    void shouldReturnStructuredBadRequestForMissingParameter() {
        MockHttpServletRequest request = request("/api/stocks/compare");
        MissingServletRequestParameterException exception =
                new MissingServletRequestParameterException("symbols", "String");

        ResponseEntity<ApiErrorResponse> response = handler.handleMissingParameter(exception, request);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("MISSING_PARAMETER", response.getBody().code());
        assertEquals("缺少必要參數：symbols。", response.getBody().message());
    }

    @Test
    void shouldHideUnexpectedExceptionDetails() {
        MockHttpServletRequest request = request("/api/stocks/2330/profile");

        ResponseEntity<ApiErrorResponse> response =
                handler.handleUnexpected(new IllegalStateException("資料庫密碼 secret"), request);

        assertEquals(500, response.getStatusCode().value());
        assertEquals("INTERNAL_ERROR", response.getBody().code());
        assertEquals("系統暫時無法處理此請求，請稍後再試。", response.getBody().message());
        assertEquals("/api/stocks/2330/profile", response.getBody().path());
    }

    @Test
    void shouldReturnStructuredBadRequestForMalformedBody() {
        MockHttpServletRequest request = request("/api/cross/batch");

        ResponseEntity<ApiErrorResponse> response = handler.handleUnreadableMessage(
                new HttpMessageNotReadableException(
                        "broken json",
                        new MockHttpInputMessage(new byte[0])),
                request);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("MALFORMED_REQUEST_BODY", response.getBody().code());
        assertEquals("請求內容缺失或 JSON 格式不正確。", response.getBody().message());
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        return request;
    }
}
