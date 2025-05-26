package org.tradelite.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.common.TickerSymbol;

@Slf4j
@RequiredArgsConstructor
public abstract class BasePriceEvaluator {

    private final TelegramClient telegramClient;

    public abstract void evaluatePrice() throws InterruptedException;

    protected void comparePrices(TickerSymbol ticker, double currentPrice, double targetPriceBuy, double targetPriceSell) {
        if (currentPrice >= targetPriceSell && (int) targetPriceSell > 0) {
            log.info("Potential sell opportunity for {}", ticker);
            telegramClient.broadcastMessage("Potential sell opportunity for " + ticker + ". Current Price: " + currentPrice + ", Target Price: " + targetPriceSell);
        }
        if (currentPrice <= targetPriceBuy && (int) targetPriceBuy > 0) {
            log.info("Potential buy opportunity for {}", ticker);
            telegramClient.broadcastMessage("Potential buy opportunity for " + ticker + ". Current Price: " + currentPrice + ", Target Price: " + targetPriceBuy);
        }
    }

}
