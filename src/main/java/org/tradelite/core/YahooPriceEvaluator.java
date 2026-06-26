package org.tradelite.core;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.client.finnhub.dto.PriceQuoteResponse;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.client.yahoo.YahooFetchException;
import org.tradelite.client.yahoo.YahooFinanceClient;
import org.tradelite.client.yahoo.YahooPriceQuote;
import org.tradelite.common.FeatureToggle;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.SymbolRegistry;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.repository.PriceQuoteRepository;
import org.tradelite.service.FeatureToggleService;
import org.tradelite.service.LivePriceCache;
import org.tradelite.service.MarketStatusService;
import org.tradelite.web.dashboard.DashboardEventPublisher;

@Slf4j
@Component
public class YahooPriceEvaluator extends BasePriceEvaluator {

    static final long REQUEST_DELAY_MS = 3000;

    private final YahooFinanceClient yahooFinanceClient;
    private final TargetPriceProvider targetPriceProvider;
    private final SymbolRegistry symbolRegistry;
    private final PriceQuoteRepository priceQuoteRepository;
    private final FeatureToggleService featureToggleService;
    private final MarketStatusService marketStatusService;
    private final LivePriceCache livePriceCache;

    @Autowired
    public YahooPriceEvaluator(
            YahooFinanceClient yahooFinanceClient,
            TargetPriceProvider targetPriceProvider,
            TelegramGateway telegramClient,
            SymbolRegistry symbolRegistry,
            PriceQuoteRepository priceQuoteRepository,
            FeatureToggleService featureToggleService,
            MarketStatusService marketStatusService,
            LivePriceCache livePriceCache,
            DashboardEventPublisher dashboardEventPublisher) {
        super(telegramClient, targetPriceProvider, dashboardEventPublisher);
        this.yahooFinanceClient = yahooFinanceClient;
        this.targetPriceProvider = targetPriceProvider;
        this.symbolRegistry = symbolRegistry;
        this.priceQuoteRepository = priceQuoteRepository;
        this.featureToggleService = featureToggleService;
        this.marketStatusService = marketStatusService;
        this.livePriceCache = livePriceCache;
    }

    @Override
    @SuppressWarnings("java:S135")
    public int evaluatePrice() throws InterruptedException {
        if (!featureToggleService.isEnabled(FeatureToggle.YAHOO_INTRADAY_PRICE_FETCH)) {
            return 0;
        }

        int updatedCount = 0;

        for (StockSymbol symbol : symbolRegistry.getInternationalStocks()) {
            if (!marketStatusService.isExchangeOpen(symbol.getTicker())) {
                continue;
            }

            YahooPriceQuote quote;
            try {
                quote = yahooFinanceClient.fetchCurrentPrice(symbol.getTicker());
            } catch (YahooFetchException e) {
                log.error(
                        "Yahoo price fetch failed for {}: {}", symbol.getTicker(), e.getMessage());
                Thread.sleep(REQUEST_DELAY_MS);
                continue;
            }
            Thread.sleep(REQUEST_DELAY_MS);

            Double lastPrice = livePriceCache.get(symbol.getTicker());
            if (lastPrice != null && Math.abs(lastPrice - quote.currentPrice()) < 0.0001) {
                continue;
            }
            livePriceCache.put(symbol.getTicker(), quote.currentPrice());

            if (featureToggleService.isEnabled(FeatureToggle.FINNHUB_PRICE_COLLECTION)) {
                persistQuote(symbol, quote);
            }

            evaluateHighPriceChange(symbol, quote);
            updatedCount++;
        }

        // Evaluate target prices for international symbols
        for (TargetPrice targetPrice : targetPriceProvider.getStockTargetPrices()) {
            if (!symbolRegistry.isInternationalSymbol(targetPrice.getSymbol())) {
                continue;
            }
            Double price = livePriceCache.get(targetPrice.getSymbol());
            if (price == null) {
                continue;
            }
            Optional<StockSymbol> ticker = symbolRegistry.fromString(targetPrice.getSymbol());
            if (ticker.isEmpty()) {
                continue;
            }
            comparePrices(
                    ticker.get(), price, targetPrice.getBuyTarget(), targetPrice.getSellTarget());
        }

        return updatedCount;
    }

    void evaluateHighPriceChange(StockSymbol symbol, YahooPriceQuote quote) {
        evaluateHighPriceChange(symbol, quote.changePercent());
    }

    private void persistQuote(StockSymbol symbol, YahooPriceQuote quote) {
        PriceQuoteResponse response = new PriceQuoteResponse();
        response.setStockSymbol(symbol);
        response.setTimestamp(quote.timestamp());
        response.setCurrentPrice(quote.currentPrice());
        response.setDailyOpen(quote.dailyOpen());
        response.setDailyHigh(quote.dailyHigh());
        response.setDailyLow(quote.dailyLow());
        response.setChange(quote.currentPrice() - quote.previousClose());
        response.setChangePercent(quote.changePercent());
        response.setPreviousClose(quote.previousClose());
        priceQuoteRepository.save(response);
    }
}
