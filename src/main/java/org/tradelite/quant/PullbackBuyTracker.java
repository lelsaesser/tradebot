package org.tradelite.quant;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.common.FeatureToggle;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.SymbolRegistry;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.core.IgnoreReason;
import org.tradelite.repository.ApexPerformerRepository;
import org.tradelite.service.FeatureToggleService;
import org.tradelite.service.LivePriceCache;
import org.tradelite.service.MarketStatusService;
import org.tradelite.service.RelativeStrengthService;
import org.tradelite.service.RelativeStrengthService.RsResult;

/**
 * Detects EMA pullback buy opportunities for tracked stocks.
 *
 * <p>A "healthy pullback" is when price drops below the short-term EMAs (9 and 21) but stays above
 * the longer-term EMAs (50, 100, 200), while both RS vs SPY and VFI confirm the stock is still
 * fundamentally strong. Sends one Telegram alert per qualifying stock.
 *
 * <p>Domestic and international stocks are handled by separate entry points so that the scheduler
 * can gate them independently against their respective trading sessions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PullbackBuyTracker {

    private final EmaService emaService;
    private final RelativeStrengthService relativeStrengthService;
    private final VfiService vfiService;
    private final LivePriceCache livePriceCache;
    private final TelegramGateway telegramClient;
    private final SymbolRegistry symbolRegistry;
    private final TargetPriceProvider targetPriceProvider;
    private final FeatureToggleService featureToggleService;
    private final ApexPerformerRepository apexPerformerRepository;
    private final MarketStatusService marketStatusService;

    /**
     * Evaluates pullback pattern for domestic stocks. Caller is responsible for NYSE-hours gating.
     */
    public void analyzeDomestic() {
        if (!featureToggleService.isEnabled(FeatureToggle.PULLBACK_BUY_ALERT)) {
            return;
        }

        Map<String, Double> priceCache = livePriceCache.getAll();
        Set<String> apexPerformers = apexPerformerRepository.findAll();

        for (StockSymbol stock : symbolRegistry.getDomesticStocks()) {
            evaluateAndAlert(stock, priceCache, apexPerformers);
        }
    }

    /**
     * Evaluates pullback pattern for international stocks (XETRA, KRX). Per-symbol exchange-hours
     * gate via {@link MarketStatusService#isExchangeOpen(String)} — caller can invoke this
     * unconditionally; closed exchanges are skipped silently.
     */
    public void analyzeInternational() {
        if (!featureToggleService.isEnabled(FeatureToggle.PULLBACK_BUY_ALERT)) {
            return;
        }

        Map<String, Double> priceCache = livePriceCache.getAll();
        Set<String> apexPerformers = apexPerformerRepository.findAll();

        for (StockSymbol stock : symbolRegistry.getInternationalStocks()) {
            if (!marketStatusService.isExchangeOpen(stock.getTicker())) {
                continue;
            }
            evaluateAndAlert(stock, priceCache, apexPerformers);
        }
    }

    private void evaluateAndAlert(
            StockSymbol stock, Map<String, Double> priceCache, Set<String> apexPerformers) {
        if (targetPriceProvider.isSymbolIgnored(stock, IgnoreReason.PULLBACK_BUY_ALERT)) {
            return;
        }

        Optional<EmaAnalysis> emaOpt =
                emaService.analyze(stock.getTicker(), stock.getCompanyName());
        if (emaOpt.isEmpty()) {
            log.warn("PullbackBuy skip: {} — EMA analysis returned empty", stock.getTicker());
            return;
        }

        EmaAnalysis ema = emaOpt.get();
        if (Double.isNaN(ema.ema50()) || Double.isNaN(ema.ema100()) || Double.isNaN(ema.ema200())) {
            log.info(
                    "Skipping {} — insufficient EMA data (ema50={}, ema100={}, ema200={})",
                    stock.getTicker(),
                    ema.ema50(),
                    ema.ema100(),
                    ema.ema200());
            return;
        }

        Double livePrice = priceCache.get(stock.getTicker());
        if (livePrice == null) {
            log.warn("Skipping {} — no cached price", stock.getTicker());
            return;
        }

        if (!isPullbackPattern(livePrice, ema)) {
            log.debug(
                    "Skipping {} — no pullback pattern (price={}, ema9={}, ema21={}, ema50={},"
                            + " ema100={}, ema200={})",
                    stock.getTicker(),
                    livePrice,
                    ema.ema9(),
                    ema.ema21(),
                    ema.ema50(),
                    ema.ema100(),
                    ema.ema200());
            return;
        }

        Optional<RsResult> rsOpt = relativeStrengthService.getCurrentRsResult(stock.getTicker());
        if (rsOpt.isEmpty() || rsOpt.get().rs() <= rsOpt.get().ema()) {
            log.info(
                    "Skipping {} — RS not positive (present={}, rs={}, ema={})",
                    stock.getTicker(),
                    rsOpt.isPresent(),
                    rsOpt.map(RsResult::rs).orElse(0.0),
                    rsOpt.map(RsResult::ema).orElse(0.0));
            return;
        }

        Optional<VfiAnalysis> vfiOpt =
                vfiService.analyze(stock.getTicker(), stock.getCompanyName());
        if (vfiOpt.isEmpty()) {
            log.warn("PullbackBuy skip: {} — VFI analysis returned empty", stock.getTicker());
            return;
        }
        if (!vfiOpt.get().isVfiPositive()) {
            log.info(
                    "Skipping {} — VFI not positive (vfi={}, signal={})",
                    stock.getTicker(),
                    vfiOpt.get().vfiValue(),
                    vfiOpt.get().signalLineValue());
            return;
        }

        String message =
                buildAlertMessage(stock, livePrice, apexPerformers.contains(stock.getTicker()));
        telegramClient.sendMessage(message);
        targetPriceProvider.addIgnoredSymbol(stock, IgnoreReason.PULLBACK_BUY_ALERT);
        log.info("Pullback buy alert sent for {}", stock.getTicker());
    }

    static boolean isPullbackPattern(double price, EmaAnalysis ema) {
        return price < ema.ema9()
                && price < ema.ema21()
                && price > ema.ema50()
                && price > ema.ema100()
                && price > ema.ema200();
    }

    static String buildAlertMessage(StockSymbol stock, double price, boolean isApexPerformer) {
        String base =
                String.format(
                        "Potential buy for *%s (%s)* at $%.2f\n"
                                + "_21 EMA pullback while volume and relative strength stay bullish_",
                        stock.getCompanyName(), stock.getTicker(), price);
        if (isApexPerformer) {
            base += "\n\n🏆 _Apex performer: outperforming top sector — strongest signal._";
        }
        return base;
    }
}
