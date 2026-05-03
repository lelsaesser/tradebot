package org.tradelite.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.client.finnhub.FinnhubClient;
import org.tradelite.client.finnhub.dto.PriceQuoteResponse;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.common.FeatureToggle;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.SymbolRegistry;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.repository.PriceQuoteRepository;
import org.tradelite.service.FeatureToggleService;
import org.tradelite.service.MarketHolidayService;

@Slf4j
@Component
public class FinnhubPriceEvaluator extends BasePriceEvaluator {

    private final FinnhubClient finnhubClient;
    private final TargetPriceProvider targetPriceProvider;
    private final TelegramGateway telegramClient;
    private final SymbolRegistry symbolRegistry;
    private final PriceQuoteRepository priceQuoteRepository;
    private final FeatureToggleService featureToggleService;
    private final MarketHolidayService marketHolidayService;

    @Getter protected final Map<String, Double> lastPriceCache = new ConcurrentHashMap<>();

    @Autowired
    public FinnhubPriceEvaluator(
            FinnhubClient finnhubClient,
            TargetPriceProvider targetPriceProvider,
            TelegramGateway telegramClient,
            SymbolRegistry symbolRegistry,
            PriceQuoteRepository priceQuoteRepository,
            FeatureToggleService featureToggleService,
            MarketHolidayService marketHolidayService) {
        super(telegramClient, targetPriceProvider);
        this.finnhubClient = finnhubClient;
        this.targetPriceProvider = targetPriceProvider;
        this.telegramClient = telegramClient;
        this.symbolRegistry = symbolRegistry;
        this.priceQuoteRepository = priceQuoteRepository;
        this.featureToggleService = featureToggleService;
        this.marketHolidayService = marketHolidayService;
    }

    @SuppressWarnings("java:S135") // allow multiple continue in for-loop
    public int evaluatePrice() throws InterruptedException {
        int updatedCount = 0;

        // Loop 1: Fetch & cache prices for ALL symbols (stocks + ETFs)
        for (StockSymbol symbol : symbolRegistry.getAll()) {
            PriceQuoteResponse priceQuote = finnhubClient.getPriceQuote(symbol);
            // Rate limit: Finnhub has 60 requests/minute limit, sleep after EVERY API call
            Thread.sleep(1100);

            Double lastPrice = lastPriceCache.get(symbol.getTicker());
            if (priceQuote == null
                    || (lastPrice != null
                            && Math.abs(lastPrice - priceQuote.getCurrentPrice()) < 0.0001)) {
                continue;
            }
            lastPriceCache.put(symbol.getTicker(), priceQuote.getCurrentPrice());

            // Persist price quote to SQLite for historical data collection (if enabled)
            if (featureToggleService.isEnabled(FeatureToggle.FINNHUB_PRICE_COLLECTION)
                    && marketHolidayService.isMarketOpen(null)) {
                priceQuoteRepository.save(priceQuote);
            }

            evaluateHighPriceChange(priceQuote);
            updatedCount++;
        }

        // Loop 2: Evaluate target prices using cached data (no API calls)
        for (TargetPrice targetPrice : targetPriceProvider.getStockTargetPrices()) {
            Double price = lastPriceCache.get(targetPrice.getSymbol());
            if (price == null) {
                continue;
            }
            Optional<StockSymbol> ticker = symbolRegistry.fromString(targetPrice.getSymbol());
            if (ticker.isEmpty()) {
                log.warn(
                        "Target price symbol {} not found in stock symbol registry",
                        targetPrice.getSymbol());
                continue;
            }
            comparePrices(
                    ticker.get(), price, targetPrice.getBuyTarget(), targetPrice.getSellTarget());
        }

        return updatedCount;
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
                    emoji + " " + displayName + ": " + String.format("%.2f", percentChange) + "%");
            targetPriceProvider.addIgnoredSymbol(
                    priceQuote.getStockSymbol(), IgnoreReason.CHANGE_PERCENT_ALERT, alertThreshold);
        }
    }
}
