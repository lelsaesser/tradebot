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

    protected DayOfWeek dayOfWeek = null;
    protected LocalTime localTime = null;


    @Autowired
    Scheduler(FinnhubPriceEvaluator finnhubPriceEvaluator, CoinGeckoPriceEvaluator coinGeckoPriceEvaluator,
              TargetPriceProvider targetPriceProvider, TelegramClient telegramClient, TelegramMessageProcessor telegramMessageProcessor,
              RootErrorHandler rootErrorHandler) {
        this.finnhubPriceEvaluator = finnhubPriceEvaluator;
        this.coinGeckoPriceEvaluator = coinGeckoPriceEvaluator;
        this.targetPriceProvider = targetPriceProvider;
        this.telegramClient = telegramClient;
        this.telegramMessageProcessor = telegramMessageProcessor;
        this.rootErrorHandler = rootErrorHandler;
    }

    @Scheduled(initialDelay = 0, fixedRate = 300000)
    protected void marketMonitoring() {
        if (DateUtil.isStockMarketOpen(dayOfWeek, localTime)) {
            rootErrorHandler.run(finnhubPriceEvaluator::evaluatePrice);
        } else {
            log.info("Market is off-hours or it's a weekend. Skipping price evaluation.");
        }
        rootErrorHandler.run(coinGeckoPriceEvaluator::evaluatePrice);

        log.info("Market monitoring round completed.");
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
}
