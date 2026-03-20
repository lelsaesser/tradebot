package org.tradelite.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.common.SectorEtfRegistry;
import org.tradelite.service.MomentumRocService;

/**
 * Tracks sector ETF momentum using Rate of Change (ROC) and sends alerts on crossovers.
 *
 * <p>This component monitors all 11 SPDR sector ETFs in real-time during market hours, calculating
 * 10-day and 20-day ROC. When a sector's ROC crosses the zero line (from negative to positive or
 * vice versa), a Telegram alert is sent indicating momentum direction change.
 *
 * <p>The analysis runs alongside the regular stock market monitoring cycle (every 5 minutes) to
 * detect crossovers as soon as they occur during trading hours.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SectorMomentumRocTracker {

    private final MomentumRocService momentumRocService;
    private final TelegramClient telegramClient;

    /**
     * Analyzes all sector ETFs for momentum crossovers and sends alerts.
     *
     * <p>This method is called during market hours as part of the stock monitoring cycle. It
     * calculates ROC for each sector ETF and sends a Telegram alert if any sector's momentum has
     * crossed the zero line.
     */
    public void analyzeAndSendAlerts() {
        log.info("Analyzing sector momentum ROC for crossovers");

        List<MomentumRocSignal> signals = new ArrayList<>();

        for (Map.Entry<String, String> entry : SectorEtfRegistry.allEtfs().entrySet()) {
            String symbol = entry.getKey();
            String displayName = entry.getValue();

            try {
                Optional<MomentumRocSignal> signal =
                        momentumRocService.detectMomentumShift(symbol, displayName);
                signal.ifPresent(signals::add);
            } catch (Exception e) {
                log.error("Error analyzing ROC for {}: {}", symbol, e.getMessage());
            }
        }

        if (!signals.isEmpty()) {
            String message = formatAlertMessage(signals);
            telegramClient.sendMessage(message);
            log.info("Sent sector momentum ROC alert with {} signals", signals.size());
        } else {
            log.info("No sector momentum crossovers detected");
        }
    }

    /**
     * Formats the alert message for Telegram.
     *
     * @param signals List of momentum signals to include in the message
     * @return Formatted Markdown message
     */
    protected String formatAlertMessage(List<MomentumRocSignal> signals) {
        StringBuilder sb = new StringBuilder();
        sb.append("⚡ *SECTOR MOMENTUM ROC ALERT*\n\n");

        List<MomentumRocSignal> turningPositive =
                signals.stream()
                        .filter(
                                s ->
                                        s.signalType()
                                                == MomentumRocSignal.SignalType
                                                        .MOMENTUM_TURNING_POSITIVE)
                        .toList();

        List<MomentumRocSignal> turningNegative =
                signals.stream()
                        .filter(
                                s ->
                                        s.signalType()
                                                == MomentumRocSignal.SignalType
                                                        .MOMENTUM_TURNING_NEGATIVE)
                        .toList();

        if (!turningPositive.isEmpty()) {
            sb.append("📈 *MOMENTUM TURNING POSITIVE:*\n");
            for (MomentumRocSignal signal : turningPositive) {
                sb.append(formatSignalLine(signal));
            }
            sb.append("\n");
        }

        if (!turningNegative.isEmpty()) {
            sb.append("📉 *MOMENTUM TURNING NEGATIVE:*\n");
            for (MomentumRocSignal signal : turningNegative) {
                sb.append(formatSignalLine(signal));
            }
            sb.append("\n");
        }

        sb.append("_ROC₁₀ = 10-day momentum | ROC₂₀ = 20-day momentum_");

        return sb.toString();
    }

    /**
     * Formats a single signal line.
     *
     * @param signal The momentum signal
     * @return Formatted line
     */
    private String formatSignalLine(MomentumRocSignal signal) {
        return String.format(
                "• *%s* (%s): ROC₁₀ %+.1f%% | ROC₂₀ %+.1f%%%n",
                signal.displayName(), signal.symbol(), signal.roc10(), signal.roc20());
    }
}
