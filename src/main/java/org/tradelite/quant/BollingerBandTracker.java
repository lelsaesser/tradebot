package org.tradelite.quant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.common.SectorEtfRegistry;

/**
 * Tracks Bollinger Band signals across sector ETFs and SPY to detect volatility extremes.
 *
 * <p>Monitors three signal types and sends Telegram alerts when actionable conditions arise:
 *
 * <ul>
 *   <li><b>Upper Band Touch</b>: Price above upper band — sector may be overextended
 *   <li><b>Lower Band Touch</b>: Price below lower band — sector may be underextended
 *   <li><b>Squeeze</b>: Bandwidth at historically low levels — breakout expected
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BollingerBandTracker {

    private final BollingerBandService bollingerBandService;
    private final TelegramClient telegramClient;

    /** Analyzes Bollinger Bands for all tracked sector ETFs and returns the results. */
    public List<BollingerBandAnalysis> analyzeAllSectors() {
        List<BollingerBandAnalysis> results = new ArrayList<>();

        for (Map.Entry<String, String> entry :
                SectorEtfRegistry.allEtfsWithBenchmark().entrySet()) {
            String symbol = entry.getKey();
            String displayName = entry.getValue();

            Optional<BollingerBandAnalysis> analysis =
                    bollingerBandService.analyze(symbol, displayName);
            analysis.ifPresent(results::add);
        }

        return results;
    }

    /** Performs daily Bollinger Band check and sends Telegram alerts for actionable signals. */
    public void trackAndAlert() {
        List<BollingerBandAnalysis> analyses = analyzeAllSectors();

        if (analyses.isEmpty()) {
            log.warn("No Bollinger Band data available — insufficient price history");
            return;
        }

        List<BollingerBandAnalysis> withSignals =
                analyses.stream().filter(BollingerBandAnalysis::hasSignals).toList();

        if (!withSignals.isEmpty()) {
            String alertMessage = buildAlertMessage(analyses, withSignals);
            telegramClient.sendMessage(alertMessage);
            log.info(
                    "Bollinger Band alert sent: {} sector(s) with signals out of {} analyzed",
                    withSignals.size(),
                    analyses.size());
        } else {
            log.info("Bollinger Band check complete: all sectors within normal range");
        }
    }

    /** Sends a full daily report of Bollinger Band state for all sectors. */
    public void sendDailyReport() {
        String report = buildSummaryReport();
        telegramClient.sendMessage(report);
        log.info("Daily Bollinger Band report sent");
    }

    private String buildAlertMessage(
            List<BollingerBandAnalysis> allAnalyses, List<BollingerBandAnalysis> withSignals) {

        StringBuilder sb = new StringBuilder();

        // Header with severity indicator
        boolean hasSqueeze = withSignals.stream().anyMatch(BollingerBandAnalysis::isSqueeze);
        boolean hasOverextended =
                withSignals.stream().anyMatch(BollingerBandAnalysis::isOverextended);
        boolean hasUnderextended =
                withSignals.stream().anyMatch(BollingerBandAnalysis::isUnderextended);

        if (hasSqueeze) {
            sb.append("🔵 *Bollinger Band Alert — Squeeze Detected*\n\n");
        } else if (hasOverextended || hasUnderextended) {
            sb.append("🟡 *Bollinger Band Alert — Band Touch*\n\n");
        } else {
            sb.append("📊 *Bollinger Band Alert*\n\n");
        }

        // Group by signal type
        List<BollingerBandAnalysis> squeezes =
                withSignals.stream().filter(BollingerBandAnalysis::isSqueeze).toList();

        List<BollingerBandAnalysis> overextended =
                withSignals.stream().filter(BollingerBandAnalysis::isOverextended).toList();

        List<BollingerBandAnalysis> underextended =
                withSignals.stream().filter(BollingerBandAnalysis::isUnderextended).toList();

        if (!squeezes.isEmpty()) {
            sb.append("*Volatility Squeeze* (breakout expected):\n");
            for (BollingerBandAnalysis analysis : squeezes) {
                sb.append(analysis.toSummaryLine()).append("\n");
                sb.append("   _").append(analysis.getInterpretation()).append("_\n");
            }
            sb.append("\n");
        }

        if (!overextended.isEmpty()) {
            sb.append("*Upper Band Touch* (overextended):\n");
            for (BollingerBandAnalysis analysis : overextended) {
                sb.append(analysis.toSummaryLine()).append("\n");
                sb.append("   _").append(analysis.getInterpretation()).append("_\n");
            }
            sb.append("\n");
        }

        if (!underextended.isEmpty()) {
            sb.append("*Lower Band Touch* (underextended):\n");
            for (BollingerBandAnalysis analysis : underextended) {
                sb.append(analysis.toSummaryLine()).append("\n");
                sb.append("   _").append(analysis.getInterpretation()).append("_\n");
            }
            sb.append("\n");
        }

        // Compact summary of all sectors
        sb.append("All sectors: ");
        for (BollingerBandAnalysis analysis : allAnalyses) {
            sb.append(analysis.toCompactLine()).append(" ");
        }
        sb.append("\n\n");

        sb.append("_%B = position within bands (0=lower, 1=upper)_\n");
        sb.append("_BW = bandwidth; P = bandwidth percentile_");

        return sb.toString();
    }

    /** Builds a summary report of all sector Bollinger Band states for display. */
    public String buildSummaryReport() {
        List<BollingerBandAnalysis> analyses = analyzeAllSectors();

        if (analyses.isEmpty()) {
            return "📊 *Bollinger Band Report*\n\n_Insufficient data for analysis._";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📊 *Bollinger Band Report*\n\n");

        for (BollingerBandAnalysis analysis : analyses) {
            sb.append(analysis.toSummaryLine()).append("\n");
        }

        long signalCount = analyses.stream().filter(BollingerBandAnalysis::hasSignals).count();
        long squeezeCount = analyses.stream().filter(BollingerBandAnalysis::isSqueeze).count();
        long overextendedCount =
                analyses.stream().filter(BollingerBandAnalysis::isOverextended).count();
        long underextendedCount =
                analyses.stream().filter(BollingerBandAnalysis::isUnderextended).count();

        sb.append("\n");
        if (signalCount == 0) {
            sb.append("✅ All sectors trading within normal Bollinger Band range.");
        } else {
            sb.append(String.format("⚠️ %d sector(s) with active signals.%n", signalCount));
            if (squeezeCount > 0) {
                sb.append(String.format("   🔵 %d squeeze(s) — breakout expected%n", squeezeCount));
            }
            if (overextendedCount > 0) {
                sb.append(
                        String.format(
                                "   ⬆️ %d overextended (above upper band)%n", overextendedCount));
            }
            if (underextendedCount > 0) {
                sb.append(
                        String.format(
                                "   ⬇️ %d underextended (below lower band)", underextendedCount));
            }
        }

        return sb.toString();
    }
}
