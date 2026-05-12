package org.tradelite;

import static org.tradelite.common.TargetPriceProvider.IGNORE_DURATION_TTL_SECONDS;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.tradelite.client.finnhub.dto.MarketHolidayResponse;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.client.telegram.TelegramMessageProcessor;
import org.tradelite.client.telegram.dto.TelegramUpdateResponse;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.core.*;
import org.tradelite.quant.BollingerBandTracker;
import org.tradelite.quant.EmaTracker;
import org.tradelite.quant.PullbackBuyTracker;
import org.tradelite.quant.RsiTracker;
import org.tradelite.quant.TailRiskTracker;
import org.tradelite.quant.VfiTracker;
import org.tradelite.service.ApiRequestMeteringService;
import org.tradelite.service.MarketStatusService;
import org.tradelite.service.OhlcvFetcher;

@Slf4j
@Component
public class Scheduler {

    private final FinnhubPriceEvaluator finnhubPriceEvaluator;
    private final CoinGeckoPriceEvaluator coinGeckoPriceEvaluator;
    private final YahooPriceEvaluator yahooPriceEvaluator;
    private final TargetPriceProvider targetPriceProvider;
    private final TelegramGateway telegramClient;
    private final TelegramMessageProcessor telegramMessageProcessor;
    private final RootErrorHandler rootErrorHandler;
    private final InsiderTracker insiderTracker;
    private final ApiRequestMeteringService apiRequestMeteringService;
    private final SectorRotationTracker sectorRotationTracker;
    private final RelativeStrengthTracker relativeStrengthTracker;
    private final SectorRelativeStrengthTracker sectorRelativeStrengthTracker;
    private final SectorMomentumRocTracker sectorMomentumRocTracker;
    private final TailRiskTracker tailRiskTracker;
    private final BollingerBandTracker bollingerBandTracker;
    private final RsiTracker rsiTracker;
    private final EmaTracker emaTracker;
    private final OhlcvFetcher ohlcvFetcher;
    private final VfiTracker vfiTracker;
    private final PullbackBuyTracker pullbackBuyTracker;
    private final MarketStatusService marketStatusService;
    private final EarningsCalendarTracker earningsCalendarTracker;
    private final AccumulationDetectionTracker accumulationDetectionTracker;

    protected ZonedDateTime marketDateTime = null;

    @Autowired
    Scheduler(
            FinnhubPriceEvaluator finnhubPriceEvaluator,
            CoinGeckoPriceEvaluator coinGeckoPriceEvaluator,
            YahooPriceEvaluator yahooPriceEvaluator,
            TargetPriceProvider targetPriceProvider,
            TelegramGateway telegramClient,
            TelegramMessageProcessor telegramMessageProcessor,
            RootErrorHandler rootErrorHandler,
            InsiderTracker insiderTracker,
            ApiRequestMeteringService apiRequestMeteringService,
            SectorRotationTracker sectorRotationTracker,
            RelativeStrengthTracker relativeStrengthTracker,
            SectorRelativeStrengthTracker sectorRelativeStrengthTracker,
            SectorMomentumRocTracker sectorMomentumRocTracker,
            TailRiskTracker tailRiskTracker,
            BollingerBandTracker bollingerBandTracker,
            RsiTracker rsiTracker,
            EmaTracker emaTracker,
            OhlcvFetcher ohlcvFetcher,
            VfiTracker vfiTracker,
            PullbackBuyTracker pullbackBuyTracker,
            MarketStatusService marketStatusService,
            EarningsCalendarTracker earningsCalendarTracker,
            AccumulationDetectionTracker accumulationDetectionTracker) {
        this.finnhubPriceEvaluator = finnhubPriceEvaluator;
        this.coinGeckoPriceEvaluator = coinGeckoPriceEvaluator;
        this.yahooPriceEvaluator = yahooPriceEvaluator;
        this.targetPriceProvider = targetPriceProvider;
        this.telegramClient = telegramClient;
        this.telegramMessageProcessor = telegramMessageProcessor;
        this.rootErrorHandler = rootErrorHandler;
        this.insiderTracker = insiderTracker;
        this.apiRequestMeteringService = apiRequestMeteringService;
        this.sectorRotationTracker = sectorRotationTracker;
        this.relativeStrengthTracker = relativeStrengthTracker;
        this.sectorRelativeStrengthTracker = sectorRelativeStrengthTracker;
        this.sectorMomentumRocTracker = sectorMomentumRocTracker;
        this.tailRiskTracker = tailRiskTracker;
        this.bollingerBandTracker = bollingerBandTracker;
        this.rsiTracker = rsiTracker;
        this.emaTracker = emaTracker;
        this.ohlcvFetcher = ohlcvFetcher;
        this.vfiTracker = vfiTracker;
        this.pullbackBuyTracker = pullbackBuyTracker;
        this.marketStatusService = marketStatusService;
        this.earningsCalendarTracker = earningsCalendarTracker;
        this.accumulationDetectionTracker = accumulationDetectionTracker;
    }

