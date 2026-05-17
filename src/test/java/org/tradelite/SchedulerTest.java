package org.tradelite;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.finnhub.dto.MarketHolidayResponse;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.client.telegram.TelegramMessageProcessor;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.core.AccumulationDetectionTracker;
import org.tradelite.core.CoinGeckoPriceEvaluator;
import org.tradelite.core.EarningsCalendarTracker;
import org.tradelite.core.FinnhubPriceEvaluator;
import org.tradelite.core.InsiderTracker;
import org.tradelite.core.RelativeStrengthTracker;
import org.tradelite.core.SectorMomentumRocTracker;
import org.tradelite.core.SectorRelativeStrengthTracker;
import org.tradelite.core.SectorRotationTracker;
import org.tradelite.core.YahooPriceEvaluator;
import org.tradelite.quant.BollingerBandTracker;
import org.tradelite.quant.EmaTracker;
import org.tradelite.quant.PullbackBuyTracker;
import org.tradelite.quant.RsiTracker;
import org.tradelite.quant.TailRiskTracker;
import org.tradelite.quant.VfiTracker;
import org.tradelite.service.ApiRequestMeteringService;
import org.tradelite.service.MarketStatusService;
import org.tradelite.service.OhlcvBackfillService;
import org.tradelite.service.OhlcvFetcher;

@ExtendWith(MockitoExtension.class)
class SchedulerTest {

    @Mock private FinnhubPriceEvaluator finnhubPriceEvaluator;
    @Mock private CoinGeckoPriceEvaluator coinGeckoPriceEvaluator;
    @Mock private YahooPriceEvaluator yahooPriceEvaluator;
    @Mock private TargetPriceProvider targetPriceProvider;
    @Mock private TelegramGateway telegramClient;
    @Mock private TelegramMessageProcessor telegramMessageProcessor;
    @Mock private RootErrorHandler rootErrorHandler;
    @Mock private InsiderTracker insiderTracker;
    @Mock private ApiRequestMeteringService apiRequestMeteringService;
    @Mock private SectorRotationTracker sectorRotationTracker;
    @Mock private RelativeStrengthTracker relativeStrengthTracker;
    @Mock private SectorRelativeStrengthTracker sectorRelativeStrengthTracker;
    @Mock private SectorMomentumRocTracker sectorMomentumRocTracker;
    @Mock private TailRiskTracker tailRiskTracker;
    @Mock private BollingerBandTracker bollingerBandTracker;
    @Mock private RsiTracker rsiTracker;
    @Mock private EmaTracker emaTracker;
    @Mock private OhlcvFetcher ohlcvFetcher;
    @Mock private VfiTracker vfiTracker;
    @Mock private PullbackBuyTracker pullbackBuyTracker;
    @Mock private MarketStatusService marketStatusService;
    @Mock private EarningsCalendarTracker earningsCalendarTracker;
    @Mock private AccumulationDetectionTracker accumulationDetectionTracker;
    @Mock private OhlcvBackfillService ohlcvBackfillService;

