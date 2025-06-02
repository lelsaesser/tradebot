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
import java.util.List;

@Slf4j
@Component
public class FinnhubPriceEvaluator extends BasePriceEvaluator {

    private final FinnhubClient finnhubClient;
    private final TargetPriceProvider targetPriceProvider;

    @Autowired
    public FinnhubPriceEvaluator(FinnhubClient finnhubClient, TargetPriceProvider targetPriceProvider, TelegramClient telegramClient) {
        super(telegramClient, targetPriceProvider);
        this.finnhubClient = finnhubClient;
        this.targetPriceProvider = targetPriceProvider;
    }

    public void evaluatePrice() throws InterruptedException {
        List<StockSymbol> tickers = StockSymbol.getAll();
        List<PriceQuoteResponse> finnhubData = new ArrayList<>();
        List<TargetPrice> targetPrices = targetPriceProvider.getStockTargetPrices();

        for (StockSymbol ticker : tickers) {
            PriceQuoteResponse priceQuote = finnhubClient.getPriceQuote(ticker);
            finnhubData.add(priceQuote);
            Thread.sleep(100);
        }

        for (PriceQuoteResponse priceQuote : finnhubData) {
            for (TargetPrice targetPrice : targetPrices) {
                if (priceQuote.getStockSymbol().getTicker().equals(targetPrice.getSymbol())) {
                    comparePrices(priceQuote.getStockSymbol(), priceQuote.getCurrentPrice(), targetPrice.getBuyTarget(), targetPrice.getSellTarget());
                }
            }
        }
    }
}
