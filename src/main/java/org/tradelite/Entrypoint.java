package org.tradelite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        insiderTracker.evaluateInsiderActivity();
        insiderTracker.evaluateInsiderSentiment();

    }

    @Scheduled(initialDelay = 0, fixedRate = 300000)
    public void scheduledActivity() {
        priceEvaluator.evaluatePriceQuotes();
    }
}
