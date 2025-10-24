package org.tradelite;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.client.telegram.TelegramMessageProcessor;
import org.tradelite.client.telegram.dto.TelegramUpdateResponse;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.core.*;
import org.tradelite.service.ApiRequestMeteringService;
import org.tradelite.utils.DateUtil;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.tradelite.common.TargetPriceProvider.IGNORE_DURATION_TTL_SECONDS;

@Slf4j
@Component
public class Scheduler {

    private final FinnhubPriceEvaluator finnhubPriceEvaluator;
    private final CoinGeckoPriceEvaluator coinGeckoPriceEvaluator;
    private final TargetPriceProvider targetPriceProvider;
    private final TelegramClient telegramClient;
    private final TelegramMessageProcessor telegramMessageProcessor;
    private final RootErrorHandler rootErrorHandler;
    private final InsiderTracker insiderTracker;
    private final RsiPriceFetcher rsiPriceFetcher;
    private final ApiRequestMeteringService apiRequestMeteringService;

    protected DayOfWeek dayOfWeek = null;
    protected LocalTime localTime = null;


    @Autowired
    Scheduler(FinnhubPriceEvaluator finnhubPriceEvaluator, CoinGeckoPriceEvaluator coinGeckoPriceEvaluator,
              TargetPriceProvider targetPriceProvider, TelegramClient telegramClient, TelegramMessageProcessor telegramMessageProcessor,
              RootErrorHandler rootErrorHandler, InsiderTracker insiderTracker, RsiPriceFetcher rsiPriceFetcher,
              ApiRequestMeteringService apiRequestMeteringService) {
        this.finnhubPriceEvaluator = finnhubPriceEvaluator;
        this.coinGeckoPriceEvaluator = coinGeckoPriceEvaluator;
        this.rsiPriceFetcher = rsiPriceFetcher;
        this.targetPriceProvider = targetPriceProvider;
        this.telegramClient = telegramClient;
        this.telegramMessageProcessor = telegramMessageProcessor;
        this.rootErrorHandler = rootErrorHandler;
        this.insiderTracker = insiderTracker;
        this.apiRequestMeteringService = apiRequestMeteringService;
    }

    @Scheduled(initialDelay = 0, fixedRate = 300000)
    protected void stockMarketMonitoring() {
        if (DateUtil.isStockMarketOpen(dayOfWeek, localTime)) {
            rootErrorHandler.run(finnhubPriceEvaluator::evaluatePrice);
        } else {
            log.info("Market is off-hours or it's a weekend. Skipping price evaluation.");
        }
        log.info("Stock market monitoring round completed.");
    }

    @Scheduled(initialDelay = 0, fixedRate = 420000)
    protected void cryptoMarketMonitoring() {
        rootErrorHandler.run(coinGeckoPriceEvaluator::evaluatePrice);
        log.info("Crypto market monitoring round completed.");
    }

    @Scheduled(cron = "0 0 23 * * MON-FRI", zone = "CET")
    protected void rsiStockMonitoring() {
        rootErrorHandler.run(rsiPriceFetcher::fetchStockClosingPrices);

        log.info("RSI daily stock price data fetch completed.");
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "UTC")
    protected void rsiCryptoMonitoring() {
        rootErrorHandler.run(rsiPriceFetcher::fetchCryptoClosingPrices);

        log.info("RSI daily crypto price data fetch completed.");
    }

    @Scheduled(fixedRate = 600000)
    protected void cleanupIgnoreSymbols() {
        rootErrorHandler.run(() -> targetPriceProvider.cleanupIgnoreSymbols(IGNORE_DURATION_TTL_SECONDS));

        log.info("Cleanup of ignored symbols completed.");
    }

    @Scheduled(fixedRate = 60000)
    protected void pollTelegramChatUpdates() {
        List<TelegramUpdateResponse> chatUpdates = telegramClient.getChatUpdates();
        rootErrorHandler.run(() -> telegramMessageProcessor.processUpdates(chatUpdates));

        log.info("Telegram chat updates processed.");
    }

    @Scheduled(cron = "0 0 12 ? * SAT", zone = "CET")
    protected void weeklyInsiderTradingReport() {
        rootErrorHandler.run(insiderTracker::trackInsiderTransactions);

        log.info("Weekly insider trading report generated.");
    }

    @Scheduled(cron = "0 0 0 1 * *", zone = "UTC")
    protected void monthlyApiUsageReport() {
        rootErrorHandler.run(() -> {
            int finnhubCount = apiRequestMeteringService.getFinnhubRequestCount();
            int coingeckoCount = apiRequestMeteringService.getCoingeckoRequestCount();
            
            if (finnhubCount > 0 || coingeckoCount > 0) {
                String previousMonth = apiRequestMeteringService.getCurrentMonth();
                
                String message = String.format("""
                        📊 *Monthly API Usage Report - %s*
                        "🔹 *Finnhub API*: %,d requests
                        "🔹 *CoinGecko API*: %,d requests
                        "🔹 *Total*: %,d requests""",
                        previousMonth, finnhubCount, coingeckoCount, finnhubCount + coingeckoCount);
                
                telegramClient.sendMessage(message);
                log.info("Monthly API usage report sent for {}: Finnhub={}, CoinGecko={}", 
                        previousMonth, finnhubCount, coingeckoCount);
            } else {
                log.info("No API requests recorded for the previous month, skipping report");
            }
            
            apiRequestMeteringService.resetCounters();
        });

        log.info("Monthly API usage report completed.");
    }
}
