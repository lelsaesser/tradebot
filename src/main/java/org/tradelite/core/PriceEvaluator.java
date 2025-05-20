package org.tradelite.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.client.FinnhubClient;
import org.tradelite.client.dto.PriceQuoteResponse;
import org.tradelite.common.StockTicker;
import org.tradelite.common.TargetPrice;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class PriceEvaluator {

    private final FinnhubClient finnhubClient;
    private final TargetPriceManager targetPriceManager;

    @Autowired
    public PriceEvaluator(FinnhubClient finnhubClient, TargetPriceManager targetPriceManager) {
        this.finnhubClient = finnhubClient;
        this.targetPriceManager = targetPriceManager;
    }

    public void evaluatePriceQuotes() throws InterruptedException {
        List<StockTicker> tickers = StockTicker.getAll();
        List<PriceQuoteResponse> priceQuotes = new ArrayList<>();
        List<TargetPrice> targetPrices = targetPriceManager.getTargetPrices();

        for (StockTicker ticker : tickers) {
            PriceQuoteResponse priceQuote = finnhubClient.getPriceQuote(ticker);
            priceQuotes.add(priceQuote);
            Thread.sleep(100);
        }

        int totalCalls = 0;

        for (PriceQuoteResponse priceQuote : priceQuotes) {
            for (TargetPrice targetPrice : targetPrices) {
                if (priceQuote.getStockTicker().equals(targetPrice.getTicker())) {
                    double currentPrice = priceQuote.getCurrentPrice();
                    double targetPriceBuy = targetPrice.getTargetPriceBuy();
                    double targetPriceSell = targetPrice.getTargetPriceSell();

                    if (currentPrice >= targetPriceSell && (int) targetPriceSell > 0) {
                        totalCalls++;
                        log.info("Potential sell opportunity for {}", priceQuote.getStockTicker());
                    }
                    if (currentPrice <= targetPriceBuy && (int) targetPriceBuy > 0) {
                        totalCalls++;
                        log.info("Potential buy opportunity for {}", priceQuote.getStockTicker());
                    }
                }
            }
        }

        if (totalCalls == 0) {
            log.info("Market monitoring completed. No potential buy or sell opportunities.");
        }

    }
}
