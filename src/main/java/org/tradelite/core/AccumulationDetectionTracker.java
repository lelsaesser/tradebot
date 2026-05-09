package org.tradelite.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.common.FeatureToggle;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.SymbolRegistry;
import org.tradelite.quant.EmaAnalysis;
import org.tradelite.quant.EmaService;
import org.tradelite.quant.TrendDirection;
import org.tradelite.quant.VfiAnalysis;
import org.tradelite.quant.VfiService;
import org.tradelite.service.FeatureToggleService;
import org.tradelite.service.RelativeStrengthService;
import org.tradelite.service.RsTrendResult;

/**
 * Orchestrates accumulation detection across all tracked stocks and sends consolidated Telegram
 * alerts when institutional accumulation is identified.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccumulationDetectionTracker {

    private final EmaService emaService;
    private final VfiService vfiService;
    private final RelativeStrengthService relativeStrengthService;
    private final AccumulationDetectionService accumulationDetectionService;
    private final TelegramGateway telegramClient;
    private final SymbolRegistry symbolRegistry;
    private final FeatureToggleService featureToggleService;

    public void analyzeAndSendAlerts() {
        if (!featureToggleService.isEnabled(FeatureToggle.ACCUMULATION_DETECTION)) {
            return;
        }

        List<AccumulationSignal> signals = new ArrayList<>();

        for (StockSymbol stock : symbolRegistry.getStocks()) {
            if (RelativeStrengthService.BENCHMARK_SYMBOL.equals(stock.getTicker())) {
                continue;
            }

            Optional<EmaAnalysis> emaOpt =
                    emaService.analyze(stock.getTicker(), stock.getCompanyName());
            if (emaOpt.isEmpty()) {
                log.warn("Accumulation skip: {} — EMA analysis returned empty", stock.getTicker());
                continue;
            }

            Optional<VfiAnalysis> vfiOpt =
                    vfiService.analyze(stock.getTicker(), stock.getCompanyName());
            if (vfiOpt.isEmpty()) {
                log.warn("Accumulation skip: {} — VFI analysis returned empty", stock.getTicker());
                continue;
            }

            // RS is informational — null-safe if unavailable
            RsTrendResult rsTrend =
                    relativeStrengthService.getRsTrend(stock.getTicker()).orElse(null);

            Optional<AccumulationSignal> signal =
                    accumulationDetectionService.evaluate(
                            stock.getTicker(),
                            stock.getCompanyName(),
                            emaOpt.get(),
                            vfiOpt.get(),
                            rsTrend);

            signal.ifPresent(signals::add);
        }

        if (!signals.isEmpty()) {
            String message = buildAlertMessage(signals);
            telegramClient.sendMessage(message);
            log.info("Accumulation detection alert sent for {} stock(s)", signals.size());
        }
    }

    String buildAlertMessage(List<AccumulationSignal> signals) {
        StringBuilder sb = new StringBuilder();
        sb.append("*Institutional accumulation detected*\n");

        for (AccumulationSignal signal : signals) {
            sb.append("\n*")
                    .append(signal.displayName())
                    .append(" (")
                    .append(signal.symbol())
                    .append(")*\n");
            sb.append(
                    String.format(
                            "  Price: $%.2f | EMA9: $%.2f < EMA21: $%.2f%n",
                            signal.currentPrice(), signal.ema9(), signal.ema21()));
            sb.append(
                    String.format(
                            "  VFI: %+.2f | Signal: %+.2f%n",
                            signal.vfiValue(), signal.vfiSignalLine()));
            sb.append(
                    String.format(
                            "  RS vs SPY: %.4f %s | EMA: %.4f %s%n",
                            signal.rsValue(),
                            trendArrow(signal.rsTrend()),
                            signal.rsEma(),
                            trendArrow(signal.rsEmaTrend())));
        }

        sb.append("\n_Based on EMA crossdown + positive rising VFI_");
        return sb.toString();
    }

    static String trendArrow(TrendDirection direction) {
        return switch (direction) {
            case RISING -> "↑";
            case FLAT -> "→";
            case FALLING -> "↓";
        };
    }
}