    private Scheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler =
                new Scheduler(
                        finnhubPriceEvaluator,
                        coinGeckoPriceEvaluator,
                        yahooPriceEvaluator,
                        targetPriceProvider,
                        telegramClient,
                        telegramMessageProcessor,
                        rootErrorHandler,
                        insiderTracker,
                        apiRequestMeteringService,
                        sectorRotationTracker,
                        relativeStrengthTracker,
                        sectorRelativeStrengthTracker,
                        sectorMomentumRocTracker,
                        tailRiskTracker,
                        bollingerBandTracker,
                        rsiTracker,
                        emaTracker,
                        ohlcvFetcher,
                        vfiTracker,
                        pullbackBuyTracker,
                        marketStatusService,
                        earningsCalendarTracker,
                        accumulationDetectionTracker,
                        ohlcvBackfillService);
    }

    @Test
    void stockMarketMonitoring_marketOpen_shouldRun() throws Exception {
        // Monday 11:00 AM NY time = market open
        scheduler.marketDateTime =
                ZonedDateTime.of(2026, 3, 30, 11, 0, 0, 0, ZoneId.of("America/New_York"));
        when(marketStatusService.isMarketOpen(scheduler.marketDateTime)).thenReturn(true);

        scheduler.stockMarketMonitoring();

        // Called 5 times: finnhubPriceEvaluator, pullbackBuyTracker,
        // sectorRelativeStrengthTracker, sectorMomentumRocTracker, yahooPriceEvaluator
        verify(rootErrorHandler, times(5)).run(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(5)).run(captor.capture());

        // Execute all captured runnables
        for (ThrowingRunnable runnable : captor.getAllValues()) {
            runnable.run();
        }

        verify(finnhubPriceEvaluator, times(1)).evaluatePrice();
        verify(yahooPriceEvaluator, times(1)).evaluatePrice();
        verify(pullbackBuyTracker, times(1)).analyzeAndSendAlerts();
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
        when(marketStatusService.isMarketOpen(scheduler.marketDateTime)).thenReturn(false);

        scheduler.stockMarketMonitoring();

        // Yahoo evaluator still runs (handles its own exchange-hours gating)
        verify(rootErrorHandler, times(1)).run(any(ThrowingRunnable.class));
        verify(finnhubPriceEvaluator, never()).evaluatePrice();
        verify(coinGeckoPriceEvaluator, never()).evaluatePrice();
    }

    @Test
    void hourlySignalMonitoring_marketOpen_shouldRun() throws Exception {
        // Monday 11:00 AM NY time = market open
        scheduler.marketDateTime =
                ZonedDateTime.of(2026, 3, 30, 11, 0, 0, 0, ZoneId.of("America/New_York"));
        when(marketStatusService.isMarketOpen(scheduler.marketDateTime)).thenReturn(true);

        scheduler.hourlySignalMonitoring();

        // Called 3 times: bollingerBandTracker, rsiService, relativeStrengthTracker
        verify(rootErrorHandler, times(3)).run(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(3)).run(captor.capture());

        for (ThrowingRunnable runnable : captor.getAllValues()) {
            runnable.run();
        }

        verify(bollingerBandTracker, times(1)).analyzeAndSendAlerts();
        verify(rsiTracker, times(1)).analyzeAndSendReport();
        verify(relativeStrengthTracker, times(1)).analyzeAndSendAlerts();
    }

    @Test
    void hourlySignalMonitoring_marketClosed_shouldNotRun() {
        // Saturday 11:00 AM NY time = market closed (weekend)
        scheduler.marketDateTime =
                ZonedDateTime.of(2026, 3, 28, 11, 0, 0, 0, ZoneId.of("America/New_York"));
        when(marketStatusService.isMarketOpen(scheduler.marketDateTime)).thenReturn(false);

        scheduler.hourlySignalMonitoring();

        verify(rootErrorHandler, never()).run(any(ThrowingRunnable.class));
        verify(bollingerBandTracker, never()).analyzeAndSendAlerts();
        verify(rsiTracker, never()).analyzeAndSendReport();
        verify(relativeStrengthTracker, never()).analyzeAndSendAlerts();
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
    void periodicMaintenance_shouldRunCleanupAndFlush() throws Exception {
        scheduler.periodicMaintenance();

        verify(rootErrorHandler, times(4)).run(argThat(Objects::nonNull));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(4)).run(captor.capture());

        for (ThrowingRunnable runnable : captor.getAllValues()) {
            runnable.run();
        }

        verify(targetPriceProvider, times(1))
                .cleanupIgnoreSymbols(TargetPriceProvider.IGNORE_DURATION_TTL_SECONDS);
        verify(apiRequestMeteringService, times(1)).flushCounters();
        verify(ohlcvBackfillService, times(1)).backfillNewlyAddedSymbols();
        verify(ohlcvBackfillService, times(1)).cleanupExpiredSymbols();
    }

    @Test
    void pollTelegramUpdates_shouldProcessUpdates() throws Exception {
        scheduler.pollTelegramChatUpdates();

        verify(rootErrorHandler, times(1)).run(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(1)).run(captor.capture());
        captor.getValue().run();

        verify(telegramClient, times(1)).getChatUpdates();
        verify(telegramMessageProcessor, times(1)).processUpdates(anyList());
    }

    @Test
    void pollTelegramUpdates_whenGetChatUpdatesFails_exceptionStaysInsideHandler() {
        when(telegramClient.getChatUpdates())
                .thenThrow(
                        new IllegalStateException(
                                "Error while fetching chat updates: api.telegram.org"));

        scheduler.pollTelegramChatUpdates();

        // getChatUpdates is now inside the lambda — execute it and verify the exception is
        // contained
        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler).run(captor.capture());

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, () -> captor.getValue().run());

        verify(telegramMessageProcessor, never()).processUpdates(anyList());
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
    void monthlyApiUsageReport_withRequests_shouldSendReportAndReset() throws Exception {
        // Setup: Mock API request counts
        when(apiRequestMeteringService.getFinnhubRequestCount()).thenReturn(150);
        when(apiRequestMeteringService.getCoingeckoRequestCount()).thenReturn(75);
        when(apiRequestMeteringService.getTwelveDataRequestCount()).thenReturn(30);
        when(apiRequestMeteringService.getPreviousMonth()).thenReturn("2025-09");

        scheduler.monthlyApiUsageReport();

        verify(rootErrorHandler, times(1)).run(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(1)).run(captor.capture());
        captor.getValue().run();

        // Verify that counts were retrieved
        verify(apiRequestMeteringService, times(1)).getFinnhubRequestCount();
        verify(apiRequestMeteringService, times(1)).getCoingeckoRequestCount();
        verify(apiRequestMeteringService, times(1)).getTwelveDataRequestCount();
        verify(apiRequestMeteringService, times(1)).getPreviousMonth();

        // Verify Telegram message was sent
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient, times(1)).sendMessage(messageCaptor.capture());

        String sentMessage = messageCaptor.getValue();
        assertTrue(sentMessage.contains("*Monthly API Usage Report - 2025-09*"));
        assertTrue(sentMessage.contains("🔹 *Finnhub API*: 150 requests"));
        assertTrue(sentMessage.contains("🔹 *CoinGecko API*: 75 requests"));
        assertTrue(sentMessage.contains("🔹 *Twelve Data API*: 30 requests"));
        assertTrue(sentMessage.contains("🔹 *Total*: 255 requests"));

        // Verify counters were reset
        verify(apiRequestMeteringService, times(1)).resetCounters();
    }

    @Test
    void monthlyApiUsageReport_withNoRequests_shouldSkipReportButStillReset() throws Exception {
        // Setup: Mock zero API request counts
        when(apiRequestMeteringService.getFinnhubRequestCount()).thenReturn(0);
        when(apiRequestMeteringService.getCoingeckoRequestCount()).thenReturn(0);
        when(apiRequestMeteringService.getTwelveDataRequestCount()).thenReturn(0);

        scheduler.monthlyApiUsageReport();

        verify(rootErrorHandler, times(1)).run(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(1)).run(captor.capture());
        captor.getValue().run();

        // Verify that counts were retrieved
        verify(apiRequestMeteringService, times(1)).getFinnhubRequestCount();
        verify(apiRequestMeteringService, times(1)).getCoingeckoRequestCount();
        verify(apiRequestMeteringService, times(1)).getTwelveDataRequestCount();

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
        when(apiRequestMeteringService.getTwelveDataRequestCount()).thenReturn(0);
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
        assertTrue(sentMessage.contains("🔹 *Twelve Data API*: 0 requests"));
        assertTrue(sentMessage.contains("🔹 *Total*: 100 requests"));

        verify(apiRequestMeteringService, times(1)).resetCounters();
    }

    @Test
    void monthlyApiUsageReport_withOnlyCoingeckoRequests_shouldSendReport() throws Exception {
        // Setup: Mock only CoinGecko requests
        when(apiRequestMeteringService.getFinnhubRequestCount()).thenReturn(0);
        when(apiRequestMeteringService.getCoingeckoRequestCount()).thenReturn(50);
        when(apiRequestMeteringService.getTwelveDataRequestCount()).thenReturn(0);
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
        assertTrue(sentMessage.contains("🔹 *Twelve Data API*: 0 requests"));
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

        verify(rootErrorHandler, times(5)).runWithStatus(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(5)).runWithStatus(captor.capture());
        for (ThrowingRunnable runnable : captor.getAllValues()) {
            runnable.run();
        }

        assertTrue(success);
        verify(finnhubPriceEvaluator, times(1)).evaluatePrice();
        verify(yahooPriceEvaluator, times(1)).evaluatePrice();
        verify(pullbackBuyTracker, times(1)).analyzeAndSendAlerts();
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
        verify(rsiTracker, times(1)).analyzeAndSendReport();
        verify(relativeStrengthTracker, times(1)).analyzeAndSendAlerts();
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
    void manualRelativeStrengthMonitoring_shouldRunAndReturnTrue() {
        stubRunWithStatus(true);

        boolean success = scheduler.manualRelativeStrengthMonitoring();

        assertTrue(success);
        verify(rootErrorHandler).runWithStatus(any(ThrowingRunnable.class));
        verify(relativeStrengthTracker).analyzeAndSendAlerts();
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
    void dailyEmaReport_shouldSendReport() throws Exception {
        scheduler.dailyEmaReport();

        verify(rootErrorHandler, times(1)).run(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(1)).run(captor.capture());
        captor.getValue().run();

        verify(emaTracker, times(1)).sendDailyReport();
    }

    @Test
    void dailyVfiReport_shouldSendReport() throws Exception {
        scheduler.dailyVfiReport();

        verify(rootErrorHandler, times(1)).run(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(1)).run(captor.capture());
        captor.getValue().run();

        verify(vfiTracker, times(1)).sendDailyReport();
    }

    @Test
    void dailyOhlcvFetch_shouldDelegateToFetcher() throws Exception {
        scheduler.dailyOhlcvFetch();

        verify(rootErrorHandler, times(1)).run(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(1)).run(captor.capture());
        captor.getValue().run();

        verify(ohlcvFetcher, times(1)).fetchAndBackfillOhlcv();
    }

    @Test
    void manualOhlcvFetch_shouldRunAndReturnTrue() throws Exception {
        stubRunWithStatus(true);

        boolean success = scheduler.manualOhlcvFetch();

        assertTrue(success);
        verify(rootErrorHandler).runWithStatus(any(ThrowingRunnable.class));
        verify(ohlcvFetcher).fetchAndBackfillOhlcv();
    }

    @Test
    void manualVfiReport_shouldRunAndReturnTrue() {
        stubRunWithStatus(true);

        boolean success = scheduler.manualVfiReport();

        assertTrue(success);
        verify(rootErrorHandler).runWithStatus(any(ThrowingRunnable.class));
        verify(vfiTracker).sendDailyReport();
    }

    @Test
    void manualPullbackBuyAlert_shouldRunAndReturnTrue() {
        stubRunWithStatus(true);

        boolean success = scheduler.manualPullbackBuyAlert();

        assertTrue(success);
        verify(rootErrorHandler).runWithStatus(any(ThrowingRunnable.class));
        verify(pullbackBuyTracker).analyzeAndSendAlerts();
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

    // --- Market holiday notification tests ---

    @Test
    void dailyMarketHolidayNotification_fullClose_sendsMessage() throws Exception {
        MarketHolidayResponse.MarketHoliday holiday = new MarketHolidayResponse.MarketHoliday();
        holiday.setEventName("Memorial Day");
        holiday.setTradingHour("");
        when(marketStatusService.getTodayHoliday()).thenReturn(Optional.of(holiday));

        scheduler.dailyMarketHolidayNotification();

        verify(rootErrorHandler, times(1)).run(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(1)).run(captor.capture());
        captor.getValue().run();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String msg = messageCaptor.getValue();
        assertTrue(msg.contains("*Market Holiday*"));
        assertTrue(msg.contains("Memorial Day"));
        assertTrue(msg.contains("Markets are closed"));
    }

    @Test
    void dailyMarketHolidayNotification_earlyClose_sendsMessage() throws Exception {
        MarketHolidayResponse.MarketHoliday holiday = new MarketHolidayResponse.MarketHoliday();
        holiday.setEventName("Day After Thanksgiving");
        holiday.setTradingHour("09:30-13:00");
        when(marketStatusService.getTodayHoliday()).thenReturn(Optional.of(holiday));

        scheduler.dailyMarketHolidayNotification();

        verify(rootErrorHandler, times(1)).run(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(1)).run(captor.capture());
        captor.getValue().run();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String msg = messageCaptor.getValue();
        assertTrue(msg.contains("*Early Close*"));
        assertTrue(msg.contains("Day After Thanksgiving"));
        assertTrue(msg.contains("13:00 ET"));
    }

    @Test
    void dailyMarketHolidayNotification_noHoliday_sendsNoMessage() throws Exception {
        when(marketStatusService.getTodayHoliday()).thenReturn(Optional.empty());

        scheduler.dailyMarketHolidayNotification();

        verify(rootErrorHandler, times(1)).run(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(1)).run(captor.capture());
        captor.getValue().run();

        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void manualMarketHolidayNotification_fullClose_sendsMessageAndReturnsTrue() {
        MarketHolidayResponse.MarketHoliday holiday = new MarketHolidayResponse.MarketHoliday();
        holiday.setEventName("Independence Day");
        holiday.setTradingHour(null);
        when(marketStatusService.getTodayHoliday()).thenReturn(Optional.of(holiday));
        stubRunWithStatus(true);

        boolean success = scheduler.manualMarketHolidayNotification();

        assertTrue(success);
        verify(telegramClient).sendMessage(contains("Independence Day"));
    }
}
