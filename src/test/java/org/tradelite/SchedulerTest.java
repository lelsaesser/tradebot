package org.tradelite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.client.telegram.TelegramMessageProcessor;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.core.CoinGeckoPriceEvaluator;
import org.tradelite.core.FinnhubPriceEvaluator;
import org.tradelite.core.InsiderTracker;
import org.tradelite.core.RsiPriceFetcher;
import org.tradelite.service.ApiRequestMeteringService;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchedulerTest {

    @Mock
    private FinnhubPriceEvaluator finnhubPriceEvaluator;
    @Mock
    private CoinGeckoPriceEvaluator coinGeckoPriceEvaluator;
    @Mock
    private TargetPriceProvider targetPriceProvider;
    @Mock
    private TelegramClient telegramClient;
    @Mock
    private TelegramMessageProcessor telegramMessageProcessor;
    @Mock
    private RootErrorHandler rootErrorHandler;
    @Mock
    private InsiderTracker insiderTracker;
    @Mock
    private RsiPriceFetcher rsiPriceFetcher;
    @Mock
    private ApiRequestMeteringService apiRequestMeteringService;

    private Scheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new Scheduler(finnhubPriceEvaluator, coinGeckoPriceEvaluator, targetPriceProvider,
                telegramClient, telegramMessageProcessor, rootErrorHandler, insiderTracker, rsiPriceFetcher,
                apiRequestMeteringService);
    }

    @Test
    void marketMonitoring_marketOpen_shouldRun() throws Exception {
        scheduler.dayOfWeek = DayOfWeek.MONDAY;
        scheduler.localTime = LocalTime.of(17, 0);

        scheduler.marketMonitoring();

        verify(rootErrorHandler, times(2)).run(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(2)).run(captor.capture());

        for (ThrowingRunnable runnable : captor.getAllValues()) {
            runnable.run();
        }

        verify(finnhubPriceEvaluator, times(1)).evaluatePrice();
        verify(coinGeckoPriceEvaluator, times(1)).evaluatePrice();
    }

    @Test
    void marketMonitoring_marketClosed_shouldOnlyRunCrypto() throws Exception {
        scheduler.dayOfWeek = DayOfWeek.SATURDAY;
        scheduler.localTime = LocalTime.of(17, 0);

        scheduler.marketMonitoring();

        verify(rootErrorHandler, times(1)).run(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(1)).run(captor.capture());

        captor.getValue().run();

        verify(finnhubPriceEvaluator, times(0)).evaluatePrice();
        verify(coinGeckoPriceEvaluator, times(1)).evaluatePrice();
    }

    @Test
    void cleanupIgnoreSymbols_shouldRun() throws Exception {
        scheduler.cleanupIgnoreSymbols();

        verify(rootErrorHandler, times(1)).run(argThat(Objects::nonNull));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(1)).run(captor.capture());

        captor.getValue().run();

        verify(targetPriceProvider, times(1)).cleanupIgnoreSymbols(TargetPriceProvider.IGNORE_DURATION_TTL_SECONDS);
    }

    @Test
    void pollTelegramUpdates_shouldProcessUpdates() throws Exception {
        scheduler.pollTelegramChatUpdates();

        verify(telegramClient, times(1)).getChatUpdates();
        verify(rootErrorHandler, times(1)).run(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(1)).run(captor.capture());
        captor.getValue().run();

        verify(telegramMessageProcessor, times(1)).processUpdates(anyList());
    }

    @Test
    void weeklyInsiderTradingReport_sendsReport() throws Exception {
        scheduler.weeklyInsiderTradingReport();

        verify(rootErrorHandler, times(1)).run(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(1)).run(captor.capture());
        captor.getValue().run();

        verify(insiderTracker, times(1)).trackInsiderTransactions();
    }

    @Test
    void rsiStockMonitoring_shouldFetchStockPrices() throws Exception {
        scheduler.rsiStockMonitoring();

        verify(rootErrorHandler, times(1)).run(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(1)).run(captor.capture());
        captor.getValue().run();

        verify(rsiPriceFetcher, times(1)).fetchStockClosingPrices();
    }

    @Test
    void rsiCryptoMonitoring_shouldFetchCryptoPrices() throws Exception {
        scheduler.rsiCryptoMonitoring();

        verify(rootErrorHandler, times(1)).run(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(1)).run(captor.capture());
        captor.getValue().run();

        verify(rsiPriceFetcher, times(1)).fetchCryptoClosingPrices();
    }

    @Test
    void monthlyApiUsageReport_withRequests_shouldSendReportAndReset() throws Exception {
        // Setup: Mock API request counts
        when(apiRequestMeteringService.getFinnhubRequestCount()).thenReturn(150);
        when(apiRequestMeteringService.getCoingeckoRequestCount()).thenReturn(75);
        when(apiRequestMeteringService.getCurrentMonth()).thenReturn("2025-09");

        scheduler.monthlyApiUsageReport();

        verify(rootErrorHandler, times(1)).run(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(1)).run(captor.capture());
        captor.getValue().run();

        // Verify that counts were retrieved
        verify(apiRequestMeteringService, times(1)).getFinnhubRequestCount();
        verify(apiRequestMeteringService, times(1)).getCoingeckoRequestCount();
        verify(apiRequestMeteringService, times(1)).getCurrentMonth();

        // Verify Telegram message was sent
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient, times(1)).sendMessage(messageCaptor.capture());

        String sentMessage = messageCaptor.getValue();
        assertTrue(sentMessage.contains("📊 *Monthly API Usage Report - 2025-09*"));
        assertTrue(sentMessage.contains("🔹 *Finnhub API*: 150 requests"));
        assertTrue(sentMessage.contains("🔹 *CoinGecko API*: 75 requests"));
        assertTrue(sentMessage.contains("🔹 *Total*: 225 requests"));

        // Verify counters were reset
        verify(apiRequestMeteringService, times(1)).resetCounters();
    }

    @Test
    void monthlyApiUsageReport_withNoRequests_shouldSkipReportButStillReset() throws Exception {
        // Setup: Mock zero API request counts
        when(apiRequestMeteringService.getFinnhubRequestCount()).thenReturn(0);
        when(apiRequestMeteringService.getCoingeckoRequestCount()).thenReturn(0);

        scheduler.monthlyApiUsageReport();

        verify(rootErrorHandler, times(1)).run(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(1)).run(captor.capture());
        captor.getValue().run();

        // Verify that counts were retrieved
        verify(apiRequestMeteringService, times(1)).getFinnhubRequestCount();
        verify(apiRequestMeteringService, times(1)).getCoingeckoRequestCount();

        // Verify NO Telegram message was sent (since no requests)
        verify(telegramClient, never()).sendMessage(anyString());
        verify(apiRequestMeteringService, never()).getCurrentMonth();

        // Verify counters were still reset
        verify(apiRequestMeteringService, times(1)).resetCounters();
    }

    @Test
    void monthlyApiUsageReport_withOnlyFinnhubRequests_shouldSendReport() throws Exception {
        // Setup: Mock only Finnhub requests
        when(apiRequestMeteringService.getFinnhubRequestCount()).thenReturn(100);
        when(apiRequestMeteringService.getCoingeckoRequestCount()).thenReturn(0);
        when(apiRequestMeteringService.getCurrentMonth()).thenReturn("2025-08");

        scheduler.monthlyApiUsageReport();

        verify(rootErrorHandler, times(1)).run(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(1)).run(captor.capture());
        captor.getValue().run();

        // Verify Telegram message was sent (since finnhubCount > 0)
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient, times(1)).sendMessage(messageCaptor.capture());

        String sentMessage = messageCaptor.getValue();
        assertTrue(sentMessage.contains("📊 *Monthly API Usage Report - 2025-08*"));
        assertTrue(sentMessage.contains("🔹 *Finnhub API*: 100 requests"));
        assertTrue(sentMessage.contains("🔹 *CoinGecko API*: 0 requests"));
        assertTrue(sentMessage.contains("🔹 *Total*: 100 requests"));

        verify(apiRequestMeteringService, times(1)).resetCounters();
    }

    @Test
    void monthlyApiUsageReport_withOnlyCoingeckoRequests_shouldSendReport() throws Exception {
        // Setup: Mock only CoinGecko requests
        when(apiRequestMeteringService.getFinnhubRequestCount()).thenReturn(0);
        when(apiRequestMeteringService.getCoingeckoRequestCount()).thenReturn(50);
        when(apiRequestMeteringService.getCurrentMonth()).thenReturn("2025-07");

        scheduler.monthlyApiUsageReport();

        verify(rootErrorHandler, times(1)).run(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(1)).run(captor.capture());
        captor.getValue().run();

        // Verify Telegram message was sent (since coingeckoCount > 0)
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient, times(1)).sendMessage(messageCaptor.capture());

        String sentMessage = messageCaptor.getValue();
        assertTrue(sentMessage.contains("📊 *Monthly API Usage Report - 2025-07*"));
        assertTrue(sentMessage.contains("🔹 *Finnhub API*: 0 requests"));
        assertTrue(sentMessage.contains("🔹 *CoinGecko API*: 50 requests"));
        assertTrue(sentMessage.contains("🔹 *Total*: 50 requests"));

        verify(apiRequestMeteringService, times(1)).resetCounters();
    }
}
