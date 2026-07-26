package org.gtalent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FinMindClientGenericDataTest {

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
    void shouldParseMarginDataWithRequestedType() {
        expectDataset("TaiwanStockMarginPurchaseShortSale", """
                {"status":200,"data":[{
                  "date":"2026-07-25","stock_id":"2330",
                  "margin_purchase_today":120,"margin_sale_today":45,"margin_purchase_balance":1500,
                  "short_sale_today":30,"short_sale_buy_today":10,"short_sale_balance":500,
                  "borrow_sell_today":25,"borrow_sell_buy_today":5,"borrow_sell_balance":300
                }]}
                """);

        List<MarginPurchaseShortSaleData> result = client.fetchMarginData("2330", "2026-07-01");

        assertEquals(1, result.size());
        assertEquals(1_500L, result.get(0).getMarginPurchaseBalance());
        assertEquals(75L, result.get(0).calculateMarginNetBuy());
        assertEquals(20L, result.get(0).calculateBorrowSellNet());
        server.verify();
    }

    @Test
    void shouldParseStockHoldingDataWithRequestedType() {
        expectDataset("TaiwanStockShareholding", """
                {"status":200,"data":[{
                  "date":"2026-07-25","stock_id":"2330",
                  "holding_stock_number":800000,"holding_stock_holder":12000,"holding_stock_ratio":65.5,
                  "foreign_investor_1000_shares_over_number":150,
                  "foreign_investor_1000_shares_over_holding_ratio":55.5,
                  "investor_10_shares_or_less_holding_ratio":12.5
                }]}
                """);

        List<StockHoldingData> result = client.fetchStockHoldingData("2330", "2026-07-01");

        assertEquals(1, result.size());
        assertEquals(800_000L, result.get(0).getHoldingStockNumber());
        assertEquals(55.5, result.get(0).calculateLargeHolderConcentration());
        assertEquals(true, result.get(0).isChipHighlyConcentrated());
        server.verify();
    }

    @Test
    void shouldParseDividendDataWithRequestedType() {
        expectDataset("TaiwanStockDividend", """
                {"status":200,"data":[{
                  "stock_id":"2330","year":2026,"cash_dividend":5.5,"stock_dividend":0.5,
                  "total_divididend":6.0,"ex_dividend_date":"2026-07-30","has_dividend":true
                }]}
                """);

        List<StockDividendData> result = client.fetchDividendData("2330", "2026-01-01");

        assertEquals(1, result.size());
        assertEquals(5.5, result.get(0).getCashDividend());
        assertEquals(5.5, result.get(0).calculateYieldRate(100.0));
        assertEquals(6.0, result.get(0).calculateTotalDividendAmount());
        server.verify();
    }

    @Test
    void shouldParseDayTradingDataWithRequestedType() {
        expectDataset("TaiwanStockDayTrading", """
                {"status":200,"data":[{
                  "date":"2026-07-25","stock_id":"2330",
                  "day_trading_trading_volume":60000,
                  "day_trading_buy_amount":120000000,
                  "day_trading_sell_amount":100000000
                }]}
                """);

        List<StockDayTradingData> result = client.fetchDayTradingData("2330", "2026-07-01");

        assertEquals(1, result.size());
        assertEquals(60_000L, result.get(0).getDayTradingVolume());
        assertEquals(20_000_000L, result.get(0).calculateDayTradingNetAmount());
        assertEquals(60.0, result.get(0).calculateDayTradingRatio(100_000L));
        server.verify();
    }

    @Test
    void shouldReturnEmptyWithoutRequestForBlankArguments() {
        assertEquals(List.of(), client.fetchMarginData(" ", "2026-07-01"));
        assertEquals(List.of(), client.fetchDividendData("2330", " "));
        server.verify();
    }

    private void expectDataset(String dataset, String responseBody) {
        server.expect(once(), request -> {
                    String query = request.getURI().getRawQuery();
                    if (query == null
                            || !query.contains("dataset=" + dataset)
                            || !query.contains("data_id=2330")
                            || !query.contains("token=test-token")) {
                        throw new AssertionError("Unexpected FinMind generic URL: " + request.getURI());
                    }
                })
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
    }
}
