package org.tradelite;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.core.CoinGeckoPriceEvaluator;
import org.tradelite.core.FinnhubPriceEvaluator;
import org.tradelite.core.InsiderTracker;

@Slf4j
@Component
public class Scheduler {

    private final InsiderTracker insiderTracker;
    private final FinnhubPriceEvaluator finnhubPriceEvaluator;
    private final CoinGeckoPriceEvaluator coinGeckoPriceEvaluator;
    private final TargetPriceProvider targetPriceProvider;

    @Autowired
    public Scheduler(InsiderTracker insiderTracker, FinnhubPriceEvaluator finnhubPriceEvaluator, CoinGeckoPriceEvaluator coinGeckoPriceEvaluator,
                     TargetPriceProvider targetPriceProvider) {
        this.insiderTracker = insiderTracker;
        this.finnhubPriceEvaluator = finnhubPriceEvaluator;
        this.coinGeckoPriceEvaluator = coinGeckoPriceEvaluator;
        this.targetPriceProvider = targetPriceProvider;
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
        targetPriceProvider.cleanupIgnoreSymbols();
        log.info("Cleanup of ignored symbols completed.");
    }
}
