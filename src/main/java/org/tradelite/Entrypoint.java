package org.tradelite;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.tradelite.core.CoinGeckoPriceEvaluator;
import org.tradelite.core.FinnhubPriceEvaluator;
import org.tradelite.core.InsiderTracker;

@Slf4j
@Component
public class Entrypoint {

    private final InsiderTracker insiderTracker;
    private final FinnhubPriceEvaluator finnhubPriceEvaluator;
    private final CoinGeckoPriceEvaluator coinGeckoPriceEvaluator;

    @Autowired
    public Entrypoint(InsiderTracker insiderTracker, FinnhubPriceEvaluator finnhubPriceEvaluator, CoinGeckoPriceEvaluator coinGeckoPriceEvaluator) {
        this.insiderTracker = insiderTracker;
        this.finnhubPriceEvaluator = finnhubPriceEvaluator;
        this.coinGeckoPriceEvaluator = coinGeckoPriceEvaluator;
    }

    //@Scheduled(fixedRate = 60 * 60 * 1000)
    public void onApplicationReady() throws InterruptedException {
        insiderTracker.evaluateInsiderActivity();
        insiderTracker.evaluateInsiderSentiment();

    }

    @Scheduled(initialDelay = 0, fixedRate = 300000)
    public void scheduledActivity() throws InterruptedException {
        coinGeckoPriceEvaluator.evaluatePrice();
        finnhubPriceEvaluator.evaluatePrice();

        log.info("Market monitoring round completed.");
    }
}
