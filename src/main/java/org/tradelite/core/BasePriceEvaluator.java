package org.tradelite.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.SymbolType;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.common.TickerSymbol;

@Slf4j
@RequiredArgsConstructor
public abstract class BasePriceEvaluator {

    private final TelegramGateway telegramClient;
    private final TargetPriceProvider targetPriceProvider;

    public abstract int evaluatePrice() throws InterruptedException;

    protected void evaluateHighPriceChange(StockSymbol symbol, double changePercent) {
        double absPercentChange = Math.abs(changePercent);
        if (absPercentChange < 5.0) {
            return;
        }

        int alertThreshold = (int) (absPercentChange / 5.0) * 5;
        if (alertThreshold > 0
                && !targetPriceProvider.isSymbolIgnored(
                        symbol, IgnoreReason.CHANGE_PERCENT_ALERT, alertThreshold)) {
            String displayName = symbol.getDisplayName();
            log.info("High price change detected for {}: {}%", displayName, changePercent);
            String emoji = changePercent > 0 ? "\uD83D\uDCC8" : "\uD83D\uDCC9";
            telegramClient.sendMessage(
                    emoji + " " + displayName + ": " + String.format("%.2f", changePercent) + "%");
            targetPriceProvider.addIgnoredSymbol(
                    symbol, IgnoreReason.CHANGE_PERCENT_ALERT, alertThreshold);
        }
    }

    protected void comparePrices(
            TickerSymbol ticker,
            double currentPrice,
            double targetPriceBuy,
            double targetPriceSell) {
        String displayName = ticker.getName();
        if (ticker.getSymbolType() == SymbolType.STOCK) {
            displayName = ticker.getDisplayName();
        }

        if (currentPrice >= targetPriceSell && (int) targetPriceSell > 0) {
            if (targetPriceProvider.isSymbolIgnored(ticker, IgnoreReason.SELL_ALERT)) {
                return;
            }
            log.info("Potential sell opportunity for {}", displayName);
            telegramClient.sendMessage(
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
            telegramClient.sendMessage(
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
