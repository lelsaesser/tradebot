package org.tradelite;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.tradelite.core.CoinGeckoPriceEvaluator;
import org.tradelite.core.FinnhubPriceEvaluator;
import org.tradelite.core.InsiderTracker;
import org.tradelite.core.TargetPriceManager;

@Slf4j
@Component
public class Scheduler {

    private final InsiderTracker insiderTracker;
    private final FinnhubPriceEvaluator finnhubPriceEvaluator;
    private final CoinGeckoPriceEvaluator coinGeckoPriceEvaluator;
    private final TargetPriceManager targetPriceManager;

    @Autowired
    public Scheduler(InsiderTracker insiderTracker, FinnhubPriceEvaluator finnhubPriceEvaluator, CoinGeckoPriceEvaluator coinGeckoPriceEvaluator,
                     TargetPriceManager targetPriceManager) {
        this.insiderTracker = insiderTracker;
        this.finnhubPriceEvaluator = finnhubPriceEvaluator;
        this.coinGeckoPriceEvaluator = coinGeckoPriceEvaluator;
        this.targetPriceManager = targetPriceManager;
    }

    //@Scheduled(fixedRate = 60 * 60 * 1000)
    private void onApplicationReady() throws InterruptedException {
        insiderTracker.evaluateInsiderActivity();
        insiderTracker.evaluateInsiderSentiment();

    }

    @Scheduled(initialDelay = 0, fixedRate = 300000)
    private void scheduledActivity() throws InterruptedException {
        coinGeckoPriceEvaluator.evaluatePrice();
        finnhubPriceEvaluator.evaluatePrice();

        log.info("Market monitoring round completed.");
    }

    @Scheduled(fixedRate = 600000)
    private void cleanupIgnoreSymbols() {
        targetPriceManager.cleanupIgnoreSymbols();
        log.info("Cleanup of ignored symbols completed.");
    }
}
