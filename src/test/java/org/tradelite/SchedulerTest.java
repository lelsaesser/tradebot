package org.tradelite;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.client.telegram.TelegramMessageProcessor;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.core.CoinGeckoPriceEvaluator;
import org.tradelite.core.FinnhubPriceEvaluator;
import org.tradelite.core.InsiderTracker;
import org.tradelite.core.RelativeStrengthTracker;
import org.tradelite.core.RsiPriceFetcher;
import org.tradelite.core.SectorMomentumRocTracker;
import org.tradelite.core.SectorRelativeStrengthTracker;
import org.tradelite.core.SectorRotationTracker;
import org.tradelite.core.YahooOhlcvFetcher;
import org.tradelite.quant.BollingerBandTracker;
import org.tradelite.quant.EmaTracker;
import org.tradelite.quant.TailRiskTracker;
import org.tradelite.service.ApiRequestMeteringService;
import org.tradelite.service.RsiService;

@ExtendWith(MockitoExtension.class)
class SchedulerTest {

    @Mock private FinnhubPriceEvaluator finnhubPriceEvaluator;
    @Mock private CoinGeckoPriceEvaluator coinGeckoPriceEvaluator;
    @Mock private TargetPriceProvider targetPriceProvider;
    @Mock private TelegramGateway telegramClient;
    @Mock private TelegramMessageProcessor telegramMessageProcessor;
    @Mock private RootErrorHandler rootErrorHandler;
    @Mock private InsiderTracker insiderTracker;
    @Mock private RsiPriceFetcher rsiPriceFetcher;
    @Mock private ApiRequestMeteringService apiRequestMeteringService;
    @Mock private SectorRotationTracker sectorRotationTracker;
    @Mock private RelativeStrengthTracker relativeStrengthTracker;
    @Mock private SectorRelativeStrengthTracker sectorRelativeStrengthTracker;
    @Mock private SectorMomentumRocTracker sectorMomentumRocTracker;
    @Mock private TailRiskTracker tailRiskTracker;
    @Mock private BollingerBandTracker bollingerBandTracker;
    @Mock private RsiService rsiService;
    @Mock private EmaTracker emaTracker;
    @Mock private YahooOhlcvFetcher yahooOhlcvFetcher;

