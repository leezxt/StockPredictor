package org.gtalent;

import org.gtalent.dto.FinMindNavData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FinMindClientNavDataTest {

    private FinMindClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new FinMindClient(restTemplate);
        ReflectionTestUtils.setField(client, "apiUrl", "https://finmind.test/api/v4/data");
        ReflectionTestUtils.setField(client, "apiToken", "test-token");
        ReflectionTestUtils.setField(client, "autoLogin", false);
    }

    @Test
    void shouldParseAndSortNavHistory() {
        server.expect(once(), request -> {
                    String query = request.getURI().getRawQuery();
                    if (query == null
                            || !query.contains("dataset=TaiwanETFNavigation")
                            || !query.contains("data_id=0050")
                            || !query.contains("start_date=2026-07-01")
                            || !query.contains("token=test-token")) {
                        throw new AssertionError("Unexpected FinMind NAV URL: " + request.getURI());
                    }
                })
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "status": 200,
                          "data": [
                            {"date":"2026-07-25","stock_id":"0050","NAV":52.25},
                            {"date":"2026-07-23","stock_id":"0050","NAV":50.25},
                            {"date":"2026-07-24","stock_id":"0050","NAV":51.25}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<FinMindNavData> result = client.fetchNavData("0050", "2026-07-01");

        assertEquals(List.of("2026-07-23", "2026-07-24", "2026-07-25"),
                result.stream().map(FinMindNavData::getDate).toList());
        assertEquals("0050", result.get(0).getStockId());
        assertEquals(50.25, result.get(0).getNav());
        assertEquals((49.50 - 50.25) / 50.25, result.get(0).calculatePremiumDiscount(49.50));
        server.verify();
    }

    @Test
    void shouldReturnEmptyWithoutRequestForBlankSymbol() {
        assertEquals(List.of(), client.fetchNavData(" ", "2026-07-01"));
        server.verify();
    }

    @Test
    void shouldReturnEmptyWhenNavRequestFails() {
        server.expect(once(), request -> assertEquals("finmind.test", request.getURI().getHost()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        assertEquals(List.of(), client.fetchNavData("0050", "2026-07-01"));
        server.verify();
    }
}
