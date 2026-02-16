package org.tradelite.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.service.RelativeStrengthService;
import org.tradelite.service.StockSymbolRegistry;

/**
 * Orchestrates relative strength calculation and alert generation for all tracked stocks.
 *
 * <p>This component: 1. Iterates through all stocks with target prices 2. Calculates RS vs SPY
 * benchmark using RelativeStrengthService 3. Sends Telegram alerts when crossovers are detected 4.
 * Persists RS data for next calculation
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RelativeStrengthTracker {

    private final RelativeStrengthService relativeStrengthService;
    private final TargetPriceProvider targetPriceProvider;
    private final StockSymbolRegistry stockSymbolRegistry;
    private final TelegramClient telegramClient;

    /**
     * Calculates relative strength for all tracked stocks and sends alerts for crossovers.
     *
     * <p>Should be called daily after market close (after RSI price data is collected).
     */
    public void analyzeAndSendAlerts() throws IOException {
        log.info("Starting relative strength analysis for all tracked stocks");

        List<RelativeStrengthSignal> outperformingSignals = new ArrayList<>();
        List<RelativeStrengthSignal> underperformingSignals = new ArrayList<>();

        // Process all stocks with target prices
        for (TargetPrice targetPrice : targetPriceProvider.getStockTargetPrices()) {
            String symbol = targetPrice.getSymbol();

            // Skip SPY benchmark itself
            if (RelativeStrengthService.BENCHMARK_SYMBOL.equals(symbol)) {
                continue;
            }

            Optional<StockSymbol> stockSymbol = stockSymbolRegistry.fromString(symbol);
            String displayName = stockSymbol.map(StockSymbol::getDisplayName).orElse(symbol);

            try {
                Optional<RelativeStrengthSignal> signal =
                        relativeStrengthService.calculateRelativeStrength(symbol, displayName);

                if (signal.isPresent()) {
                    if (signal.get().signalType()
                            == RelativeStrengthSignal.SignalType.OUTPERFORMING) {
                        outperformingSignals.add(signal.get());
                    } else {
                        underperformingSignals.add(signal.get());
                    }
                }
            } catch (Exception e) {
                log.error("Error calculating RS for {}: {}", symbol, e.getMessage());
            }
        }

        // Send consolidated alert if any crossovers detected
        if (!outperformingSignals.isEmpty() || !underperformingSignals.isEmpty()) {
            String alertMessage = formatAlertMessage(outperformingSignals, underperformingSignals);
            telegramClient.sendMessage(alertMessage);
            log.info(
                    "Sent RS alert: {} outperforming, {} underperforming",
                    outperformingSignals.size(),
                    underperformingSignals.size());
        } else {
            log.info("No RS crossovers detected");
        }

        // Persist RS data
        relativeStrengthService.saveRsHistory();
        log.info("Relative strength analysis completed");
    }

    /**
     * Formats the alert message for Telegram.
     *
     * @param outperforming List of stocks that crossed above their RS EMA
     * @param underperforming List of stocks that crossed below their RS EMA
     * @return Formatted Markdown message
     */
    protected String formatAlertMessage(
            List<RelativeStrengthSignal> outperforming,
            List<RelativeStrengthSignal> underperforming) {

        StringBuilder sb = new StringBuilder();
        sb.append("📈 *RELATIVE STRENGTH ALERT*\n\n");

        if (!outperforming.isEmpty()) {
            sb.append("*🟢 OUTPERFORMING SPY:*\n");
            for (RelativeStrengthSignal signal : outperforming) {
                sb.append(formatSignalLine(signal));
            }
            sb.append("\n");
        }

        if (!underperforming.isEmpty()) {
            sb.append("*🔴 UNDERPERFORMING SPY:*\n");
            for (RelativeStrengthSignal signal : underperforming) {
                sb.append(formatSignalLine(signal));
            }
            sb.append("\n");
        }

        sb.append("_Based on 50-period EMA crossover_");

        return sb.toString();
    }

    /**
     * Formats a single signal line for the alert message.
     *
     * @param signal The signal to format
     * @return Formatted line
     */
    private String formatSignalLine(RelativeStrengthSignal signal) {
        return String.format(
                "• *%s* (%s)%n  RS: %.4f | EMA: %.4f (%+.1f%%)%n",
                signal.symbol(),
                signal.displayName(),
                signal.rsValue(),
                signal.emaValue(),
                signal.percentageDiff());
    }
}
