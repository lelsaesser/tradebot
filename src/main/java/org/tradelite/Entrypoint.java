package org.tradelite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.tradelite.client.coingecko.CoinGeckoClient;
import org.tradelite.client.coingecko.CoinId;
import org.tradelite.client.coingecko.dto.CoinGeckoPriceResponse;
import org.tradelite.core.InsiderTracker;
import org.tradelite.core.PriceEvaluator;

@Component
public class Entrypoint {

    private final InsiderTracker insiderTracker;
    private final PriceEvaluator priceEvaluator;
    private final CoinGeckoClient coinGeckoClient;

    @Autowired
    public Entrypoint(InsiderTracker insiderTracker, PriceEvaluator priceEvaluator, CoinGeckoClient coinGeckoClient) {
        this.insiderTracker = insiderTracker;
        this.priceEvaluator = priceEvaluator;
        this.coinGeckoClient = coinGeckoClient;
    }

    //@Scheduled(fixedRate = 60 * 60 * 1000)
    public void onApplicationReady() throws InterruptedException {
        insiderTracker.evaluateInsiderActivity();
        insiderTracker.evaluateInsiderSentiment();

    }

    @Scheduled(initialDelay = 0, fixedRate = 300000)
    public void scheduledActivity() throws InterruptedException {
        CoinGeckoPriceResponse.CoinData data = coinGeckoClient.getCoinPriceData(CoinId.BITCOIN);
        priceEvaluator.evaluatePriceQuotes();
    }
}
