package org.tradelite.quant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.common.SymbolRegistry;

/**
 * Tracks tail risk across sector ETFs and SPY to provide early warning of market instability.
 *
 * <p>Monitors kurtosis and skewness levels and sends alerts when tail risk becomes elevated:
 *
 * <ul>
 *   <li><b>Kurtosis</b>: High values indicate increased probability of extreme moves
 *   <li><b>Skewness</b>: Direction of risk - negative = crash bias, positive = rally potential
 * </ul>
 *
 * <p><b>Dual-window comparison:</b> tail risk is computed for two parallel lookback windows — a
 * short ~1-month window ({@link #SHORT}) and a long ~1-year window ({@link #LONG}). Alerts and the
 * daily report tag every block with {@code [35d]} / {@code [252d]} so the streams are
 * distinguishable. See issue #336.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TailRiskTracker {

    /**
     * Short window: 50-calendar-day fetch (oversized to guarantee row coverage even with holidays)
     * yielding a 25-trading-day kurtosis/skewness sample.
     */
    static final TailRiskWindow SHORT = new TailRiskWindow(50, 25);

    /**
     * Long window: 400-calendar-day fetch (matches the codebase convention used by
     * EmaService/VfiService/RelativeStrengthService) yielding a 252-trading-day sample.
     */
    static final TailRiskWindow LONG = new TailRiskWindow(400, 252);

    private static final String SHORT_TAG = "`[35d]`";
    private static final String LONG_TAG = "`[252d]`";
    private static final String SHORT_SECTION_TAG = "`[35d window]`";
    private static final String LONG_SECTION_TAG = "`[252d window]`";

    private final TailRiskService tailRiskService;
    private final TelegramGateway telegramClient;
    private final SymbolRegistry symbolRegistry;

    /** Analyzes tail risk for all tracked sector ETFs using the given lookback window. */
    public List<TailRiskAnalysis> analyzeAllSectors(TailRiskWindow window) {
        List<TailRiskAnalysis> results = new ArrayList<>();

        for (Map.Entry<String, String> entry : symbolRegistry.getAllEtfs().entrySet()) {
            String symbol = entry.getKey();
            String displayName = entry.getValue();

            Optional<TailRiskAnalysis> analysis =
                    tailRiskService.analyzeTailRisk(symbol, displayName, window);
            analysis.ifPresent(results::add);
        }

        return results;
    }

    /** Performs daily tail risk check and sends Telegram alerts for elevated risk. */
    public void trackAndAlert() {
        // Stable order: 35d first, 252d second.
        sendAlertIfElevated(analyzeAllSectors(SHORT), SHORT_TAG);
        sendAlertIfElevated(analyzeAllSectors(LONG), LONG_TAG);
    }

    private void sendAlertIfElevated(List<TailRiskAnalysis> analyses, String tag) {
        if (analyses.isEmpty()) {
            log.warn("No tail risk data available for {} window", tag);
            return;
        }

        List<TailRiskAnalysis> highRiskSectors =
                analyses.stream().filter(a -> a.riskLevel() == TailRiskLevel.HIGH).toList();
        List<TailRiskAnalysis> extremeRiskSectors =
                analyses.stream().filter(a -> a.riskLevel() == TailRiskLevel.EXTREME).toList();

        if (highRiskSectors.isEmpty() && extremeRiskSectors.isEmpty()) {
            log.info(
                    "Tail risk check complete for {} window: all sectors within normal range", tag);
            return;
        }

        String alertMessage = buildAlertMessage(analyses, highRiskSectors, extremeRiskSectors, tag);
        telegramClient.sendMessage(alertMessage);
        log.info(
                "Tail risk alert sent for {} window: {} HIGH, {} EXTREME sectors",
                tag,
                highRiskSectors.size(),
                extremeRiskSectors.size());
    }

    public void sendDailyReport() {
        List<TailRiskAnalysis> shortResults = analyzeAllSectors(SHORT);
        List<TailRiskAnalysis> longResults = analyzeAllSectors(LONG);

        logComparison(shortResults, longResults);

        String report = buildCombinedSummaryReport(shortResults, longResults);
        telegramClient.sendMessage(report);
        log.info("Daily tail risk report sent (dual-window)");
    }

    /**
     * Emits one {@code TAIL_RISK_COMPARE} INFO log line per symbol, side-by-side SHORT and LONG
     * window values, for offline grep-based analysis (issue #336 comparison period).
     *
     * <p><b>Formula boundary (#433):</b> log lines emitted before the #433 merge used the biased
     * method-of-moments formula ({@code m₄/m₂²}, {@code m₃/m₂^(3/2)}). Lines from #433 onward use
     * Fisher-Pearson G2 (kurtosis) and G1 (skewness) via Apache Commons Statistics. <b>The two
     * regimes are not directly comparable.</b> At the SHORT window (n=25), new kurtosis values are
     * ~1.08× larger and new skewness magnitudes ~1.15× larger than equivalent historical values. At
     * the LONG window (n=252), differences are &lt;1%. When grepping {@code TAIL_RISK_COMPARE}
     * lines for the comparison-period analysis, filter by the #433 merge date.
     */
    private void logComparison(
            List<TailRiskAnalysis> shortResults, List<TailRiskAnalysis> longResults) {
        Map<String, TailRiskAnalysis> shortBySymbol = indexBySymbol(shortResults);
        Map<String, TailRiskAnalysis> longBySymbol = indexBySymbol(longResults);

        java.util.Set<String> symbols = new java.util.LinkedHashSet<>();
        symbols.addAll(shortBySymbol.keySet());
        symbols.addAll(longBySymbol.keySet());

        for (String symbol : symbols) {
            TailRiskAnalysis s = shortBySymbol.get(symbol);
            TailRiskAnalysis l = longBySymbol.get(symbol);

            String agreement;
            if (s == null || l == null) {
                agreement = "N/A";
            } else {
                agreement = String.valueOf(s.riskLevel() == l.riskLevel());
            }

            log.info(
                    "TAIL_RISK_COMPARE symbol={} k35={} k252={} excess35={} excess252={}"
                            + " skew35={} skew252={} lvl35={} lvl252={} agreement={}",
                    symbol,
                    fmt(s == null ? null : s.kurtosis()),
                    fmt(l == null ? null : l.kurtosis()),
                    fmt(s == null ? null : s.excessKurtosis()),
                    fmt(l == null ? null : l.excessKurtosis()),
                    fmt(s == null ? null : s.skewness()),
                    fmt(l == null ? null : l.skewness()),
                    s == null ? "N/A" : s.riskLevel(),
                    l == null ? "N/A" : l.riskLevel(),
                    agreement);
        }
    }

    private static Map<String, TailRiskAnalysis> indexBySymbol(List<TailRiskAnalysis> analyses) {
        Map<String, TailRiskAnalysis> map = new HashMap<>();
        for (TailRiskAnalysis a : analyses) {
            map.put(a.symbol(), a);
        }
        return map;
    }

    private static String fmt(Double v) {
        return v == null ? "N/A" : String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    private String buildAlertMessage(
            List<TailRiskAnalysis> allAnalyses,
            List<TailRiskAnalysis> highRisk,
            List<TailRiskAnalysis> extremeRisk,
            String tag) {

        StringBuilder sb = new StringBuilder();

        // Header with severity and window tag
        if (!extremeRisk.isEmpty()) {
            sb.append("🔴 *Tail Risk Alert - Extreme* ").append(tag).append("\n\n");
        } else {
            sb.append("🟠 *Tail Risk Alert - High* ").append(tag).append("\n\n");
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

    /**
     * Builds a combined daily report containing both window sections, each independently
     * summarized.
     */
    String buildCombinedSummaryReport(
            List<TailRiskAnalysis> shortResults, List<TailRiskAnalysis> longResults) {
        StringBuilder sb = new StringBuilder();
        sb.append("*Tail Risk Report*\n\n");
        appendReportSection(sb, SHORT_SECTION_TAG, shortResults);
        sb.append("\n\n");
        appendReportSection(sb, LONG_SECTION_TAG, longResults);
        return sb.toString();
    }

    private void appendReportSection(
            StringBuilder sb, String sectionTag, List<TailRiskAnalysis> analyses) {
        sb.append(sectionTag).append("\n");
        if (analyses.isEmpty()) {
            sb.append("_Insufficient data for analysis._");
            return;
        }

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
    }
}
