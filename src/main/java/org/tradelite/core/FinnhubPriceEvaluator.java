package org.tradelite.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.client.finnhub.FinnhubClient;
import org.tradelite.client.finnhub.dto.PriceQuoteResponse;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.TargetPrice;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class FinnhubPriceEvaluator extends BasePriceEvaluator {

    private final FinnhubClient finnhubClient;
    private final TargetPriceManager targetPriceManager;

    @Autowired
    public FinnhubPriceEvaluator(FinnhubClient finnhubClient, TargetPriceManager targetPriceManager, TelegramClient telegramClient) {
        super(telegramClient);
        this.finnhubClient = finnhubClient;
        this.targetPriceManager = targetPriceManager;
    }

    public void evaluatePrice() throws InterruptedException {
        List<StockSymbol> tickers = StockSymbol.getAll();
        List<PriceQuoteResponse> finnhubData = new ArrayList<>();
        List<TargetPrice> targetPrices = targetPriceManager.getTargetPrices();

        for (StockSymbol ticker : tickers) {
            PriceQuoteResponse priceQuote = finnhubClient.getPriceQuote(ticker);
            finnhubData.add(priceQuote);
            Thread.sleep(100);
        }

        for (PriceQuoteResponse priceQuote : finnhubData) {
            for (TargetPrice targetPrice : targetPrices) {
                if (priceQuote.getStockSymbol().getTicker().equals(targetPrice.getSymbol())) {
                    comparePrices(priceQuote.getStockSymbol(), priceQuote.getCurrentPrice(), targetPrice.getTargetPriceBuy(), targetPrice.getTargetPriceSell());
                }
            }
        }
    }
}
