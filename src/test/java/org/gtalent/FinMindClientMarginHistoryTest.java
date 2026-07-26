package org.gtalent;

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

class FinMindClientMarginHistoryTest {

    private FinMindClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new FinMindClient(restTemplate);
        ReflectionTestUtils.setField(client, "apiUrl", "https://finmind.test/api/v4/data");
        ReflectionTestUtils.setField(client, "apiToken", "test-token");
    }

    @Test
    void shouldParseSortAndKeepLatestRequestedTradingDays() {
        server.expect(once(), request -> {
                    String query = request.getURI().getRawQuery();
                    if (query == null
                            || !query.contains("dataset=TaiwanStockMarginPurchaseShortSale")
                            || !query.contains("data_id=2330")
                            || !query.contains("token=test-token")) {
                        throw new AssertionError("Unexpected FinMind margin URL: " + request.getURI());
                    }
                })
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "status": 200,
                          "data": [
                            {"date":"2026-07-25","stock_id":"2330","MarginPurchaseBuy":30,"MarginPurchaseSell":10,"MarginPurchaseLimit":1200,"ShortSaleBuy":8,"ShortSaleSell":5,"ShortSaleLimit":400},
                            {"date":"2026-07-23","stock_id":"2330","MarginPurchaseBuy":10,"MarginPurchaseSell":5,"MarginPurchaseLimit":1000,"ShortSaleBuy":4,"ShortSaleSell":2,"ShortSaleLimit":300},
                            {"date":"2026-07-24","stock_id":"2330","MarginPurchaseBuy":20,"MarginPurchaseSell":8,"MarginPurchaseLimit":1100,"ShortSaleBuy":6,"ShortSaleSell":3,"ShortSaleLimit":350}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<FinMindMarginData> result = client.fetchMarginHistory("2330", 2);

        assertEquals(List.of("2026-07-24", "2026-07-25"),
                result.stream().map(FinMindMarginData::getDate).toList());
        assertEquals(1_100, result.get(0).getMarginPurchaseLimit());
        assertEquals(350, result.get(0).getShortSaleLimit());
        assertEquals(20, result.get(1).getMarginPurchaseNetChange());
        assertEquals(3, result.get(1).getShortSaleNetChange());
        server.verify();
    }

    @Test
    void shouldReturnEmptyWithoutRequestForBlankSymbol() {
        assertEquals(List.of(), client.fetchMarginHistory("  ", 10));
        server.verify();
    }

    @Test
    void shouldReturnEmptyWhenFinMindRequestFails() {
        server.expect(once(), request -> assertEquals("finmind.test", request.getURI().getHost()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        assertEquals(List.of(), client.fetchMarginHistory("2330", 10));
        server.verify();
    }
}
