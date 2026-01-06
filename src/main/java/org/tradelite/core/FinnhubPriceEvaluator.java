package org.tradelite.core;

import java.util.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.client.finnhub.FinnhubClient;
import org.tradelite.client.finnhub.dto.PriceQuoteResponse;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.trading.DemoTradingService;

@Slf4j
@Component
public class FinnhubPriceEvaluator extends BasePriceEvaluator {

    private final FinnhubClient finnhubClient;
    private final TargetPriceProvider targetPriceProvider;
    private final TelegramClient telegramClient;

    @Getter
    protected final Map<StockSymbol, Double> lastPriceCache = new EnumMap<>(StockSymbol.class);

    @Autowired
    public FinnhubPriceEvaluator(
            FinnhubClient finnhubClient,
            TargetPriceProvider targetPriceProvider,
            TelegramClient telegramClient,
            DemoTradingService demoTradingService) {
        super(telegramClient, targetPriceProvider, demoTradingService);
        this.finnhubClient = finnhubClient;
        this.targetPriceProvider = targetPriceProvider;
        this.telegramClient = telegramClient;
    }

    @SuppressWarnings("java:S135") // allow multiple continue in for-loop
    public int evaluatePrice() throws InterruptedException {
        List<PriceQuoteResponse> finnhubData = new ArrayList<>();
        List<TargetPrice> targetPrices = targetPriceProvider.getStockTargetPrices();

        for (TargetPrice targetPrice : targetPrices) {
            Optional<StockSymbol> ticker = StockSymbol.fromString(targetPrice.getSymbol());
            if (ticker.isEmpty()) {
                log.warn(
                        "Target price symbol {} not found in StockSymbol enum",
                        targetPrice.getSymbol());
                continue;
            }

            PriceQuoteResponse priceQuote = finnhubClient.getPriceQuote(ticker.get());

            Double lastPrice = lastPriceCache.get(ticker.get());
            if (priceQuote == null
                    || (lastPrice != null
                            && Math.abs(lastPrice - priceQuote.getCurrentPrice()) < 0.0001)) {
                continue;
            }
            lastPriceCache.put(ticker.get(), priceQuote.getCurrentPrice());

            finnhubData.add(priceQuote);
            Thread.sleep(100);
        }

        for (PriceQuoteResponse priceQuote : finnhubData) {
            evaluateHighPriceChange(priceQuote);

            for (TargetPrice targetPrice : targetPrices) {
                if (priceQuote.getStockSymbol().getTicker().equals(targetPrice.getSymbol())) {
                    comparePrices(
                            priceQuote.getStockSymbol(),
                            priceQuote.getCurrentPrice(),
                            targetPrice.getBuyTarget(),
                            targetPrice.getSellTarget());
                }
            }
        }
        return finnhubData.size();
    }

    public void evaluateHighPriceChange(PriceQuoteResponse priceQuote) {
        double percentChange = priceQuote.getChangePercent();
        double absPercentChange = Math.abs(percentChange);

        if (absPercentChange < 5.0) {
            return;
        }

        int alertThreshold = (int) (absPercentChange / 5.0) * 5;

        if (alertThreshold > 0
                && !targetPriceProvider.isSymbolIgnored(
                        priceQuote.getStockSymbol(),
                        IgnoreReason.CHANGE_PERCENT_ALERT,
                        alertThreshold)) {
            String displayName = priceQuote.getStockSymbol().getDisplayName();
            log.info("High price change detected for {}: {}%", displayName, percentChange);
            String emoji = percentChange > 0 ? "📈" : "📉";
            telegramClient.sendMessage(
                    emoji
                            + " High daily price swing detected for "
                            + displayName
                            + ": "
                            + String.format("%.2f", percentChange)
                            + "%");
            targetPriceProvider.addIgnoredSymbol(
                    priceQuote.getStockSymbol(), IgnoreReason.CHANGE_PERCENT_ALERT, alertThreshold);
        }
    }
}