    @Scheduled(initialDelay = 0, fixedRate = 300000)
    public void stockMarketMonitoring() {
        if (marketStatusService.isMarketOpen(marketDateTime)) {
            rootErrorHandler.run(finnhubPriceEvaluator::evaluatePrice);
            rootErrorHandler.run(pullbackBuyTracker::analyzeAndSendAlerts);
            // Analyze sector ETFs in real-time for rotation signals
            rootErrorHandler.run(sectorRelativeStrengthTracker::analyzeAndSendAlerts);
            rootErrorHandler.run(sectorMomentumRocTracker::analyzeAndSendAlerts);
        } else {
            log.info("Market is off-hours or it's a weekend. Skipping price evaluation.");
        }
        // International stocks — evaluator handles its own exchange-hours gating
        rootErrorHandler.run(yahooPriceEvaluator::evaluatePrice);
        log.info("Stock market monitoring round completed.");
    }

    @Scheduled(cron = "0 0 * * * MON-FRI", zone = "CET")
    protected void hourlySignalMonitoring() {
        if (marketStatusService.isMarketOpen(marketDateTime)) {
            rootErrorHandler.run(bollingerBandTracker::analyzeAndSendAlerts);
            rootErrorHandler.run(rsiTracker::analyzeAndSendReport);
            rootErrorHandler.run(relativeStrengthTracker::analyzeAndSendAlerts);
        } else {
            log.info("Market is off-hours or it's a weekend. Skipping hourly signal monitoring.");
        }
        log.info("Hourly signal monitoring round completed.");
    }

    @Scheduled(initialDelay = 0, fixedRate = 420000)
    public void cryptoMarketMonitoring() {
        rootErrorHandler.run(coinGeckoPriceEvaluator::evaluatePrice);
        log.info("Crypto market monitoring round completed.");
    }

    @Scheduled(cron = "0 0 16,21 * * MON-FRI", zone = "CET")
    protected void dailySectorRelativeStrengthReport() {
        rootErrorHandler.run(sectorRelativeStrengthTracker::sendDailySectorRsSummary);
        log.info("Daily sector relative strength report completed.");
    }

    @Scheduled(cron = "0 0 13 * * MON-FRI", zone = "CET")
    protected void dailyTailRiskMonitoring() {
        rootErrorHandler.run(tailRiskTracker::sendDailyReport);
        rootErrorHandler.run(tailRiskTracker::trackAndAlert);
        log.info("Daily tail risk monitoring completed.");
    }

    @Scheduled(cron = "0 50 15 * * MON-FRI", zone = "CET")
    protected void dailyEmaReport() {
        rootErrorHandler.run(emaTracker::sendDailyReport);
        log.info("Daily EMA report completed.");
    }

    @Scheduled(cron = "0 0 9 * * MON-FRI", zone = "CET")
    protected void dailyVfiReport() {
        rootErrorHandler.run(vfiTracker::sendDailyReport);
        log.info("Daily VFI report completed.");
    }

    @Scheduled(cron = "0 0 10 * * MON-FRI", zone = "CET")
    protected void dailyAccumulationDetection() {
        rootErrorHandler.run(accumulationDetectionTracker::analyzeAndSendAlerts);
        log.info("Daily accumulation detection completed.");
    }

    @Scheduled(cron = "0 0 8 * * MON-FRI", zone = "CET")
    protected void dailyMarketHolidayNotification() {
        rootErrorHandler.run(this::doMarketHolidayNotification);
        log.info("Daily market holiday notification check completed.");
    }

    @Scheduled(cron = "0 15 8 * * *", zone = "CET")
    protected void dailyEarningsCalendarCheck() {
        rootErrorHandler.run(earningsCalendarTracker::checkAndAlert);
        log.info("Daily earnings calendar check completed.");
    }

    @Scheduled(fixedRate = 600000)
    public void periodicMaintenance() {
        rootErrorHandler.run(
                () -> targetPriceProvider.cleanupIgnoreSymbols(IGNORE_DURATION_TTL_SECONDS));
        rootErrorHandler.run(apiRequestMeteringService::flushCounters);

        log.info("Periodic maintenance completed.");
    }

