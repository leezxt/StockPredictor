package org.gtalent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HistoryBackfillServiceTest {
    private final TwseService twseService = mock(TwseService.class);
    private final StockHistoryStore historyStore = mock(StockHistoryStore.class);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final HistoryBackfillService service =
            new HistoryBackfillService(twseService, historyStore, executor);

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    void shouldCompleteImmediatelyWhenLocalHistoryIsReady() {
        when(historyStore.getRecentHistory("2330", 2)).thenReturn(validHistory(2));

        HistoryBackfillService.BackfillTaskSnapshot snapshot =
                service.requestBackfill("2330", 2, false);

        assertNotNull(snapshot);
        assertEquals("COMPLETED", snapshot.status());
        assertTrue(snapshot.dataReady());
        assertEquals(100, snapshot.progressPct());
    }

    @Test
    void shouldBeCreatedBySpringWithProductionConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(TwseService.class, () -> twseService);
            context.registerBean(StockHistoryStore.class, () -> historyStore);
            context.register(HistoryBackfillService.class);
            context.refresh();

            assertNotNull(context.getBean(HistoryBackfillService.class));
        }
    }

    @Test
    void shouldDeduplicateRequestsAndUpgradeRequiredDays() throws Exception {
        AtomicBoolean saved = new AtomicBoolean();
        CountDownLatch fetchStarted = new CountDownLatch(1);
        CountDownLatch allowFetchToFinish = new CountDownLatch(1);

        when(historyStore.getRecentHistory(anyString(), anyInt())).thenAnswer(invocation ->
                saved.get() ? validHistory(invocation.getArgument(1)) : Collections.emptyList());
        when(twseService.fetchMonthlyData(anyString(), anyString())).thenAnswer(invocation -> {
            fetchStarted.countDown();
            assertTrue(allowFetchToFinish.await(2, TimeUnit.SECONDS));
            return List.of(new String[]{"row"});
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            saved.set(true);
            return null;
        }).when(historyStore).saveMonthlyHistory(anyString(), org.mockito.ArgumentMatchers.anyList());

        service.requestBackfill("2330", 5, false);
        assertTrue(fetchStarted.await(2, TimeUnit.SECONDS));

        HistoryBackfillService.BackfillTaskSnapshot upgraded =
                service.requestBackfill("2330", 20, true);
        assertEquals(20, upgraded.requiredDays());
        assertTrue(upgraded.requireFullHistory());

        when(historyStore.getFullHistory(anyString(), anyInt())).thenAnswer(invocation ->
                saved.get() ? validHistory(invocation.getArgument(1)) : Collections.emptyList());
        allowFetchToFinish.countDown();

        HistoryBackfillService.BackfillTaskSnapshot completed = awaitTerminal("2330");
        assertEquals("COMPLETED", completed.status());
        assertTrue(completed.dataReady());
        verify(twseService).fetchMonthlyData(anyString(), anyString());
    }

    private HistoryBackfillService.BackfillTaskSnapshot awaitTerminal(String symbol) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        HistoryBackfillService.BackfillTaskSnapshot snapshot;
        do {
            snapshot = service.getTask(symbol);
            if (snapshot != null
                    && ("COMPLETED".equals(snapshot.status()) || "FAILED".equals(snapshot.status()))) {
                return snapshot;
            }
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);
        return service.getTask(symbol);
    }

    private static List<StockDataPoint> validHistory(int size) {
        return java.util.stream.IntStream.range(0, size)
                .mapToObj(index -> new StockDataPoint(
                        "2026-01-" + String.format("%02d", index + 1),
                        100,
                        105,
                        95,
                        102,
                        1_000
                ))
                .toList();
    }
}
