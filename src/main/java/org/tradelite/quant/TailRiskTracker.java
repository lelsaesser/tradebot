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
 * Tracks tail risk across sector ETFs and SPY to provide early warning of market instability.
 *
 * <p>Monitors kurtosis and skewness levels and sends alerts when tail risk becomes elevated:
 *
 * <ul>
 *   <li><b>Kurtosis</b>: High values indicate increased probability of extreme moves
 *   <li><b>Skewness</b>: Direction of risk - negative = crash bias, positive = rally potential
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TailRiskTracker {

    private final TailRiskService tailRiskService;
    private final TelegramClient telegramClient;

    /** Analyzes tail risk for all tracked sector ETFs and returns the results. */
    public List<TailRiskAnalysis> analyzeAllSectors() {
        List<TailRiskAnalysis> results = new ArrayList<>();

        for (Map.Entry<String, String> entry :
                SectorEtfRegistry.allEtfsWithBenchmark().entrySet()) {
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

    public void sendDailyReport() {
        String report = buildSummaryReport();
        telegramClient.sendMessage(report);
        log.info("Daily tail risk report sent");
    }

    private String buildAlertMessage(
            List<TailRiskAnalysis> allAnalyses,
            List<TailRiskAnalysis> highRisk,
            List<TailRiskAnalysis> extremeRisk) {

        StringBuilder sb = new StringBuilder();

        // Header with severity
        if (!extremeRisk.isEmpty()) {
            sb.append("🔴 *Tail Risk Alert - Extreme*\n\n");
        } else {
            sb.append("🟠 *Tail Risk Alert - High*\n\n");
        }

        // List extreme risk sectors first with skewness context
        if (!extremeRisk.isEmpty()) {
            sb.append("*Extreme* risk sectors:\n");
            for (TailRiskAnalysis analysis : extremeRisk) {
                sb.append(analysis.toSummaryLine()).append("\n");
                sb.append("   _").append(analysis.getRiskInterpretation()).append("_\n");
            }
            sb.append("\n");
        }

        // List high risk sectors with skewness context
        if (!highRisk.isEmpty()) {
            sb.append("*High* risk sectors:\n");
            for (TailRiskAnalysis analysis : highRisk) {
                sb.append(analysis.toSummaryLine()).append("\n");
                sb.append("   _").append(analysis.getRiskInterpretation()).append("_\n");
            }
            sb.append("\n");
        }

        // Summary of all sectors
        sb.append("All sectors: ");
        for (TailRiskAnalysis analysis : allAnalyses) {
            sb.append(analysis.toCompactLine()).append(" ");
        }
        sb.append("\n\n");

        // Directional summary
        long crashRiskCount = allAnalyses.stream().filter(TailRiskAnalysis::hasCrashRisk).count();
        long rallyPotentialCount =
                allAnalyses.stream().filter(TailRiskAnalysis::hasRallyPotential).count();

        if (crashRiskCount > 0 || rallyPotentialCount > 0) {
            sb.append("📊 *Directional Bias:*\n");
            if (crashRiskCount > 0) {
                sb.append("• ⬇️ ")
                        .append(crashRiskCount)
                        .append(" sector(s) with crash risk bias\n");
            }
            if (rallyPotentialCount > 0) {
                sb.append("• ⬆️ ")
                        .append(rallyPotentialCount)
                        .append(" sector(s) with rally potential\n");
            }
            sb.append("\n");
        }

        // Interpretation guidance
        sb.append("_Kurtosis = probability of extreme moves_\n");
        sb.append("_Skewness = likely direction (⬇️ crash / ⬆️ rally)_");

        return sb.toString();
    }

    /** Builds a summary report of all sector tail risk levels for display. */
    public String buildSummaryReport() {
        List<TailRiskAnalysis> analyses = analyzeAllSectors();

        if (analyses.isEmpty()) {
            return "📊 *Tail Risk Report*\n\n_Insufficient data for analysis._";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📊 *Tail Risk Report*\n\n");

        for (TailRiskAnalysis analysis : analyses) {
            sb.append(analysis.toSummaryLine()).append("\n");
        }

        long elevatedCount = analyses.stream().filter(a -> a.riskLevel().isElevated()).count();

        long crashRiskCount = analyses.stream().filter(TailRiskAnalysis::hasCrashRisk).count();
        long rallyPotentialCount =
                analyses.stream().filter(TailRiskAnalysis::hasRallyPotential).count();

        sb.append("\n");
        if (elevatedCount == 0) {
            sb.append("✅ All sectors within normal tail risk range.");
        } else {
            sb.append(
                    String.format("⚠️ %d sector(s) showing elevated tail risk.%n", elevatedCount));
            if (crashRiskCount > 0) {
                sb.append(String.format("   ⬇️ %d with crash risk bias%n", crashRiskCount));
            }
            if (rallyPotentialCount > 0) {
                sb.append(String.format("   ⬆️ %d with rally potential", rallyPotentialCount));
            }
        }

        return sb.toString();
    }
}
