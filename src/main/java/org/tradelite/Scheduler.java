package org.tradelite;

import static org.tradelite.common.TargetPriceProvider.IGNORE_DURATION_TTL_SECONDS;

import java.time.ZonedDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.client.telegram.TelegramMessageProcessor;
import org.tradelite.client.telegram.dto.TelegramUpdateResponse;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.core.*;
import org.tradelite.quant.BollingerBandTracker;
import org.tradelite.quant.TailRiskTracker;
import org.tradelite.service.ApiRequestMeteringService;
import org.tradelite.service.RsiService;
import org.tradelite.utils.DateUtil;

@Slf4j
@Component
public class Scheduler {

    private final FinnhubPriceEvaluator finnhubPriceEvaluator;
    private final CoinGeckoPriceEvaluator coinGeckoPriceEvaluator;
    private final TargetPriceProvider targetPriceProvider;
    private final TelegramGateway telegramClient;
    private final TelegramMessageProcessor telegramMessageProcessor;
    private final RootErrorHandler rootErrorHandler;
    private final InsiderTracker insiderTracker;
    private final RsiPriceFetcher rsiPriceFetcher;
    private final ApiRequestMeteringService apiRequestMeteringService;
    private final SectorRotationTracker sectorRotationTracker;
    private final RelativeStrengthTracker relativeStrengthTracker;
    private final SectorRelativeStrengthTracker sectorRelativeStrengthTracker;
    private final SectorMomentumRocTracker sectorMomentumRocTracker;
    private final TailRiskTracker tailRiskTracker;
    private final BollingerBandTracker bollingerBandTracker;
    private final RsiService rsiService;

    protected ZonedDateTime marketDateTime = null;

    @Autowired
    Scheduler(
            FinnhubPriceEvaluator finnhubPriceEvaluator,
            CoinGeckoPriceEvaluator coinGeckoPriceEvaluator,
            TargetPriceProvider targetPriceProvider,
            TelegramGateway telegramClient,
            TelegramMessageProcessor telegramMessageProcessor,
            RootErrorHandler rootErrorHandler,
            InsiderTracker insiderTracker,
            RsiPriceFetcher rsiPriceFetcher,
            ApiRequestMeteringService apiRequestMeteringService,
            SectorRotationTracker sectorRotationTracker,
            RelativeStrengthTracker relativeStrengthTracker,
            SectorRelativeStrengthTracker sectorRelativeStrengthTracker,
            SectorMomentumRocTracker sectorMomentumRocTracker,
            TailRiskTracker tailRiskTracker,
            BollingerBandTracker bollingerBandTracker,
            RsiService rsiService) {
        this.finnhubPriceEvaluator = finnhubPriceEvaluator;
        this.coinGeckoPriceEvaluator = coinGeckoPriceEvaluator;
        this.rsiPriceFetcher = rsiPriceFetcher;
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
        this.rsiService = rsiService;
    }

    @Scheduled(initialDelay = 0, fixedRate = 300000)
    public void stockMarketMonitoring() {
        if (DateUtil.isStockMarketOpen(marketDateTime)) {
            rootErrorHandler.run(finnhubPriceEvaluator::evaluatePrice);
            // Analyze sector ETFs in real-time for rotation signals
            rootErrorHandler.run(sectorRelativeStrengthTracker::analyzeAndSendAlerts);
            rootErrorHandler.run(sectorMomentumRocTracker::analyzeAndSendAlerts);
        } else {
            log.info("Market is off-hours or it's a weekend. Skipping price evaluation.");
        }
        log.info("Stock market monitoring round completed.");
    }

