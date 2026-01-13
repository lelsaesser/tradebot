package org.tradelite.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.SymbolType;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.common.TickerSymbol;
import org.tradelite.service.NotificationService;

@Slf4j
@RequiredArgsConstructor
public abstract class BasePriceEvaluator {

    private final NotificationService notificationService;
    private final TargetPriceProvider targetPriceProvider;

    public abstract int evaluatePrice() throws InterruptedException;

    protected void comparePrices(
            TickerSymbol ticker,
            double currentPrice,
            double targetPriceBuy,
            double targetPriceSell) {
        String displayName = ticker.getName();
        if (ticker.getSymbolType() == SymbolType.STOCK) {
            displayName = ((StockSymbol) ticker).getDisplayName();
        }

        if (currentPrice >= targetPriceSell && (int) targetPriceSell > 0) {
            if (targetPriceProvider.isSymbolIgnored(ticker, IgnoreReason.SELL_ALERT)) {
                return;
            }
            log.info("Potential sell opportunity for {}", displayName);
            notificationService.sendNotification(
                    "💰 Potential sell opportunity for "
                            + displayName
                            + ". Current Price: "
                            + currentPrice
                            + ", Target Price: "
                            + targetPriceSell);
            targetPriceProvider.addIgnoredSymbol(ticker, IgnoreReason.SELL_ALERT);
        }

        if (currentPrice <= targetPriceBuy && (int) targetPriceBuy > 0) {
            if (targetPriceProvider.isSymbolIgnored(ticker, IgnoreReason.BUY_ALERT)) {
                return;
            }
            log.info("Potential buy opportunity for {}", displayName);
            notificationService.sendNotification(
                    "🚀 Potential buy opportunity for "
                            + displayName
                            + ". Current Price: "
                            + currentPrice
                            + ", Target Price: "
                            + targetPriceBuy);
            targetPriceProvider.addIgnoredSymbol(ticker, IgnoreReason.BUY_ALERT);
        }
    }
}
