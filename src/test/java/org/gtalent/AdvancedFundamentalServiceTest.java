package org.gtalent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdvancedFundamentalServiceTest {
    @Mock
    private FundamentalService fundamentalService;

    @Mock
    private FinMindClient finMindClient;

    @Mock
    private FinancialDataRepository financialDataRepository;

    @Test
    void shouldReturnCompleteQuarterCacheWithoutCallingFinMind() {
        List<FinancialQuarterData> cached = List.of(
                quarter("2025-09-30", 58, 47, 40),
                quarter("2025-12-31", 59, 48, 41));
        when(financialDataRepository.getFinancialQuarterHistory("2330", 2)).thenReturn(cached);

        List<FinancialQuarterData> result = createService().getQuarterHistory("2330", 1);

        assertSame(cached, result);
        verify(finMindClient, never()).fetchFinancialStatements(anyString(), anyString());
        verify(finMindClient, never()).fetchBalanceSheet(anyString(), anyString());
    }

    @Test
    void shouldPersistQuarterDataFetchedFromFinMind() {
        when(financialDataRepository.getFinancialQuarterHistory("2330", 2)).thenReturn(List.of());
        when(finMindClient.fetchFinancialStatements(anyString(), anyString())).thenReturn(List.of(
                row("2025-12-31", "GrossProfitMargin", 59),
                row("2025-12-31", "OperatingProfitMargin", 48),
                row("2025-12-31", "NetProfitMargin", 41)));
        when(finMindClient.fetchBalanceSheet(anyString(), anyString())).thenReturn(List.of());

        List<FinancialQuarterData> result = createService().getQuarterHistory("2330", 2);

        assertEquals(1, result.size());
        assertEquals("2025-12-31", result.get(0).getQuarterDate());
        assertEquals(59, result.get(0).getGrossProfitMargin());
        verify(financialDataRepository).saveFinancialQuarterData(
                org.mockito.ArgumentMatchers.eq("2330"),
                org.mockito.ArgumentMatchers.argThat(data ->
                        "2025-12-31".equals(data.getQuarterDate())
                                && data.getOperatingProfitMargin() == 48
                                && data.getNetProfitMargin() == 41));
    }

    @Test
    void shouldAwardEightPointsWhenAllThreeMarginsRise() {
        List<FinMindFinancialData> data = List.of(
                financial("2025-12-31", "NetProfitMargin", 41),
                financial("2025-09-30", "GrossProfitMargin", 58),
                financial("2025-12-31", "GrossProfitMargin", 59),
                financial("2025-09-30", "OperatingProfitMargin", 47),
                financial("2025-09-30", "NetProfitMargin", 40),
                financial("2025-12-31", "OperatingProfitMargin", 48));

        assertEquals(8, createService().checkTripleRiseScore(data));
    }

    @Test
    void shouldReturnZeroWhenAnyMarginDoesNotRise() {
        List<FinMindFinancialData> data = List.of(
                financial("2025-09-30", "GrossProfitMargin", 58),
                financial("2025-09-30", "OperatingProfitMargin", 47),
                financial("2025-09-30", "NetProfitMargin", 40),
                financial("2025-12-31", "GrossProfitMargin", 59),
                financial("2025-12-31", "OperatingProfitMargin", 47),
                financial("2025-12-31", "NetProfitMargin", 41));

        assertEquals(0, createService().checkTripleRiseScore(data));
    }

    @Test
    void shouldRecognizeChineseMarginTypes() {
        List<FinMindFinancialData> data = List.of(
                financial("2025-09-30", "毛利率", 58),
                financial("2025-09-30", "營業利益率", 47),
                financial("2025-09-30", "淨利率", 40),
                financial("2025-12-31", "毛利率", 59),
                financial("2025-12-31", "營業利益率", 48),
                financial("2025-12-31", "淨利率", 41));

        assertEquals(8, createService().checkTripleRiseScore(data));
    }

    @Test
    void shouldReturnZeroForEmptyOrSingleQuarterData() {
        assertEquals(0, createService().checkTripleRiseScore(List.of()));
        assertEquals(0, createService().checkTripleRiseScore(List.of(
                financial("2025-12-31", "GrossProfitMargin", 59),
                financial("2025-12-31", "OperatingProfitMargin", 48),
                financial("2025-12-31", "NetProfitMargin", 41))));
    }

    private AdvancedFundamentalService createService() {
        return new AdvancedFundamentalService(
                fundamentalService,
                finMindClient,
                financialDataRepository);
    }

    private FinancialQuarterData quarter(String date, double gross, double operating, double net) {
        return new FinancialQuarterData(date, gross, operating, net, 30, 1_000);
    }

    private FinMindRawFinancialRow row(String date, String type, double value) {
        FinMindRawFinancialRow row = new FinMindRawFinancialRow();
        row.setDate(date);
        row.setStockId("2330");
        row.setType(type);
        row.setValue(value);
        return row;
    }

    private FinMindFinancialData financial(String date, String type, double value) {
        return new FinMindFinancialData(date, "2330", type, value);
    }
}
