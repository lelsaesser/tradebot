package org.tradelite.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.repository.ApiMeteringRecord;
import org.tradelite.repository.ApiMeteringRepository;

@ExtendWith(MockitoExtension.class)
class ApiRequestMeteringServiceTest {

    @Mock private ApiMeteringRepository repository;

    private ApiRequestMeteringService meteringService;

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    @BeforeEach
    void setUp() {
        when(repository.findAll()).thenReturn(List.of());
        meteringService = new ApiRequestMeteringService(repository);
    }

    @Test
    void testIncrementFinnhubRequests() {
        assertEquals(0, meteringService.getFinnhubRequestCount());

        meteringService.incrementFinnhubRequests();
        assertEquals(1, meteringService.getFinnhubRequestCount());

        meteringService.incrementFinnhubRequests();
        assertEquals(2, meteringService.getFinnhubRequestCount());
    }

    @Test
    void testIncrementCoingeckoRequests() {
        assertEquals(0, meteringService.getCoingeckoRequestCount());

        meteringService.incrementCoingeckoRequests();
        assertEquals(1, meteringService.getCoingeckoRequestCount());

        meteringService.incrementCoingeckoRequests();
        assertEquals(2, meteringService.getCoingeckoRequestCount());
    }

    @Test
    void testIncrementTwelveDataRequests() {
        assertEquals(0, meteringService.getTwelveDataRequestCount());

        meteringService.incrementTwelveDataRequests();
        assertEquals(1, meteringService.getTwelveDataRequestCount());

        meteringService.incrementTwelveDataRequests();
        assertEquals(2, meteringService.getTwelveDataRequestCount());
    }

    @Test
    void testIncrementYahooRequests() {
        assertEquals(0, meteringService.getYahooRequestCount());

        meteringService.incrementYahooRequests();
        assertEquals(1, meteringService.getYahooRequestCount());

        meteringService.incrementYahooRequests();
        assertEquals(2, meteringService.getYahooRequestCount());
    }

    @Test
    void testCountersIndependent() {
        meteringService.incrementFinnhubRequests();
        meteringService.incrementFinnhubRequests();
        meteringService.incrementCoingeckoRequests();
        meteringService.incrementTwelveDataRequests();
        meteringService.incrementTwelveDataRequests();
        meteringService.incrementTwelveDataRequests();
        meteringService.incrementYahooRequests();

        assertEquals(2, meteringService.getFinnhubRequestCount());
        assertEquals(1, meteringService.getCoingeckoRequestCount());
        assertEquals(3, meteringService.getTwelveDataRequestCount());
        assertEquals(1, meteringService.getYahooRequestCount());
    }

    @Test
    void testGetRequestCountSummary() {
        meteringService.incrementFinnhubRequests();
        meteringService.incrementFinnhubRequests();
        meteringService.incrementCoingeckoRequests();
        meteringService.incrementTwelveDataRequests();
        meteringService.incrementTwelveDataRequests();
        meteringService.incrementTwelveDataRequests();
        meteringService.incrementYahooRequests();

        String summary = meteringService.getRequestCountSummary();
        String currentMonth = LocalDateTime.now().format(MONTH_FORMATTER);

        assertTrue(summary.contains(currentMonth));
        assertTrue(summary.contains("Finnhub: 2"));
        assertTrue(summary.contains("CoinGecko: 1"));
        assertTrue(summary.contains("TwelveData: 3"));
        assertTrue(summary.contains("Yahoo: 1"));
    }

    @Test
    void testFlushCounters_persistsAllCounters() {
        meteringService.incrementFinnhubRequests();
        meteringService.incrementCoingeckoRequests();
        meteringService.incrementCoingeckoRequests();

        meteringService.flushCounters();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ApiMeteringRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());

        List<ApiMeteringRecord> flushed = captor.getValue();
        assertEquals(4, flushed.size());

