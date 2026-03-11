package org.tradelite.quant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tradelite.client.telegram.TelegramClient;

/**
 * Tracks tail risk across sector ETFs and SPY to provide early warning of market instability.
 *
 * <p>Monitors kurtosis levels and sends alerts when tail risk becomes elevated. High kurtosis
 * indicates increased probability of extreme price moves (crashes or rallies).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TailRiskTracker {

    /** Sector ETFs to track with display names. */
    private static final Map<String, String> SECTOR_ETFS =
            Map.ofEntries(
                    Map.entry("SPY", "S&P 500"),
                    Map.entry("XLE", "Energy"),
                    Map.entry("XLK", "Technology"),
                    Map.entry("XLF", "Financials"),
                    Map.entry("XLV", "Healthcare"),
                    Map.entry("XLI", "Industrials"),
                    Map.entry("XLP", "Consumer Staples"),
                    Map.entry("XLY", "Consumer Discretionary"),
                    Map.entry("XLB", "Materials"),
                    Map.entry("XLU", "Utilities"),
                    Map.entry("XLRE", "Real Estate"),
                    Map.entry("XLC", "Communication Services"));

    private final TailRiskService tailRiskService;
    private final TelegramClient telegramClient;

    /** Analyzes tail risk for all tracked sector ETFs and returns the results. */
    public List<TailRiskAnalysis> analyzeAllSectors() {
        List<TailRiskAnalysis> results = new ArrayList<>();

        for (Map.Entry<String, String> entry : SECTOR_ETFS.entrySet()) {
            String symbol = entry.getKey();
            String displayName = entry.getValue();

            Optional<TailRiskAnalysis> analysis =
                    tailRiskService.analyzeTailRisk(symbol, displayName);
            analysis.ifPresent(results::add);
        }

        return results;
    }

    /** Performs daily tail risk check and sends Telegram alerts for elevated risk. */
    public void trackAndAlert() {
        List<TailRiskAnalysis> analyses = analyzeAllSectors();

        if (analyses.isEmpty()) {
            log.warn("No tail risk data available - insufficient price history");
            return;
        }

        List<TailRiskAnalysis> highRiskSectors =
                analyses.stream().filter(a -> a.riskLevel() == TailRiskLevel.HIGH).toList();

        List<TailRiskAnalysis> extremeRiskSectors =
                analyses.stream().filter(a -> a.riskLevel() == TailRiskLevel.EXTREME).toList();

        // Only send alerts if there's elevated risk
        if (!highRiskSectors.isEmpty() || !extremeRiskSectors.isEmpty()) {
            String alertMessage = buildAlertMessage(analyses, highRiskSectors, extremeRiskSectors);
            telegramClient.sendMessage(alertMessage);
            log.info(
                    "Tail risk alert sent: {} HIGH, {} EXTREME sectors",
                    highRiskSectors.size(),
                    extremeRiskSectors.size());
        } else {
            log.info("Tail risk check complete: all sectors within normal range");
        }
    }

    private String buildAlertMessage(
            List<TailRiskAnalysis> allAnalyses,
            List<TailRiskAnalysis> highRisk,
            List<TailRiskAnalysis> extremeRisk) {

        StringBuilder sb = new StringBuilder();

        // Header with severity
        if (!extremeRisk.isEmpty()) {
            sb.append("🔴 TAIL RISK ALERT - EXTREME\n\n");
        } else {
            sb.append("🟠 TAIL RISK ALERT - HIGH\n\n");
        }

        // List extreme risk sectors first
        if (!extremeRisk.isEmpty()) {
            sb.append("EXTREME risk sectors:\n");
            for (TailRiskAnalysis analysis : extremeRisk) {
                sb.append("• ").append(analysis.toSummaryLine()).append("\n");
            }
            sb.append("\n");
        }

        // List high risk sectors
        if (!highRisk.isEmpty()) {
            sb.append("HIGH risk sectors:\n");
            for (TailRiskAnalysis analysis : highRisk) {
                sb.append("• ").append(analysis.toSummaryLine()).append("\n");
            }
            sb.append("\n");
        }

        // Summary of all sectors
        sb.append("All sectors: ");
        for (TailRiskAnalysis analysis : allAnalyses) {
            sb.append(analysis.toCompactLine()).append(" ");
        }
        sb.append("\n\n");

        // Interpretation guidance
        sb.append("⚠️ Big moves more likely than normal.\n");
        sb.append("Review macro conditions to assess direction.");

        return sb.toString();
    }

    /** Builds a summary report of all sector tail risk levels for display. */
    public String buildSummaryReport() {
        List<TailRiskAnalysis> analyses = analyzeAllSectors();

        if (analyses.isEmpty()) {
            return "📊 Tail Risk Report\n\nInsufficient data for analysis.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📊 Tail Risk Report\n\n");

        for (TailRiskAnalysis analysis : analyses) {
            sb.append(analysis.toSummaryLine()).append("\n");
        }

        long elevatedCount =
                analyses.stream()
                        .filter(
                                a ->
                                        a.riskLevel() == TailRiskLevel.HIGH
                                                || a.riskLevel() == TailRiskLevel.EXTREME)
                        .count();

        sb.append("\n");
        if (elevatedCount == 0) {
            sb.append("✅ All sectors within normal tail risk range.");
        } else {
            sb.append(String.format("⚠️ %d sector(s) showing elevated tail risk.", elevatedCount));
        }

        return sb.toString();
    }
}
