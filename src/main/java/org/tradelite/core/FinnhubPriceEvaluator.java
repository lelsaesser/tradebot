package org.tradelite.core;

import java.util.*;
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
import org.tradelite.service.LivePriceCache;
import org.tradelite.service.MarketStatusService;

@Slf4j
@Component
public class FinnhubPriceEvaluator extends BasePriceEvaluator {

    private final FinnhubClient finnhubClient;
    private final TargetPriceProvider targetPriceProvider;
    private final SymbolRegistry symbolRegistry;
    private final PriceQuoteRepository priceQuoteRepository;
    private final FeatureToggleService featureToggleService;
    private final MarketStatusService marketStatusService;
    private final LivePriceCache livePriceCache;

    @Autowired
    public FinnhubPriceEvaluator(
            FinnhubClient finnhubClient,
            TargetPriceProvider targetPriceProvider,
            TelegramGateway telegramClient,
            SymbolRegistry symbolRegistry,
            PriceQuoteRepository priceQuoteRepository,
            FeatureToggleService featureToggleService,
            MarketStatusService marketStatusService,
            LivePriceCache livePriceCache) {
        super(telegramClient, targetPriceProvider);
        this.finnhubClient = finnhubClient;
        this.targetPriceProvider = targetPriceProvider;
        this.symbolRegistry = symbolRegistry;
        this.priceQuoteRepository = priceQuoteRepository;
        this.featureToggleService = featureToggleService;
        this.marketStatusService = marketStatusService;
        this.livePriceCache = livePriceCache;
    }

    @SuppressWarnings("java:S135") // allow multiple continue in for-loop
    public int evaluatePrice() throws InterruptedException {
        int updatedCount = 0;

        // Loop 1: Fetch & cache prices for ALL symbols (stocks + ETFs)
        for (StockSymbol symbol : symbolRegistry.getAll()) {
            if (symbolRegistry.isInternationalSymbol(symbol.getTicker())) {
                continue;
            }
            PriceQuoteResponse priceQuote = finnhubClient.getPriceQuote(symbol);
            // Rate limit: Finnhub has 60 requests/minute limit, sleep after EVERY API call
            Thread.sleep(1100);

            Double lastPrice = livePriceCache.get(symbol.getTicker());
            if (priceQuote == null
                    || (lastPrice != null
                            && Math.abs(lastPrice - priceQuote.getCurrentPrice()) < 0.0001)) {
                continue;
            }
            livePriceCache.put(symbol.getTicker(), priceQuote.getCurrentPrice());

            // Persist price quote to SQLite for historical data collection (if enabled)
            if (featureToggleService.isEnabled(FeatureToggle.FINNHUB_PRICE_COLLECTION)
                    && marketStatusService.isMarketOpen(null)) {
                priceQuoteRepository.save(priceQuote);
            }

            evaluateHighPriceChange(priceQuote);
            updatedCount++;
        }

        // Loop 2: Evaluate target prices using cached data (no API calls)
        // Only evaluate domestic (US) symbols — international symbols are handled by
        // YahooPriceEvaluator
        for (TargetPrice targetPrice : targetPriceProvider.getStockTargetPrices()) {
            if (symbolRegistry.isInternationalSymbol(targetPrice.getSymbol())) {
                continue;
            }
            Double price = livePriceCache.get(targetPrice.getSymbol());
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
        evaluateHighPriceChange(priceQuote.getStockSymbol(), priceQuote.getChangePercent());
    }
}
