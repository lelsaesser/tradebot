package org.tradelite.quant;

import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.common.FeatureToggle;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.SymbolRegistry;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.core.FinnhubPriceEvaluator;
import org.tradelite.core.IgnoreReason;
import org.tradelite.service.FeatureToggleService;
import org.tradelite.service.RelativeStrengthService;
import org.tradelite.service.RelativeStrengthService.RsResult;

/**
 * Detects EMA pullback buy opportunities for tracked stocks.
 *
 * <p>A "healthy pullback" is when price drops below the short-term EMAs (9 and 21) but stays above
 * the longer-term EMAs (50, 100, 200), while both RS vs SPY and VFI confirm the stock is still
 * fundamentally strong. Sends one Telegram alert per qualifying stock.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PullbackBuyTracker {

    private final EmaService emaService;
    private final RelativeStrengthService relativeStrengthService;
    private final VfiService vfiService;
    private final FinnhubPriceEvaluator finnhubPriceEvaluator;
    private final TelegramGateway telegramClient;
    private final SymbolRegistry symbolRegistry;
    private final TargetPriceProvider targetPriceProvider;
    private final FeatureToggleService featureToggleService;

    public void analyzeAndSendAlerts() {
        if (!featureToggleService.isEnabled(FeatureToggle.PULLBACK_BUY_ALERT)) {
            return;
        }

        Map<String, Double> priceCache = finnhubPriceEvaluator.getLastPriceCache();

        for (StockSymbol stock : symbolRegistry.getStocks()) {
            if (targetPriceProvider.isSymbolIgnored(stock, IgnoreReason.PULLBACK_BUY_ALERT)) {
                continue;
            }

            Optional<EmaAnalysis> emaOpt =
                    emaService.analyze(stock.getTicker(), stock.getCompanyName());
            if (emaOpt.isEmpty()) {
                continue;
            }

            EmaAnalysis ema = emaOpt.get();
            if (Double.isNaN(ema.ema50())
                    || Double.isNaN(ema.ema100())
                    || Double.isNaN(ema.ema200())) {
                continue;
            }

            Double livePrice = priceCache.get(stock.getTicker());
            if (livePrice == null) {
                continue;
            }

            if (!isPullbackPattern(livePrice, ema)) {
                continue;
            }

            Optional<RsResult> rsOpt =
                    relativeStrengthService.getCurrentRsResult(stock.getTicker());
            if (rsOpt.isEmpty() || rsOpt.get().rs() <= rsOpt.get().ema()) {
                continue;
            }

            Optional<VfiAnalysis> vfiOpt =
                    vfiService.analyze(stock.getTicker(), stock.getCompanyName());
            if (vfiOpt.isEmpty() || !vfiOpt.get().isVfiPositive()) {
                continue;
            }

            String message = buildAlertMessage(stock, livePrice);
            telegramClient.sendMessage(message);
            targetPriceProvider.addIgnoredSymbol(stock, IgnoreReason.PULLBACK_BUY_ALERT);
            log.info("Pullback buy alert sent for {}", stock.getTicker());
        }
    }

    static boolean isPullbackPattern(double price, EmaAnalysis ema) {
        return price < ema.ema9()
                && price < ema.ema21()
                && price > ema.ema50()
                && price > ema.ema100()
                && price > ema.ema200();
    }

    static String buildAlertMessage(StockSymbol stock, double price) {
        return String.format(
                "Potential buy for %s (%s) at $%.2f."
                        + " 21 EMA pullback while volume and relative strength stay bullish",
                stock.getCompanyName(), stock.getTicker(), price);
    }
}
