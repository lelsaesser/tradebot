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

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

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

    private Scheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new Scheduler(finnhubPriceEvaluator, coinGeckoPriceEvaluator, targetPriceProvider,
                telegramClient, telegramMessageProcessor, rootErrorHandler, insiderTracker, rsiPriceFetcher);
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
}
