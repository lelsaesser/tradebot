package org.tradelite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.tradelite.core.InsiderTracker;
import org.tradelite.core.PriceEvaluator;

@Component
public class Entrypoint {

    private final InsiderTracker insiderTracker;
    private final PriceEvaluator priceEvaluator;

    @Autowired
    public Entrypoint(InsiderTracker insiderTracker, PriceEvaluator priceEvaluator) {
        this.insiderTracker = insiderTracker;
        this.priceEvaluator = priceEvaluator;
    }

    @Scheduled(fixedRate = 60 * 60 * 1000)
    public void onApplicationReady() throws InterruptedException {
        insiderTracker.evaluateInsiderActivity();
        insiderTracker.evaluateInsiderSentiment();

    }

    @Scheduled(initialDelay = 0, fixedRate = 300000)
    public void scheduledActivity() throws InterruptedException {
        priceEvaluator.evaluatePriceQuotes();
    }
}
