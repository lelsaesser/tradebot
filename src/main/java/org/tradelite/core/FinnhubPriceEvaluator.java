package org.tradelite.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.client.finnhub.FinnhubClient;
import org.tradelite.client.finnhub.dto.PriceQuoteResponse;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class FinnhubPriceEvaluator extends BasePriceEvaluator {

    private final FinnhubClient finnhubClient;
    private final TargetPriceProvider targetPriceProvider;
    private final TelegramClient telegramClient;

    protected final Map<StockSymbol, Double> lastPriceCache = new EnumMap<>(StockSymbol.class);

    @Autowired
    public FinnhubPriceEvaluator(FinnhubClient finnhubClient, TargetPriceProvider targetPriceProvider, TelegramClient telegramClient) {
        super(telegramClient, targetPriceProvider);
        this.finnhubClient = finnhubClient;
        this.targetPriceProvider = targetPriceProvider;
        this.telegramClient = telegramClient;
    }

    public int evaluatePrice() throws InterruptedException {
        List<StockSymbol> tickers = StockSymbol.getAll();
        List<PriceQuoteResponse> finnhubData = new ArrayList<>();
        List<TargetPrice> targetPrices = targetPriceProvider.getStockTargetPrices();

        for (StockSymbol ticker : tickers) {
            PriceQuoteResponse priceQuote = finnhubClient.getPriceQuote(ticker);

            Double lastPrice = lastPriceCache.get(ticker);
            if (priceQuote == null || (lastPrice != null && Math.abs(lastPrice - priceQuote.getCurrentPrice()) < 0.0001)) {
                continue;
            }
            lastPriceCache.put(ticker, priceQuote.getCurrentPrice());

            finnhubData.add(priceQuote);
            Thread.sleep(100);
        }

        for (PriceQuoteResponse priceQuote : finnhubData) {
            evaluateHighPriceChange(priceQuote);

            for (TargetPrice targetPrice : targetPrices) {
                if (priceQuote.getStockSymbol().getTicker().equals(targetPrice.getSymbol())) {
                    comparePrices(priceQuote.getStockSymbol(), priceQuote.getCurrentPrice(), targetPrice.getBuyTarget(), targetPrice.getSellTarget());
                }
            }
        }
        return finnhubData.size();
    }

    public void evaluateHighPriceChange(PriceQuoteResponse priceQuote) {
        double percentChange = priceQuote.getChangePercent();

        if (percentChange > 5.0 && !targetPriceProvider.isSymbolIgnored(priceQuote.getStockSymbol(), IgnoreReason.CHANGE_PERCENT_ALERT)) {
            log.info("High price change detected for {}: {}%", priceQuote.getStockSymbol(), percentChange);
            telegramClient.sendMessage("⚠️ High daily price swing detected for " + priceQuote.getStockSymbol() + ": " + percentChange + "%");
            targetPriceProvider.addIgnoredSymbol(priceQuote.getStockSymbol(), IgnoreReason.CHANGE_PERCENT_ALERT);
        }
    }
}