    @Scheduled(fixedRate = 60000)
    public void pollTelegramChatUpdates() {
        rootErrorHandler.run(
                () -> {
                    List<TelegramUpdateResponse> chatUpdates = telegramClient.getChatUpdates();
                    telegramMessageProcessor.processUpdates(chatUpdates);
                    log.info("Telegram chat updates processed.");
                });
    }

    @Scheduled(cron = "0 0 12 ? * SAT", zone = "CET")
    public void weeklyInsiderTradingReport() {
        rootErrorHandler.run(insiderTracker::trackInsiderTransactions);

        log.info("Weekly insider trading report generated.");
    }

    @Scheduled(cron = "0 30 16 * * MON-FRI", zone = "CET")
    public void dailySectorRotationTracking() {
        rootErrorHandler.run(sectorRotationTracker::fetchAndStoreDailyPerformance);

        log.info("Daily sector rotation tracking completed.");
    }

    @Scheduled(cron = "0 0 23 * * MON-FRI", zone = "CET")
    protected void dailyOhlcvFetch() {
        rootErrorHandler.run(ohlcvFetcher::fetchAndBackfillOhlcv);
        log.info("Daily OHLCV fetch completed.");
    }

    @Scheduled(cron = "0 0 0 1 * *", zone = "UTC")
    public void monthlyApiUsageReport() {
        rootErrorHandler.run(this::doMonthlyApiUsageReport);
        log.info("Monthly API usage report completed.");
    }

    private void doMonthlyApiUsageReport() {
        int finnhubCount = apiRequestMeteringService.getFinnhubRequestCount();
        int coingeckoCount = apiRequestMeteringService.getCoingeckoRequestCount();
        int twelveDataCount = apiRequestMeteringService.getTwelveDataRequestCount();
        int yahooCount = apiRequestMeteringService.getYahooRequestCount();

        if (finnhubCount > 0 || coingeckoCount > 0 || twelveDataCount > 0 || yahooCount > 0) {
            String previousMonth = apiRequestMeteringService.getPreviousMonth();

            String message =
                    String.format(
                            """
                            *Monthly API Usage Report - %s*
                            🔹 *Finnhub API*: %,d requests
                            🔹 *CoinGecko API*: %,d requests
                            🔹 *Twelve Data API*: %,d requests
                            🔹 *Yahoo Finance*: %,d requests
                            🔹 *Total*: %,d requests""",
                            previousMonth,
                            finnhubCount,
                            coingeckoCount,
                            twelveDataCount,
                            yahooCount,
                            finnhubCount + coingeckoCount + twelveDataCount + yahooCount);

            telegramClient.sendMessage(message);
            log.info(
                    "Monthly API usage report sent for {}: Finnhub={}, CoinGecko={}, TwelveData={}, Yahoo={}",
                    previousMonth,
                    finnhubCount,
                    coingeckoCount,
                    twelveDataCount,
                    yahooCount);
        } else {
            log.info("No API requests recorded for the previous month, skipping report");
        }

        apiRequestMeteringService.resetCounters();
    }

    private void doMarketHolidayNotification() {
        Optional<MarketHolidayResponse.MarketHoliday> holiday =
                marketStatusService.getTodayHoliday();
        if (holiday.isEmpty()) {
            return;
        }

        MarketHolidayResponse.MarketHoliday h = holiday.get();
        String tradingHour = h.getTradingHour();

        if (tradingHour == null || tradingHour.isEmpty()) {
            String message =
                    String.format(
                            "*Market Holiday*\n"
                                    + "It's a U.S. market holiday today (%s). Markets are closed."
                                    + " Enjoy your day!",
                            h.getEventName());
            telegramClient.sendMessage(message);
        } else {
            String closeTime = tradingHour.split("-")[1];
            String message =
                    String.format(
                            "*Early Close*%nEarly close today (%s). Markets close at %s ET.",
                            h.getEventName(), closeTime);
            telegramClient.sendMessage(message);
        }
    }

    public boolean manualStockMarketMonitoring() {
        boolean success = true;
        success &= rootErrorHandler.runWithStatus(finnhubPriceEvaluator::evaluatePrice);
        success &= rootErrorHandler.runWithStatus(yahooPriceEvaluator::evaluatePrice);
        success &= rootErrorHandler.runWithStatus(pullbackBuyTracker::analyzeAndSendAlerts);
        success &=
                rootErrorHandler.runWithStatus(sectorRelativeStrengthTracker::analyzeAndSendAlerts);
        success &= rootErrorHandler.runWithStatus(sectorMomentumRocTracker::analyzeAndSendAlerts);
        log.info("Manual stock market monitoring completed.");
        return success;
    }

