package org.tradelite.core;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.client.finnhub.FinnhubClient;
import org.tradelite.client.finnhub.dto.PriceQuoteResponse;
import org.tradelite.common.FeatureToggle;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.repository.PriceQuoteEntity;
import org.tradelite.repository.PriceQuoteRepository;
import org.tradelite.service.FeatureToggleService;
import org.tradelite.service.StockSymbolRegistry;

@Slf4j
@Component
public class FinnhubPriceEvaluator extends BasePriceEvaluator {

    private final FinnhubClient finnhubClient;
    private final TargetPriceProvider targetPriceProvider;
    private final TelegramGateway telegramClient;
    private final StockSymbolRegistry stockSymbolRegistry;
    private final PriceQuoteRepository priceQuoteRepository;
    private final FeatureToggleService featureToggleService;

    @Getter protected final Map<String, Double> lastPriceCache = new ConcurrentHashMap<>();

    @Autowired
    public FinnhubPriceEvaluator(
            FinnhubClient finnhubClient,
            TargetPriceProvider targetPriceProvider,
            TelegramGateway telegramClient,
            StockSymbolRegistry stockSymbolRegistry,
            PriceQuoteRepository priceQuoteRepository,
            FeatureToggleService featureToggleService) {
        super(telegramClient, targetPriceProvider);
        this.finnhubClient = finnhubClient;
        this.targetPriceProvider = targetPriceProvider;
        this.telegramClient = telegramClient;
        this.stockSymbolRegistry = stockSymbolRegistry;
        this.priceQuoteRepository = priceQuoteRepository;
        this.featureToggleService = featureToggleService;
    }

    @SuppressWarnings("java:S135") // allow multiple continue in for-loop
    public int evaluatePrice() throws InterruptedException {
        List<PriceQuoteResponse> finnhubData = new ArrayList<>();
        List<TargetPrice> targetPrices = targetPriceProvider.getStockTargetPrices();

        for (TargetPrice targetPrice : targetPrices) {
            Optional<StockSymbol> ticker = stockSymbolRegistry.fromString(targetPrice.getSymbol());
            if (ticker.isEmpty()) {
                log.warn(
                        "Target price symbol {} not found in stock symbol registry",
                        targetPrice.getSymbol());
                continue;
            }

            PriceQuoteResponse priceQuote = finnhubClient.getPriceQuote(ticker.get());
            // Rate limit: Finnhub has 60 requests/minute limit, sleep after EVERY API call
            Thread.sleep(1100);

            Double lastPrice = lastPriceCache.get(ticker.get().getTicker());
            if (priceQuote == null
                    || (lastPrice != null
                            && Math.abs(lastPrice - priceQuote.getCurrentPrice()) < 0.0001)) {
                continue;
            }
            lastPriceCache.put(ticker.get().getTicker(), priceQuote.getCurrentPrice());

            // Persist price quote to SQLite for historical data collection (if enabled)
            if (featureToggleService.isEnabled(FeatureToggle.FINNHUB_PRICE_COLLECTION)
                    && !isPotentialMarketHoliday(
                            ticker.get().getTicker(), priceQuote.getCurrentPrice())) {
                priceQuoteRepository.save(priceQuote);
            }

            finnhubData.add(priceQuote);
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

    public boolean isPotentialMarketHoliday(String symbol, double currentPrice) {
        LocalDate today = LocalDate.now();

        List<PriceQuoteEntity> todayEntries =
                priceQuoteRepository.findBySymbolAndDate(symbol, today);
        if (!todayEntries.isEmpty()) {
            return false;
        }

        Optional<PriceQuoteEntity> latestPersisted =
                priceQuoteRepository.findLatestBySymbol(symbol);
        if (latestPersisted.isEmpty()) {
            return false;
        }

        double lastPersistedPrice = latestPersisted.get().getCurrentPrice();
        if (lastPersistedPrice == currentPrice) {
            log.info(
                    "Potential market holiday detected for {}: persisted price {} == current price {}, skipping SQLite persistence",
                    symbol,
                    lastPersistedPrice,
                    currentPrice);
            return true;
        }

        return false;
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
