package org.tradelite.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.common.TickerSymbol;

@Slf4j
@RequiredArgsConstructor
public abstract class BasePriceEvaluator {

    private final TelegramClient telegramClient;
    private final TargetPriceProvider targetPriceProvider;

    public abstract void evaluatePrice() throws InterruptedException;

    protected void comparePrices(TickerSymbol ticker, double currentPrice, double targetPriceBuy, double targetPriceSell) {
        if (targetPriceProvider.isSymbolIgnored(ticker)) {
            log.info("Notification for {} was already sent within 2 hours. Skipping.", ticker);
            return;
        }
        if (currentPrice >= targetPriceSell && (int) targetPriceSell > 0) {
            log.info("Potential sell opportunity for {}", ticker);
            telegramClient.sendMessage("\uD83D\uDCB0 Potential sell opportunity for " + ticker + ". Current Price: " + currentPrice + ", Target Price: " + targetPriceSell);
            targetPriceProvider.addIgnoredSymbol(ticker);
        }
        if (currentPrice <= targetPriceBuy && (int) targetPriceBuy > 0) {
            log.info("Potential buy opportunity for {}", ticker);
            telegramClient.sendMessage("\uD83D\uDE80 Potential buy opportunity for " + ticker + ". Current Price: " + currentPrice + ", Target Price: " + targetPriceBuy);
            targetPriceProvider.addIgnoredSymbol(ticker);
        }
    }

}