    public boolean manualHourlySignalMonitoring() {
        boolean success = true;
        success &= rootErrorHandler.runWithStatus(bollingerBandTracker::analyzeAndSendAlerts);
        success &= rootErrorHandler.runWithStatus(rsiTracker::analyzeAndSendReport);
        success &= rootErrorHandler.runWithStatus(relativeStrengthTracker::analyzeAndSendAlerts);
        log.info("Manual hourly signal monitoring completed.");
        return success;
    }

    public boolean manualCryptoMarketMonitoring() {
        boolean success = rootErrorHandler.runWithStatus(coinGeckoPriceEvaluator::evaluatePrice);
        log.info("Manual crypto market monitoring completed.");
        return success;
    }

    public boolean manualRelativeStrengthMonitoring() {
        boolean success =
                rootErrorHandler.runWithStatus(relativeStrengthTracker::analyzeAndSendAlerts);
        log.info("Manual relative strength monitoring completed.");
        return success;
    }

    public boolean manualWeeklyInsiderTradingReport() {
        boolean success = rootErrorHandler.runWithStatus(insiderTracker::trackInsiderTransactions);
        log.info("Manual insider trading report completed.");
        return success;
    }

    public boolean manualDailySectorRotationTracking() {
        boolean success =
                rootErrorHandler.runWithStatus(
                        sectorRotationTracker::fetchAndStoreDailyPerformance);
        log.info("Manual sector rotation tracking completed.");
        return success;
    }

    public boolean manualDailySectorRelativeStrengthReport() {
        boolean success =
                rootErrorHandler.runWithStatus(
                        sectorRelativeStrengthTracker::sendDailySectorRsSummary);
        log.info("Manual sector relative strength report completed.");
        return success;
    }

    public boolean manualDailyTailRiskMonitoring() {
        boolean success = true;
        success &= rootErrorHandler.runWithStatus(tailRiskTracker::sendDailyReport);
        success &= rootErrorHandler.runWithStatus(tailRiskTracker::trackAndAlert);
        log.info("Manual tail risk monitoring completed.");
        return success;
    }

    public boolean manualEmaReport() {
        boolean success = rootErrorHandler.runWithStatus(emaTracker::sendDailyReport);
        log.info("Manual EMA report completed.");
        return success;
    }

    public boolean manualMonthlyApiUsageReport() {
        boolean success = rootErrorHandler.runWithStatus(this::doMonthlyApiUsageReport);
        log.info("Manual monthly API usage report completed.");
        return success;
    }

    public boolean manualOhlcvFetch() {
        boolean success = rootErrorHandler.runWithStatus(ohlcvFetcher::fetchAndBackfillOhlcv);
        log.info("Manual OHLCV fetch completed.");
        return success;
    }

    public boolean manualOhlcvFetchLimited(int maxSymbols) {
        boolean success =
                rootErrorHandler.runWithStatus(
                        () -> ohlcvFetcher.fetchAndBackfillOhlcv(maxSymbols));
        log.info("Manual OHLCV fetch (limited to {} symbols) completed.", maxSymbols);
        return success;
    }

    public boolean manualVfiReport() {
        boolean success = rootErrorHandler.runWithStatus(vfiTracker::sendDailyReport);
        log.info("Manual VFI report completed.");
        return success;
    }

    public boolean manualPullbackBuyAlert() {
        boolean success = rootErrorHandler.runWithStatus(pullbackBuyTracker::analyzeAndSendAlerts);
        log.info("Manual pullback buy alert scan completed.");
        return success;
    }

    public boolean manualEarningsCalendarCheck() {
        boolean success = rootErrorHandler.runWithStatus(earningsCalendarTracker::checkAndAlert);
        log.info("Manual earnings calendar check completed.");
        return success;
    }

    public boolean manualAccumulationDetection() {
        boolean success =
                rootErrorHandler.runWithStatus(accumulationDetectionTracker::analyzeAndSendAlerts);
        log.info("Manual accumulation detection completed.");
        return success;
    }

    public boolean manualMarketHolidayNotification() {
        boolean success = rootErrorHandler.runWithStatus(this::doMarketHolidayNotification);
        log.info("Manual market holiday notification completed.");
        return success;
    }

    public boolean manualYahooPriceEvaluation() {
        boolean success = rootErrorHandler.runWithStatus(yahooPriceEvaluator::evaluatePrice);
        log.info("Manual Yahoo price evaluation completed.");
        return success;
    }
}
