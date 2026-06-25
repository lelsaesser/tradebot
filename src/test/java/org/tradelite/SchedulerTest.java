package org.tradelite;

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
import org.tradelite.core.AccumulationDetectionTracker;
import org.tradelite.core.CoinGeckoPriceEvaluator;
import org.tradelite.core.EarningsCalendarTracker;
import org.tradelite.core.FinnhubPriceEvaluator;
import org.tradelite.core.InsiderTracker;
import org.tradelite.core.MarketHolidayNotifier;
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
import org.tradelite.service.LivePriceCache;
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
    @Mock private LivePriceCache livePriceCache;
    @Mock private MarketHolidayNotifier marketHolidayNotifier;
    @Mock private org.tradelite.core.TreasuryTracker treasuryTracker;

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
                        ohlcvBackfillService,
                        livePriceCache,
                        marketHolidayNotifier,
                        treasuryTracker);
    }

    @Test
    void stockMarketMonitoring_marketOpen_shouldRun() throws Exception {
        // Monday 11:00 AM NY time = market open
        scheduler.marketDateTime =
                ZonedDateTime.of(2026, 3, 30, 11, 0, 0, 0, ZoneId.of("America/New_York"));
        when(marketStatusService.isMarketOpen(scheduler.marketDateTime)).thenReturn(true);

        scheduler.stockMarketMonitoring();

        // Called 6 times: finnhubPriceEvaluator, pullbackBuyTracker.analyzeDomestic,
        // sectorRelativeStrengthTracker, sectorMomentumRocTracker, yahooPriceEvaluator,
        // pullbackBuyTracker.analyzeInternational
        verify(rootErrorHandler, times(6)).run(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(6)).run(captor.capture());

        // Execute all captured runnables
        for (ThrowingRunnable runnable : captor.getAllValues()) {
            runnable.run();
        }

        verify(finnhubPriceEvaluator, times(1)).evaluatePrice();
        verify(yahooPriceEvaluator, times(1)).evaluatePrice();
        verify(pullbackBuyTracker, times(1)).analyzeDomestic();
        verify(pullbackBuyTracker, times(1)).analyzeInternational();
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

        // Yahoo evaluator + analyzeInternational still run (both handle their own per-symbol
        // exchange-hours gating)
        verify(rootErrorHandler, times(2)).run(any(ThrowingRunnable.class));
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
    void periodicMaintenance_shouldRunCleanupFlushAndEvict() throws Exception {
        scheduler.periodicMaintenance();

        verify(rootErrorHandler, times(5)).run(argThat(Objects::nonNull));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(5)).run(captor.capture());

        for (ThrowingRunnable runnable : captor.getAllValues()) {
            runnable.run();
        }

        verify(targetPriceProvider, times(1))
                .cleanupIgnoreSymbols(TargetPriceProvider.IGNORE_DURATION_TTL_SECONDS);
        verify(apiRequestMeteringService, times(1)).flushCounters();
        verify(ohlcvBackfillService, times(1)).backfillNewlyAddedSymbols();
        verify(ohlcvBackfillService, times(1)).cleanupExpiredSymbols();
        verify(livePriceCache, times(1)).evictStale();
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
    void monthlyApiUsageReport_delegatesToMeteringService() throws Exception {
        scheduler.monthlyApiUsageReport();

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(1)).run(captor.capture());
        captor.getValue().run();

        verify(apiRequestMeteringService, times(1)).sendMonthlyUsageReport();
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

        verify(rootErrorHandler, times(6)).runWithStatus(any(ThrowingRunnable.class));

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(6)).runWithStatus(captor.capture());
        for (ThrowingRunnable runnable : captor.getAllValues()) {
            runnable.run();
        }

        assertTrue(success);
        verify(finnhubPriceEvaluator, times(1)).evaluatePrice();
        verify(yahooPriceEvaluator, times(1)).evaluatePrice();
        verify(pullbackBuyTracker, times(1)).analyzeDomestic();
        verify(pullbackBuyTracker, times(1)).analyzeInternational();
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
    void manualMonthlyApiUsageReport_delegatesToMeteringService() {
        stubRunWithStatus(true);

        boolean success = scheduler.manualMonthlyApiUsageReport();

        assertTrue(success);
        verify(rootErrorHandler).runWithStatus(any(ThrowingRunnable.class));
        verify(apiRequestMeteringService, times(1)).sendMonthlyUsageReport();
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
        verify(rootErrorHandler, times(2)).runWithStatus(any(ThrowingRunnable.class));
        verify(pullbackBuyTracker).analyzeDomestic();
        verify(pullbackBuyTracker).analyzeInternational();
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
    void dailyMarketHolidayNotification_delegatesToNotifier() throws Exception {
        scheduler.dailyMarketHolidayNotification();

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(1)).run(captor.capture());
        captor.getValue().run();

        verify(marketHolidayNotifier, times(1)).sendDailyReport();
    }

    @Test
    void manualMarketHolidayNotification_delegatesToNotifier() {
        stubRunWithStatus(true);

        boolean success = scheduler.manualMarketHolidayNotification();

        assertTrue(success);
        verify(marketHolidayNotifier, times(1)).sendDailyReport();
    }

    @Test
    void dailyTreasuryReport_delegatesToTracker() throws Exception {
        scheduler.dailyTreasuryReport();

        ArgumentCaptor<ThrowingRunnable> captor = ArgumentCaptor.forClass(ThrowingRunnable.class);
        verify(rootErrorHandler, times(1)).run(captor.capture());
        captor.getValue().run();

        verify(treasuryTracker, times(1)).checkAndAlert();
    }

    @Test
    void manualTreasuryReport_delegatesToTracker() {
        stubRunWithStatus(true);

        boolean success = scheduler.manualTreasuryReport();

        assertTrue(success);
        verify(treasuryTracker, times(1)).checkAndAlert();
    }
}