    @Scheduled(cron = "0 0 * * * MON-FRI", zone = "CET")
    protected void hourlySignalMonitoring() {
        if (DateUtil.isStockMarketOpen(marketDateTime)) {
            rootErrorHandler.run(bollingerBandTracker::analyzeAndSendAlerts);
            rootErrorHandler.run(rsiService::sendRsiReport);
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

    @Scheduled(cron = "0 0 23 * * MON-FRI", zone = "CET")
    public void rsiStockMonitoring() {
        rootErrorHandler.run(rsiPriceFetcher::fetchStockClosingPrices);
        log.info("RSI daily stock price data fetch completed.");

        // Run RS analysis after RSI data is fetched (both use same price data)
        rootErrorHandler.run(relativeStrengthTracker::analyzeAndSendAlerts);
        log.info("Relative strength vs SPY analysis completed.");
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

    @Scheduled(cron = "0 40 15 * * MON-FRI", zone = "CET")
    protected void dailyBollingerBandReport() {
        rootErrorHandler.run(bollingerBandTracker::sendDailyReport);
        log.info("Daily Bollinger Band report completed.");
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "UTC")
    public void rsiCryptoMonitoring() {
        rootErrorHandler.run(rsiPriceFetcher::fetchCryptoClosingPrices);
        log.info("RSI daily crypto price data fetch completed.");
    }

    @Scheduled(fixedRate = 600000)
    public void cleanupIgnoreSymbols() {
        rootErrorHandler.run(
                () -> targetPriceProvider.cleanupIgnoreSymbols(IGNORE_DURATION_TTL_SECONDS));

        log.info("Cleanup of ignored symbols completed.");
    }

    @Scheduled(fixedRate = 60000)
    public void pollTelegramChatUpdates() {
        List<TelegramUpdateResponse> chatUpdates = telegramClient.getChatUpdates();
        rootErrorHandler.run(() -> telegramMessageProcessor.processUpdates(chatUpdates));

        log.info("Telegram chat updates processed.");
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

    @Scheduled(cron = "0 0 0 1 * *", zone = "UTC")
    public void monthlyApiUsageReport() {
        rootErrorHandler.run(
                () -> {
                    int finnhubCount = apiRequestMeteringService.getFinnhubRequestCount();
                    int coingeckoCount = apiRequestMeteringService.getCoingeckoRequestCount();

                    if (finnhubCount > 0 || coingeckoCount > 0) {
                        String previousMonth = apiRequestMeteringService.getPreviousMonth();

                        String message = String.format(
                                """
                                        *Monthly API Usage Report - %s*
                                        🔹 *Finnhub API*: %,d requests
                                        🔹 *CoinGecko API*: %,d requests
                                        🔹 *Total*: %,d requests""",
                                previousMonth,
                                finnhubCount,
                                coingeckoCount,
                                finnhubCount + coingeckoCount);

                        telegramClient.sendMessage(message);
                        log.info(
                                "Monthly API usage report sent for {}: Finnhub={}, CoinGecko={}",
                                previousMonth,
                                finnhubCount,
                                coingeckoCount);
                    } else {
                        log.info(
                                "No API requests recorded for the previous month, skipping report");
                    }

                    apiRequestMeteringService.resetCounters();
                });

        log.info("Monthly API usage report completed.");
    }

    public boolean manualStockMarketMonitoring() {
        boolean success = true;
        success &= rootErrorHandler.runWithStatus(finnhubPriceEvaluator::evaluatePrice);
        success &= rootErrorHandler.runWithStatus(sectorRelativeStrengthTracker::analyzeAndSendAlerts);
        success &= rootErrorHandler.runWithStatus(sectorMomentumRocTracker::analyzeAndSendAlerts);
        log.info("Manual stock market monitoring completed.");
        return success;
    }

    public boolean manualHourlySignalMonitoring() {
        boolean success = true;
        success &= rootErrorHandler.runWithStatus(bollingerBandTracker::analyzeAndSendAlerts);
        success &= rootErrorHandler.runWithStatus(rsiService::sendRsiReport);
        log.info("Manual hourly signal monitoring completed.");
        return success;
    }

    public boolean manualCryptoMarketMonitoring() {
        boolean success = rootErrorHandler.runWithStatus(coinGeckoPriceEvaluator::evaluatePrice);
        log.info("Manual crypto market monitoring completed.");
        return success;
    }

    public boolean manualRsiStockMonitoring() {
        boolean success = true;
        success &= rootErrorHandler.runWithStatus(rsiPriceFetcher::fetchStockClosingPrices);
        success &= rootErrorHandler.runWithStatus(relativeStrengthTracker::analyzeAndSendAlerts);
        log.info("Manual RSI stock monitoring completed.");
        return success;
    }

    public boolean manualRsiCryptoMonitoring() {
        boolean success = rootErrorHandler.runWithStatus(rsiPriceFetcher::fetchCryptoClosingPrices);
        log.info("Manual RSI crypto monitoring completed.");
        return success;
    }

    public boolean manualWeeklyInsiderTradingReport() {
        boolean success = rootErrorHandler.runWithStatus(insiderTracker::trackInsiderTransactions);
        log.info("Manual insider trading report completed.");
        return success;
    }

    public boolean manualDailySectorRotationTracking() {
        boolean success = rootErrorHandler.runWithStatus(
                sectorRotationTracker::fetchAndStoreDailyPerformance);
        log.info("Manual sector rotation tracking completed.");
        return success;
    }

    public boolean manualDailySectorRelativeStrengthReport() {
        boolean success = rootErrorHandler.runWithStatus(
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

    public boolean manualDailyBollingerBandReport() {
        boolean success = rootErrorHandler.runWithStatus(bollingerBandTracker::sendDailyReport);
        log.info("Manual Bollinger Band report completed.");
        return success;
    }

    public boolean manualMonthlyApiUsageReport() {
        boolean success = rootErrorHandler.runWithStatus(
                () -> {
                    monthlyApiUsageReport();
                });
        log.info("Manual monthly API usage report completed.");
        return success;
    }
}