        String currentMonth = LocalDateTime.now().format(MONTH_FORMATTER);
        for (ApiMeteringRecord meteringRecord : flushed) {
            assertEquals(currentMonth, meteringRecord.month());
            assertNotNull(meteringRecord.lastUpdated());
        }
    }

    @Test
    void testResetCounters_zerosAndFlushes() {
        meteringService.incrementFinnhubRequests();
        meteringService.incrementFinnhubRequests();
        meteringService.incrementCoingeckoRequests();
        meteringService.incrementTwelveDataRequests();
        meteringService.incrementYahooRequests();

        meteringService.resetCounters();

        assertEquals(0, meteringService.getFinnhubRequestCount());
        assertEquals(0, meteringService.getCoingeckoRequestCount());
        assertEquals(0, meteringService.getTwelveDataRequestCount());
        assertEquals(0, meteringService.getYahooRequestCount());

        // Verify flush was called (saveAll invoked)
        verify(repository).saveAll(anyList());
    }

    @Test
    void testInitializationFromRepository_currentMonth() {
        String currentMonth = LocalDateTime.now().format(MONTH_FORMATTER);
        LocalDateTime now = LocalDateTime.now();

        when(repository.findAll())
                .thenReturn(
                        List.of(
                                new ApiMeteringRecord("finnhub", currentMonth, 42, now),
                                new ApiMeteringRecord("coingecko", currentMonth, 17, now),
                                new ApiMeteringRecord("twelvedata", currentMonth, 99, now),
                                new ApiMeteringRecord("yahoo", currentMonth, 5, now)));

        ApiRequestMeteringService service = new ApiRequestMeteringService(repository);

        assertEquals(42, service.getFinnhubRequestCount());
        assertEquals(17, service.getCoingeckoRequestCount());
        assertEquals(99, service.getTwelveDataRequestCount());
        assertEquals(5, service.getYahooRequestCount());
    }

    @Test
    void testInitializationFromRepository_staleMonth_resetsToZero() {
        String oldMonth = "2024-01";
        LocalDateTime now = LocalDateTime.now();

        when(repository.findAll())
                .thenReturn(
                        List.of(
                                new ApiMeteringRecord("finnhub", oldMonth, 5000, now),
                                new ApiMeteringRecord("coingecko", oldMonth, 3000, now)));

        ApiRequestMeteringService service = new ApiRequestMeteringService(repository);

        assertEquals(0, service.getFinnhubRequestCount());
        assertEquals(0, service.getCoingeckoRequestCount());
    }

    @Test
    void testInitializationFromRepository_unknownProvider_ignored() {
        String currentMonth = LocalDateTime.now().format(MONTH_FORMATTER);
        LocalDateTime now = LocalDateTime.now();

        when(repository.findAll())
                .thenReturn(
                        List.of(new ApiMeteringRecord("unknown_provider", currentMonth, 100, now)));

        // Should not throw
        ApiRequestMeteringService service = new ApiRequestMeteringService(repository);

        assertEquals(0, service.getFinnhubRequestCount());
    }

    @Test
    void testShutdown_flushesCounters() {
        meteringService.incrementFinnhubRequests();

        meteringService.shutdown();

        verify(repository).saveAll(anyList());
    }

    @Test
    void testGetCurrentMonth() {
        String currentMonth = meteringService.getCurrentMonth();
        String expectedMonth = LocalDateTime.now().format(MONTH_FORMATTER);
        assertEquals(expectedMonth, currentMonth);
    }

    @Test
    void testGetPreviousMonth() {
        String previousMonth = meteringService.getPreviousMonth();
        String expectedMonth = LocalDateTime.now().minusMonths(1).format(MONTH_FORMATTER);
        assertEquals(expectedMonth, previousMonth);
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        final int numThreads = 12;
        final int incrementsPerThread = 100;
        Thread[] threads = new Thread[numThreads];

        for (int i = 0; i < numThreads; i++) {
            final int threadIndex = i;
            threads[i] =
                    new Thread(
                            () -> {
                                for (int j = 0; j < incrementsPerThread; j++) {
                                    if (threadIndex % 4 == 0) {
                                        meteringService.incrementFinnhubRequests();
                                    } else if (threadIndex % 4 == 1) {
                                        meteringService.incrementCoingeckoRequests();
                                    } else if (threadIndex % 4 == 2) {
                                        meteringService.incrementTwelveDataRequests();
                                    } else {
                                        meteringService.incrementYahooRequests();
                                    }
                                }
                            });
        }

        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        int expectedPerProvider = (numThreads / 4) * incrementsPerThread;
        assertEquals(expectedPerProvider, meteringService.getFinnhubRequestCount());
        assertEquals(expectedPerProvider, meteringService.getCoingeckoRequestCount());
        assertEquals(expectedPerProvider, meteringService.getTwelveDataRequestCount());
        assertEquals(expectedPerProvider, meteringService.getYahooRequestCount());
    }
}
