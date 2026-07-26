package org.gtalent;

import org.gtalent.dto.BollingerResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndicatorCalculatorRepositoryTest {

    @Test
    void shouldCalculateFlatBollingerBandsFromRepositoryHistory() {
        StockDataRepository repository = mock(StockDataRepository.class);
        IndicatorCalculator calculator = new IndicatorCalculator(repository);
        List<StockDataPoint> history = new ArrayList<>();
        for (int day = 1; day <= 20; day++) {
            history.add(new StockDataPoint("2026-07-" + String.format("%02d", day), 100.0));
        }
        when(repository.getFullHistory("2330", 20)).thenReturn(history);

        BollingerResult result = calculator.calculateBollinger("2330");

        assertEquals(100.0, result.getUpperBand());
        assertEquals(100.0, result.getMiddleBand());
        assertEquals(100.0, result.getLowerBand());
        assertEquals(0.0, result.getBandwidth());
        verify(repository).getFullHistory("2330", 20);
    }

    @Test
    void shouldCalculateVwapFromRepositoryPriceAndVolume() {
        StockDataRepository repository = mock(StockDataRepository.class);
        IndicatorCalculator calculator = new IndicatorCalculator(repository);
        when(repository.getFullHistory("2330", 20)).thenReturn(List.of(
                new StockDataPoint("2026-07-23", 100.0, 100L),
                new StockDataPoint("2026-07-24", 110.0, 300L)
        ));

        double result = calculator.calculateVWAP("2330");

        assertEquals(107.5, result);
        verify(repository).getFullHistory("2330", 20);
    }

    @Test
    void shouldCalculateRsiFromRepositoryHistory() {
        StockDataRepository repository = mock(StockDataRepository.class);
        IndicatorCalculator calculator = new IndicatorCalculator(repository);
        List<StockDataPoint> history = new ArrayList<>();
        for (int day = 1; day <= 15; day++) {
            history.add(new StockDataPoint(
                    "2026-07-" + String.format("%02d", day),
                    100.0 + day));
        }
        when(repository.getFullHistory("2330", 15)).thenReturn(history);

        double result = calculator.calculateRSI(" 2330 ", 14);

        assertEquals(100.0, result);
        verify(repository).getFullHistory("2330", 15);
    }

    @Test
    void shouldCalculateFlatMacdAndSeriesFromRepositoryHistory() {
        StockDataRepository repository = mock(StockDataRepository.class);
        IndicatorCalculator calculator = new IndicatorCalculator(repository);
        List<StockDataPoint> history = new ArrayList<>();
        for (int day = 1; day <= 35; day++) {
            history.add(new StockDataPoint("2026-06-" + String.format("%02d", day), 100.0));
        }
        when(repository.getFullHistory("2330", 35)).thenReturn(history);

        MACDResult current = calculator.calculateMACD("2330");
        List<MACDResult> series = calculator.calculateMACDSeries("2330", 3);

        assertEquals(0.0, current.dif);
        assertEquals(0.0, current.dea);
        assertEquals(0.0, current.histogram);
        assertEquals(3, series.size());
        for (MACDResult item : series) {
            assertEquals(0.0, item.dif);
            assertEquals(0.0, item.dea);
            assertEquals(0.0, item.histogram);
        }
        verify(repository, org.mockito.Mockito.times(2)).getFullHistory("2330", 35);
    }

    @Test
    void shouldCalculateNeutralKdFromFlatRepositoryHistory() {
        StockDataRepository repository = mock(StockDataRepository.class);
        IndicatorCalculator calculator = new IndicatorCalculator(repository);
        List<StockDataPoint> history = new ArrayList<>();
        for (int day = 1; day <= 15; day++) {
            history.add(new StockDataPoint(
                    "2026-07-" + String.format("%02d", day),
                    100.0, 100.0, 100.0, 100.0));
        }
        when(repository.getFullHistory("2330", 15)).thenReturn(history);

        List<KDResult> result = calculator.calculateKD(" 2330 ", 1);

        assertEquals(1, result.size());
        assertEquals(50.0, result.get(0).getK(), 1.0e-9);
        assertEquals(50.0, result.get(0).getD(), 1.0e-9);
        verify(repository).getFullHistory("2330", 15);
    }

    @Test
    void shouldCalculateFlatBbwSeriesFromRepositoryHistory() {
        StockDataRepository repository = mock(StockDataRepository.class);
        IndicatorCalculator calculator = new IndicatorCalculator(repository);
        List<StockDataPoint> history = new ArrayList<>();
        for (int day = 1; day <= 27; day++) {
            history.add(new StockDataPoint("2026-06-" + String.format("%02d", day), 100.0));
        }
        when(repository.getFullHistory("2330", 27)).thenReturn(history);

        List<Double> result = calculator.calculateBBWSeries("2330", 2);

        assertEquals(List.of(0.0, 0.0), result);
        verify(repository).getFullHistory("2330", 27);
    }

    @Test
    void shouldCalculateStrongAdxFromRisingRepositoryHistory() {
        StockDataRepository repository = mock(StockDataRepository.class);
        IndicatorCalculator calculator = new IndicatorCalculator(repository);
        List<StockDataPoint> history = new ArrayList<>();
        for (int day = 1; day <= 20; day++) {
            double close = 100.0 + day;
            history.add(new StockDataPoint(
                    "2026-07-" + String.format("%02d", day),
                    close - 1.0, close + 1.0, close - 1.0, close, 1_000L));
        }
        when(repository.getFullHistory("2330", 20)).thenReturn(history);

        double result = calculator.calculateADX(" 2330 ", 5);

        assertEquals(100.0, result, 1.0e-9);
        verify(repository).getFullHistory("2330", 20);
    }

    @Test
    void shouldCalculateAtrFromFixedRepositoryRange() {
        StockDataRepository repository = mock(StockDataRepository.class);
        IndicatorCalculator calculator = new IndicatorCalculator(repository);
        List<StockDataPoint> history = new ArrayList<>();
        for (int day = 1; day <= 15; day++) {
            history.add(new StockDataPoint(
                    "2026-07-" + String.format("%02d", day),
                    100.0, 102.0, 98.0, 100.0, 1_000L));
        }
        when(repository.getFullHistory("2330", 15)).thenReturn(history);

        double result = calculator.calculateATR("2330", 5);

        assertEquals(4.0, result, 1.0e-9);
        verify(repository).getFullHistory("2330", 15);
    }

    @Test
    void shouldCalculatePositiveObvStrengthFromRisingRepositoryHistory() {
        StockDataRepository repository = mock(StockDataRepository.class);
        IndicatorCalculator calculator = new IndicatorCalculator(repository);
        List<StockDataPoint> history = new ArrayList<>();
        for (int day = 1; day <= 6; day++) {
            history.add(new StockDataPoint(
                    "2026-07-" + String.format("%02d", day),
                    100.0 + day, 1_000L));
        }
        when(repository.getFullHistory("2330", 6)).thenReturn(history);

        double result = calculator.calculateOBV(" 2330 ", 5);

        assertEquals(1.0, result, 1.0e-9);
        verify(repository).getFullHistory("2330", 6);
    }

    @Test
    void shouldCalculateOverboughtMfiFromRisingRepositoryHistory() {
        StockDataRepository repository = mock(StockDataRepository.class);
        IndicatorCalculator calculator = new IndicatorCalculator(repository);
        List<StockDataPoint> history = new ArrayList<>();
        for (int day = 1; day <= 10; day++) {
            double close = 100.0 + day;
            history.add(new StockDataPoint(
                    "2026-07-" + String.format("%02d", day),
                    close - 1.0, close + 1.0, close - 1.0, close, 1_000L));
        }
        when(repository.getFullHistory("2330", 10)).thenReturn(history);

        double result = calculator.calculateMFI("2330", 5);

        assertEquals(100.0, result, 1.0e-9);
        verify(repository).getFullHistory("2330", 10);
    }

    @Test
    void shouldCalculateSharedOhlcvIndicatorsFromRisingRepositoryHistory() {
        StockDataRepository repository = mock(StockDataRepository.class);
        IndicatorCalculator calculator = new IndicatorCalculator(repository);
        List<StockDataPoint> shortHistory = risingOhlcvHistory(10);
        List<StockDataPoint> longHistory = risingOhlcvHistory(35);
        when(repository.getFullHistory("2330", 10)).thenReturn(shortHistory);
        when(repository.getFullHistory("2330", 35)).thenReturn(longHistory);

        assertEquals(1, calculator.calculateSuperTrendDirection(" 2330 ", 5, 3.0));
        assertEquals(5.0 / 6.0, calculator.calculateDonchianPosition("2330", 5), 1.0e-9);
        assertEquals(0.0, calculator.calculateCMF("2330", 5), 1.0e-9);
        assertEquals(111.11111111111111, calculator.calculateCCI("2330", 5), 1.0e-9);
        assertEquals(-100.0 / 6.0, calculator.calculateWilliamsR("2330", 5), 1.0e-9);
        assertEquals(80.0, calculator.calculateAroonOscillator("2330", 5), 1.0e-9);
        verify(repository).getFullHistory("2330", 35);
        verify(repository, org.mockito.Mockito.times(5)).getFullHistory("2330", 10);
    }

    private static List<StockDataPoint> risingOhlcvHistory(int days) {
        List<StockDataPoint> history = new ArrayList<>();
        for (int day = 1; day <= days; day++) {
            double close = 100.0 + day;
            history.add(new StockDataPoint(
                    "2026-07-" + String.format("%02d", day),
                    close - 1.0, close + 1.0, close - 1.0, close, 1_000L));
        }
        return history;
    }
}