    private Scheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler =
                new Scheduler(
                        finnhubPriceEvaluator,
                        coinGeckoPriceEvaluator,
                        targetPriceProvider,
                        telegramClient,
                        telegramMessageProcessor,
                        rootErrorHandler,
                        insiderTracker,
                        rsiPriceFetcher,
                        apiRequestMeteringService,
                        sectorRotationTracker,
                        relativeStrengthTracker,
                        sectorRelativeStrengthTracker,
                        sectorMomentumRocTracker,
                        tailRiskTracker,
                        bollingerBandTracker,
                        rsiService,
                        emaTracker,
                        yahooOhlcvFetcher);
    }

    @Test
    void stockMarketMonitoring_marketOpen_shouldRun() throws Exception {
        // Monday 11:00 AM NY time = market open
        scheduler.marketDateTime =
                ZonedDateTime.of(2026, 3, 30, 11, 0, 0, 0, ZoneId.of("America/New_York"));

        scheduler.stockMarketMonitoring();

        // Called 3 times: finnhubPriceEvaluator, sectorRelativeStrengthTracker,
        // sectorMomentumRocTracker (BB moved to hourly schedule)
        verify(rootErrorHandler, times(3)).run(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(3)).run(captor.capture());

        // Execute all captured runnables
        for (ThrowingRunnable runnable : captor.getAllValues()) {
            runnable.run();
        }

        verify(finnhubPriceEvaluator, times(1)).evaluatePrice();
        verify(sectorRelativeStrengthTracker, times(1)).analyzeAndSendAlerts();
        verify(sectorMomentumRocTracker, times(1)).analyzeAndSendAlerts();
        verify(bollingerBandTracker, never()).analyzeAndSendAlerts();
        verify(coinGeckoPriceEvaluator, times(0)).evaluatePrice();
    }

    @Test
    void stockMarketMonitoring_marketClosed_shouldNotRun() throws Exception {
        // Saturday 11:00 AM NY time = market closed (weekend)
        scheduler.marketDateTime =
                ZonedDateTime.of(2026, 3, 28, 11, 0, 0, 0, ZoneId.of("America/New_York"));

        scheduler.stockMarketMonitoring();

        verify(rootErrorHandler, never()).run(any(ThrowingRunnable.class));
        verify(finnhubPriceEvaluator, never()).evaluatePrice();
        verify(coinGeckoPriceEvaluator, never()).evaluatePrice();
    }

    @Test
    void hourlySignalMonitoring_marketOpen_shouldRun() throws Exception {
        // Monday 11:00 AM NY time = market open
        scheduler.marketDateTime =
                ZonedDateTime.of(2026, 3, 30, 11, 0, 0, 0, ZoneId.of("America/New_York"));

        scheduler.hourlySignalMonitoring();

        // Called 3 times: bollingerBandTracker, rsiService, yahooOhlcvFetcher
        verify(rootErrorHandler, times(3)).run(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(3)).run(captor.capture());

        for (ThrowingRunnable runnable : captor.getAllValues()) {
            runnable.run();
        }

        verify(bollingerBandTracker, times(1)).analyzeAndSendAlerts();
        verify(rsiService, times(1)).sendRsiReport();
        verify(yahooOhlcvFetcher, times(1)).fetchAndBackfillOhlcv();
    }

    @Test
    void hourlySignalMonitoring_marketClosed_shouldNotRun() throws Exception {
        // Saturday 11:00 AM NY time = market closed (weekend)
        scheduler.marketDateTime =
                ZonedDateTime.of(2026, 3, 28, 11, 0, 0, 0, ZoneId.of("America/New_York"));

        scheduler.hourlySignalMonitoring();

        verify(rootErrorHandler, never()).run(any(ThrowingRunnable.class));
        verify(bollingerBandTracker, never()).analyzeAndSendAlerts();
        verify(rsiService, never()).sendRsiReport();
        verify(yahooOhlcvFetcher, never()).fetchAndBackfillOhlcv();
    }

    @Test
    void cryptoMarketMonitoring_shouldRun() throws Exception {
        scheduler.cryptoMarketMonitoring();

        verify(rootErrorHandler, times(1)).run(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(1)).run(captor.capture());

        captor.getValue().run();

        verify(coinGeckoPriceEvaluator, times(1)).evaluatePrice();
        verify(finnhubPriceEvaluator, never()).evaluatePrice();
    }

    @Test
    void cleanupIgnoreSymbols_shouldRun() throws Exception {
        scheduler.cleanupIgnoreSymbols();

        verify(rootErrorHandler, times(1)).run(argThat(Objects::nonNull));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(1)).run(captor.capture());

        captor.getValue().run();

        verify(targetPriceProvider, times(1))
                .cleanupIgnoreSymbols(TargetPriceProvider.IGNORE_DURATION_TTL_SECONDS);
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
    void rsiStockMonitoring_shouldFetchStockPricesAndAnalyzeRS() throws Exception {
        scheduler.rsiStockMonitoring();

        // Verify rootErrorHandler.run is called twice (once for RSI, once for RS)
        verify(rootErrorHandler, times(2)).run(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(2)).run(captor.capture());

        // Execute both captured runnables
        for (ThrowingRunnable runnable : captor.getAllValues()) {
            runnable.run();
        }

        verify(rsiPriceFetcher, times(1)).fetchStockClosingPrices();
        verify(relativeStrengthTracker, times(1)).analyzeAndSendAlerts();
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
        when(apiRequestMeteringService.getPreviousMonth()).thenReturn("2025-09");

        scheduler.monthlyApiUsageReport();

        verify(rootErrorHandler, times(1)).run(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(1)).run(captor.capture());
        captor.getValue().run();

        // Verify that counts were retrieved
        verify(apiRequestMeteringService, times(1)).getFinnhubRequestCount();
        verify(apiRequestMeteringService, times(1)).getCoingeckoRequestCount();
        verify(apiRequestMeteringService, times(1)).getPreviousMonth();

        // Verify Telegram message was sent
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient, times(1)).sendMessage(messageCaptor.capture());

        String sentMessage = messageCaptor.getValue();
        assertTrue(sentMessage.contains("*Monthly API Usage Report - 2025-09*"));
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
        verify(apiRequestMeteringService, never()).getPreviousMonth();

        // Verify counters were still reset
        verify(apiRequestMeteringService, times(1)).resetCounters();
    }

    @Test
    void monthlyApiUsageReport_withOnlyFinnhubRequests_shouldSendReport() throws Exception {
        // Setup: Mock only Finnhub requests
        when(apiRequestMeteringService.getFinnhubRequestCount()).thenReturn(100);
        when(apiRequestMeteringService.getCoingeckoRequestCount()).thenReturn(0);
        when(apiRequestMeteringService.getPreviousMonth()).thenReturn("2025-08");

        scheduler.monthlyApiUsageReport();

        verify(rootErrorHandler, times(1)).run(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(1)).run(captor.capture());
        captor.getValue().run();

        // Verify Telegram message was sent (since finnhubCount > 0)
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient, times(1)).sendMessage(messageCaptor.capture());

        String sentMessage = messageCaptor.getValue();
        assertTrue(sentMessage.contains("*Monthly API Usage Report - 2025-08*"));
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
        when(apiRequestMeteringService.getPreviousMonth()).thenReturn("2025-07");

        scheduler.monthlyApiUsageReport();

        verify(rootErrorHandler, times(1)).run(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(1)).run(captor.capture());
        captor.getValue().run();

        // Verify Telegram message was sent (since coingeckoCount > 0)
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient, times(1)).sendMessage(messageCaptor.capture());

        String sentMessage = messageCaptor.getValue();
        assertTrue(sentMessage.contains("*Monthly API Usage Report - 2025-07*"));
        assertTrue(sentMessage.contains("🔹 *Finnhub API*: 0 requests"));
        assertTrue(sentMessage.contains("🔹 *CoinGecko API*: 50 requests"));
        assertTrue(sentMessage.contains("🔹 *Total*: 50 requests"));

        verify(apiRequestMeteringService, times(1)).resetCounters();
    }

    @Test
    void dailySectorRotationTracking_shouldFetchSectorPerformance() throws Exception {
        scheduler.dailySectorRotationTracking();

        verify(rootErrorHandler, times(1)).run(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(1)).run(captor.capture());
        captor.getValue().run();

        verify(sectorRotationTracker, times(1)).fetchAndStoreDailyPerformance();
    }

    @Test
    void dailySectorRelativeStrengthReport_shouldSendSummary() throws Exception {
        scheduler.dailySectorRelativeStrengthReport();

        verify(rootErrorHandler, times(1)).run(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(1)).run(captor.capture());
        captor.getValue().run();

        verify(sectorRelativeStrengthTracker, times(1)).sendDailySectorRsSummary();
    }

    @Test
    void manualStockMarketMonitoring_shouldRunRegardlessOfMarketHours() throws Exception {
        when(rootErrorHandler.runWithStatus(any())).thenReturn(true);

        boolean success = scheduler.manualStockMarketMonitoring();

        verify(rootErrorHandler, times(3)).runWithStatus(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(3)).runWithStatus(captor.capture());
        for (ThrowingRunnable runnable : captor.getAllValues()) {
            runnable.run();
        }

        assertTrue(success);
        verify(finnhubPriceEvaluator, times(1)).evaluatePrice();
        verify(sectorRelativeStrengthTracker, times(1)).analyzeAndSendAlerts();
        verify(sectorMomentumRocTracker, times(1)).analyzeAndSendAlerts();
    }

    @Test
    void manualHourlySignalMonitoring_shouldRunRegardlessOfMarketHours() throws Exception {
        when(rootErrorHandler.runWithStatus(any())).thenReturn(true);

        boolean success = scheduler.manualHourlySignalMonitoring();

        verify(rootErrorHandler, times(3)).runWithStatus(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(3)).runWithStatus(captor.capture());
        for (ThrowingRunnable runnable : captor.getAllValues()) {
            runnable.run();
        }

        assertTrue(success);
        verify(bollingerBandTracker, times(1)).analyzeAndSendAlerts();
        verify(rsiService, times(1)).sendRsiReport();
        verify(yahooOhlcvFetcher, times(1)).fetchAndBackfillOhlcv();
    }

    @Test
    void manualCryptoMarketMonitoring_shouldRunAndReturnTrue() throws Exception {
        stubRunWithStatus(true);

        boolean success = scheduler.manualCryptoMarketMonitoring();

        assertTrue(success);
        verify(rootErrorHandler).runWithStatus(any(ThrowingRunnable.class));
        verify(coinGeckoPriceEvaluator).evaluatePrice();
    }

    @Test
    void manualRsiStockMonitoring_shouldRunAndReturnTrue() throws Exception {
        stubRunWithStatus(true, true);

        boolean success = scheduler.manualRsiStockMonitoring();

        assertTrue(success);
        verify(rootErrorHandler, times(2)).runWithStatus(any(ThrowingRunnable.class));
        verify(rsiPriceFetcher).fetchStockClosingPrices();
        verify(relativeStrengthTracker).analyzeAndSendAlerts();
    }

    @Test
    void manualRsiStockMonitoring_returnsFalseWhenOneStepFailsButStillRunsBoth() throws Exception {
        stubRunWithStatus(true, false);

        boolean success = scheduler.manualRsiStockMonitoring();

        assertFalse(success);
        verify(rootErrorHandler, times(2)).runWithStatus(any(ThrowingRunnable.class));
        verify(rsiPriceFetcher).fetchStockClosingPrices();
        verify(relativeStrengthTracker).analyzeAndSendAlerts();
    }

    @Test
    void manualRsiCryptoMonitoring_shouldRunAndReturnTrue() throws Exception {
        stubRunWithStatus(true);

        boolean success = scheduler.manualRsiCryptoMonitoring();

        assertTrue(success);
        verify(rootErrorHandler).runWithStatus(any(ThrowingRunnable.class));
        verify(rsiPriceFetcher).fetchCryptoClosingPrices();
    }

    @Test
    void manualWeeklyInsiderTradingReport_shouldRunAndReturnTrue() {
        stubRunWithStatus(true);

        boolean success = scheduler.manualWeeklyInsiderTradingReport();

        assertTrue(success);
        verify(rootErrorHandler).runWithStatus(any(ThrowingRunnable.class));
        verify(insiderTracker).trackInsiderTransactions();
    }

    @Test
    void manualDailySectorRotationTracking_shouldRunAndReturnTrue() {
        stubRunWithStatus(true);

        boolean success = scheduler.manualDailySectorRotationTracking();

        assertTrue(success);
        verify(rootErrorHandler).runWithStatus(any(ThrowingRunnable.class));
        verify(sectorRotationTracker).fetchAndStoreDailyPerformance();
    }

    @Test
    void manualDailySectorRelativeStrengthReport_shouldRunAndReturnTrue() {
        stubRunWithStatus(true);

        boolean success = scheduler.manualDailySectorRelativeStrengthReport();

        assertTrue(success);
        verify(rootErrorHandler).runWithStatus(any(ThrowingRunnable.class));
        verify(sectorRelativeStrengthTracker).sendDailySectorRsSummary();
    }

    @Test
    void manualDailyTailRiskMonitoring_shouldRunAndReturnTrue() {
        stubRunWithStatus(true, true);

        boolean success = scheduler.manualDailyTailRiskMonitoring();

        assertTrue(success);
        verify(rootErrorHandler, times(2)).runWithStatus(any(ThrowingRunnable.class));
        verify(tailRiskTracker).sendDailyReport();
        verify(tailRiskTracker).trackAndAlert();
    }

    @Test
    void manualDailyBollingerBandReport_shouldRunAndReturnTrue() {
        stubRunWithStatus(true);

        boolean success = scheduler.manualDailyBollingerBandReport();

        assertTrue(success);
        verify(rootErrorHandler).runWithStatus(any(ThrowingRunnable.class));
        verify(bollingerBandTracker).sendDailyReport();
    }

    @Test
    void manualMonthlyApiUsageReport_withRequestsShouldSendReportAndReset() {
        when(apiRequestMeteringService.getFinnhubRequestCount()).thenReturn(10);
        when(apiRequestMeteringService.getCoingeckoRequestCount()).thenReturn(5);
        when(apiRequestMeteringService.getPreviousMonth()).thenReturn("2026-03");
        stubRunWithStatus(true);

        boolean success = scheduler.manualMonthlyApiUsageReport();

        assertTrue(success);
        verify(rootErrorHandler).runWithStatus(any(ThrowingRunnable.class));
        verify(telegramClient).sendMessage(contains("*Monthly API Usage Report - 2026-03*"));
        verify(apiRequestMeteringService).resetCounters();
    }

    @Test
    void manualMonthlyApiUsageReport_withNoRequestsShouldStillResetCounters() {
        when(apiRequestMeteringService.getFinnhubRequestCount()).thenReturn(0);
        when(apiRequestMeteringService.getCoingeckoRequestCount()).thenReturn(0);
        stubRunWithStatus(true);

        boolean success = scheduler.manualMonthlyApiUsageReport();

        assertTrue(success);
        verify(rootErrorHandler).runWithStatus(any(ThrowingRunnable.class));
        verify(telegramClient, never()).sendMessage(anyString());
        verify(apiRequestMeteringService).resetCounters();
        verify(apiRequestMeteringService, never()).getPreviousMonth();
    }

    @Test
    void manualYahooOhlcvFetch_shouldRunAndReturnTrue() throws Exception {
        stubRunWithStatus(true);

        boolean success = scheduler.manualYahooOhlcvFetch();

        assertTrue(success);
        verify(rootErrorHandler).runWithStatus(any(ThrowingRunnable.class));
        verify(yahooOhlcvFetcher).fetchAndBackfillOhlcv();
    }

    @Test
    void manualYahooOhlcvFetch_failure_shouldReturnFalse() {
        stubRunWithStatus(false);

        boolean success = scheduler.manualYahooOhlcvFetch();

        assertFalse(success);
        verify(rootErrorHandler).runWithStatus(any(ThrowingRunnable.class));
    }

    @Test
    void dailyBollingerBandReport_shouldSendReport() throws Exception {
        scheduler.dailyBollingerBandReport();

        verify(rootErrorHandler, times(1)).run(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(1)).run(captor.capture());
        captor.getValue().run();

        verify(bollingerBandTracker, times(1)).sendDailyReport();
    }

    @Test
    void dailyEmaReport_shouldSendReport() throws Exception {
        scheduler.dailyEmaReport();

        verify(rootErrorHandler, times(1)).run(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(1)).run(captor.capture());
        captor.getValue().run();

        verify(emaTracker, times(1)).sendDailyReport();
    }

    @Test
    void dailyTailRiskMonitoring_shouldSendReportAndAlerts() throws Exception {
        scheduler.dailyTailRiskMonitoring();

        verify(rootErrorHandler, times(2)).run(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(2)).run(captor.capture());

        for (ThrowingRunnable runnable : captor.getAllValues()) {
            runnable.run();
        }

        verify(tailRiskTracker, times(1)).sendDailyReport();
        verify(tailRiskTracker, times(1)).trackAndAlert();
    }

    private void stubRunWithStatus(boolean... results) {
        Queue<Boolean> remaining = new ArrayDeque<>();
        for (boolean result : results) {
            remaining.add(result);
        }

        when(rootErrorHandler.runWithStatus(any()))
                .thenAnswer(
                        invocation -> {
                            ThrowingRunnable runnable = invocation.getArgument(0);
                            runnable.run();
                            return remaining.isEmpty() || remaining.remove();
                        });
    }
}
