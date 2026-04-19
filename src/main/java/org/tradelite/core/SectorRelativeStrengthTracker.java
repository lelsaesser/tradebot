package org.tradelite.core;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.common.SymbolRegistry;
import org.tradelite.service.RelativeStrengthService;
import org.tradelite.service.RelativeStrengthService.RsResult;

/**
 * Tracks and reports relative strength of sector ETFs vs SPY benchmark.
 *
 * <p>This component provides two capabilities:
 *
 * <ol>
 *   <li>Real-time RS crossover detection during market hours (every 5 minutes)
 *   <li>Daily summary showing all 11 SPDR sector ETFs ranked by RS with streak counts
 * </ol>
 *
 * <p>The RS value is the ratio of sector price to SPY price, and the percentage shown is the
 * deviation from the 50-period EMA of that ratio.
 *
 * <p>Streak tracking shows consecutive days each sector has been outperforming or underperforming
 * SPY. This helps identify sustained trends vs short-term fluctuations.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SectorRelativeStrengthTracker {

    private final RelativeStrengthService relativeStrengthService;
    private final TelegramGateway telegramClient;
    private final SectorRsStreakPersistence streakPersistence;
    private final SymbolRegistry symbolRegistry;

    /**
     * Analyzes sector ETFs for RS crossovers and sends alerts.
     *
     * <p>This method is called during market hours as part of the stock monitoring cycle. It
     * detects when a sector ETF's RS crosses above or below its 50-period EMA.
     *
     * @throws IOException if RS data persistence fails
     */
    public void analyzeAndSendAlerts() throws IOException {
        log.info("Analyzing sector ETF relative strength for crossovers");

        List<RelativeStrengthSignal> outperformingSignals = new ArrayList<>();
        List<RelativeStrengthSignal> underperformingSignals = new ArrayList<>();

        for (Map.Entry<String, String> entry : symbolRegistry.getAllEtfs().entrySet()) {
            String symbol = entry.getKey();
            String displayName = entry.getValue();

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
                log.error("Error calculating RS for sector ETF {}: {}", symbol, e.getMessage());
            }
        }

        // Send consolidated alert if any crossovers detected
        if (!outperformingSignals.isEmpty() || !underperformingSignals.isEmpty()) {
            String alertMessage =
                    formatCrossoverAlertMessage(outperformingSignals, underperformingSignals);
            telegramClient.sendMessage(alertMessage);
            log.info(
                    "Sent sector RS crossover alert: {} outperforming, {} underperforming",
                    outperformingSignals.size(),
                    underperformingSignals.size());
        } else {
            log.info("No sector ETF RS crossovers detected");
        }

        // Persist RS data
        relativeStrengthService.saveRsHistory();
    }

    /**
     * Formats the crossover alert message for Telegram.
     *
     * @param outperforming List of sectors that crossed above their RS EMA
     * @param underperforming List of sectors that crossed below their RS EMA
     * @return Formatted Markdown message
     */
    protected String formatCrossoverAlertMessage(
            List<RelativeStrengthSignal> outperforming,
            List<RelativeStrengthSignal> underperforming) {

        StringBuilder sb = new StringBuilder();
        sb.append("📊 *SECTOR RS CROSSOVER ALERT*\n\n");

        if (!outperforming.isEmpty()) {
            sb.append("*🟢 NOW OUTPERFORMING SPY:*\n");
            for (RelativeStrengthSignal signal : outperforming) {
                sb.append(formatCrossoverSignalLine(signal));
            }
            sb.append("\n");
        }

        if (!underperforming.isEmpty()) {
            sb.append("*🔴 NOW UNDERPERFORMING SPY:*\n");
            for (RelativeStrengthSignal signal : underperforming) {
                sb.append(formatCrossoverSignalLine(signal));
            }
            sb.append("\n");
        }

        sb.append("_RS crossed 50-period EMA_");

        return sb.toString();
    }

    /**
     * Formats a single crossover signal line.
     *
     * @param signal The signal to format
     * @return Formatted line
     */
    private String formatCrossoverSignalLine(RelativeStrengthSignal signal) {
        return String.format(
                "• *%s* (%s): %+.1f%%%n",
                signal.displayName(), signal.symbol(), signal.percentageDiff());
    }

    /**
     * Generates and sends a daily summary of sector ETF relative strength vs SPY.
     *
     * <p>Sectors are sorted by their percentage deviation from the 50-period EMA, with
     * outperforming sectors listed first and underperforming sectors listed last. Each sector shows
     * its current streak count (consecutive days in current direction).
     */
    public void sendDailySectorRsSummary() {
        log.info("Generating daily sector RS summary");

        List<SectorRsData> sectorData = collectSectorRsData();

        if (sectorData.isEmpty()) {
            log.warn("No sector RS data available, skipping summary");
            return;
        }

        String message = formatSummaryMessage(sectorData);
        telegramClient.sendMessage(message);
        log.info("Sent sector RS summary with {} sectors", sectorData.size());
    }

    /**
     * Collects RS data for all sector ETFs, including streak information.
     *
     * @return List of sector RS data, sorted by percentage difference (descending)
     */
    protected List<SectorRsData> collectSectorRsData() {
        return collectRsDataForEtfs(symbolRegistry.getAllEtfs());
    }

    /**
     * Collects RS data for a given set of ETFs, including streak information.
     *
     * @param etfNames Map of symbol to display name
     * @return List of sector RS data, sorted by percentage difference (descending)
     */
    private List<SectorRsData> collectRsDataForEtfs(Map<String, String> etfNames) {
        List<SectorRsData> sectorData = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (Map.Entry<String, String> entry : etfNames.entrySet()) {
            String symbol = entry.getKey();
            String displayName = entry.getValue();

            try {
                Optional<RsResult> rsResult = relativeStrengthService.getCurrentRsResult(symbol);

                if (rsResult.isPresent()) {
                    RsResult result = rsResult.get();
                    double percentageDiff = ((result.rs() - result.ema()) / result.ema()) * 100;
                    boolean isOutperforming = percentageDiff >= 0;

                    // Update streak and get current streak days
                    SectorRsStreakPersistence.StreakUpdateResult streakResult =
                            streakPersistence.updateStreak(symbol, isOutperforming, today);
                    int streakDays = streakResult.newStreak().streakDays();

                    sectorData.add(
                            new SectorRsData(
                                    symbol,
                                    displayName,
                                    result.rs(),
                                    result.ema(),
                                    percentageDiff,
                                    result.dataPoints(),
                                    result.isComplete(),
                                    streakDays,
                                    streakResult.previousStreakDays(),
                                    streakResult.directionChanged()));
                } else {
                    log.debug("No RS data available for {}", symbol);
                }
            } catch (Exception e) {
                log.error("Error getting RS data for {}: {}", symbol, e.getMessage());
            }
        }

        // Sort by percentage difference descending (best performers first)
        sectorData.sort((a, b) -> Double.compare(b.percentageDiff(), a.percentageDiff()));

        return sectorData;
    }

    /**
     * Formats the summary message for Telegram.
     *
     * @param sectorData List of sector RS data, sorted by performance
     * @return Formatted Markdown message
     */
    protected String formatSummaryMessage(List<SectorRsData> sectorData) {
        // Split into broad sector and thematic ETFs
        Set<String> thematicSymbols = symbolRegistry.getThematicSymbols();
        List<SectorRsData> broadData =
                sectorData.stream().filter(s -> !thematicSymbols.contains(s.symbol())).toList();
        List<SectorRsData> thematicData =
                sectorData.stream().filter(s -> thematicSymbols.contains(s.symbol())).toList();

        StringBuilder sb = new StringBuilder();
        sb.append("*Sector ETF Relative Strength vs SPY*\n\n");

        // Format broad sectors
        formatSection(sb, broadData, "Sectors");

        // Format thematic ETFs
        if (!thematicData.isEmpty()) {
            formatSection(sb, thematicData, "Thematic / Industry");
        }

        sb.append("_RS = Sector/SPY ratio | % = deviation from 50-EMA | 📅 = streak days_");

        return sb.toString();
    }

    /**
     * Formats a section of the summary with outperforming/underperforming split.
     *
     * @param sb StringBuilder to append to
     * @param data RS data for this section
     * @param sectionTitle Title for this section
     */
    private void formatSection(StringBuilder sb, List<SectorRsData> data, String sectionTitle) {
        List<SectorRsData> outperforming =
                data.stream().filter(s -> s.percentageDiff() >= 0).toList();

        List<SectorRsData> underperforming =
                data.stream().filter(s -> s.percentageDiff() < 0).toList();

        if (!outperforming.isEmpty()) {
            sb.append("🟢 *").append(sectionTitle).append(" Outperforming SPY:*\n");
            for (int i = 0; i < outperforming.size(); i++) {
                SectorRsData sector = outperforming.get(i);
                sb.append(formatSectorLine(i + 1, sector));
            }
            sb.append("\n");
        }

        if (!underperforming.isEmpty()) {
            sb.append("🔴 *").append(sectionTitle).append(" Underperforming SPY:*\n");
            for (int i = 0; i < underperforming.size(); i++) {
                SectorRsData sector = underperforming.get(i);
                sb.append(formatSectorLine(outperforming.size() + i + 1, sector));
            }
            sb.append("\n");
        }
    }

    /**
     * Formats a single sector line for the summary with streak information.
     *
     * @param rank The rank number
     * @param sector The sector data
     * @return Formatted line with streak count
     */
    private String formatSectorLine(int rank, SectorRsData sector) {
        String streakIndicator = String.format("📅%d", sector.streakDays());

        String endedStreakInfo = "";
        if (sector.streakJustEnded() && sector.previousStreakDays() > 1) {
            String previousDirection =
                    sector.percentageDiff() >= 0 ? "underperforming" : "outperforming";
            endedStreakInfo =
                    String.format(
                            " 🔄 ended %d-day %s", sector.previousStreakDays(), previousDirection);
        }

        if (sector.isComplete()) {
            return String.format(
                    "%d. *%s*: %+.1f%% %s%s%n",
                    rank,
                    sector.displayName(),
                    sector.percentageDiff(),
                    streakIndicator,
                    endedStreakInfo);
        } else {
            return String.format(
                    "%d. *%s*: %+.1f%% %s%s (%d days)%n",
                    rank,
                    sector.displayName(),
                    sector.percentageDiff(),
                    streakIndicator,
                    endedStreakInfo,
                    sector.dataPoints());
        }
    }
}
