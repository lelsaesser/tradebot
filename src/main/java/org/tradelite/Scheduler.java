package org.tradelite;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.client.telegram.TelegramMessageProcessor;
import org.tradelite.client.telegram.dto.TelegramUpdateResponse;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.core.CoinGeckoPriceEvaluator;
import org.tradelite.core.FinnhubPriceEvaluator;
import org.tradelite.core.InsiderTracker;

import java.util.List;

import static org.tradelite.common.TargetPriceProvider.IGNORE_DURATION_TTL_SECONDS;

@Slf4j
@Component
public class Scheduler {

    private final InsiderTracker insiderTracker;
    private final FinnhubPriceEvaluator finnhubPriceEvaluator;
    private final CoinGeckoPriceEvaluator coinGeckoPriceEvaluator;
    private final TargetPriceProvider targetPriceProvider;
    private final TelegramClient telegramClient;
    private final TelegramMessageProcessor telegramMessageProcessor;

    @Autowired
    Scheduler(InsiderTracker insiderTracker, FinnhubPriceEvaluator finnhubPriceEvaluator, CoinGeckoPriceEvaluator coinGeckoPriceEvaluator,
                     TargetPriceProvider targetPriceProvider, TelegramClient telegramClient, TelegramMessageProcessor telegramMessageProcessor) {
        this.insiderTracker = insiderTracker;
        this.finnhubPriceEvaluator = finnhubPriceEvaluator;
        this.coinGeckoPriceEvaluator = coinGeckoPriceEvaluator;
        this.targetPriceProvider = targetPriceProvider;
        this.telegramClient = telegramClient;
        this.telegramMessageProcessor = telegramMessageProcessor;
    }

    //@Scheduled(fixedRate = 60 * 60 * 1000)
    private void onApplicationReady() throws InterruptedException {
        insiderTracker.evaluateInsiderActivity();
        insiderTracker.evaluateInsiderSentiment();

    }

    @Scheduled(initialDelay = 0, fixedRate = 300000)
    private void scheduledActivity() throws InterruptedException {
        coinGeckoPriceEvaluator.evaluatePrice();
        finnhubPriceEvaluator.evaluatePrice();

        log.info("Market monitoring round completed.");
    }

    @Scheduled(fixedRate = 600000)
    private void cleanupIgnoreSymbols() {
        targetPriceProvider.cleanupIgnoreSymbols(IGNORE_DURATION_TTL_SECONDS);

        log.info("Cleanup of ignored symbols completed.");
    }

    @Scheduled(fixedRate = 60000)
    private void pollTelegramChatUpdates() {
        List<TelegramUpdateResponse> chatUpdates = telegramClient.getChatUpdates();
        telegramMessageProcessor.processUpdates(chatUpdates);

        log.info("Telegram chat updates processed.");
    }
}
