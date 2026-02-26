package org.tradelite.core;

import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.service.RelativeStrengthService;
import org.tradelite.service.RelativeStrengthService.RsResult;

/**
 * Tracks and reports daily relative strength of sector ETFs vs SPY benchmark.
 *
 * <p>This component generates a daily summary showing all 11 SPDR sector ETFs ranked by their
 * relative strength compared to SPY. The RS value is the ratio of sector price to SPY price, and
 * the percentage shown is the deviation from the 50-period EMA of that ratio.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SectorRelativeStrengthTracker {

    /** Sector ETF symbols with their display names */
    private static final Map<String, String> SECTOR_ETF_NAMES =
            Map.ofEntries(
                    Map.entry("XLK", "Technology"),
                    Map.entry("XLF", "Financials"),
                    Map.entry("XLE", "Energy"),
                    Map.entry("XLV", "Health Care"),
                    Map.entry("XLY", "Cons. Discretionary"),
                    Map.entry("XLP", "Cons. Staples"),
                    Map.entry("XLI", "Industrials"),
                    Map.entry("XLC", "Communication"),
                    Map.entry("XLRE", "Real Estate"),
                    Map.entry("XLB", "Materials"),
                    Map.entry("XLU", "Utilities"));

    private final RelativeStrengthService relativeStrengthService;
    private final TelegramClient telegramClient;

    /** Represents a sector's RS data for sorting and display */
    record SectorRsData(
            String symbol,
            String displayName,
            double rsValue,
            double emaValue,
            double percentageDiff,
            int dataPoints,
            boolean isComplete) {}

    /**
     * Generates and sends a daily summary of sector ETF relative strength vs SPY.
     *
     * <p>Sectors are sorted by their percentage deviation from the 50-period EMA, with
     * outperforming sectors listed first and underperforming sectors listed last.
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
     * Collects RS data for all sector ETFs.
     *
     * @return List of sector RS data, sorted by percentage difference (descending)
     */
    protected List<SectorRsData> collectSectorRsData() {
        List<SectorRsData> sectorData = new ArrayList<>();

        for (Map.Entry<String, String> entry : SECTOR_ETF_NAMES.entrySet()) {
            String symbol = entry.getKey();
            String displayName = entry.getValue();

            try {
                Optional<RsResult> rsResult = relativeStrengthService.getCurrentRsResult(symbol);

                if (rsResult.isPresent()) {
                    RsResult result = rsResult.get();
                    double percentageDiff = ((result.rs() - result.ema()) / result.ema()) * 100;

                    sectorData.add(
                            new SectorRsData(
                                    symbol,
                                    displayName,
                                    result.rs(),
                                    result.ema(),
                                    percentageDiff,
                                    result.dataPoints(),
                                    result.isComplete()));
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
        StringBuilder sb = new StringBuilder();
        sb.append("📊 *SECTOR ETF RELATIVE STRENGTH vs SPY*\n\n");

        List<SectorRsData> outperforming =
                sectorData.stream().filter(s -> s.percentageDiff() >= 0).toList();

        List<SectorRsData> underperforming =
                sectorData.stream().filter(s -> s.percentageDiff() < 0).toList();

        if (!outperforming.isEmpty()) {
            sb.append("🟢 *OUTPERFORMING SPY:*\n");
            for (int i = 0; i < outperforming.size(); i++) {
                SectorRsData sector = outperforming.get(i);
                sb.append(formatSectorLine(i + 1, sector));
            }
            sb.append("\n");
        }

        if (!underperforming.isEmpty()) {
            sb.append("🔴 *UNDERPERFORMING SPY:*\n");
            for (int i = 0; i < underperforming.size(); i++) {
                SectorRsData sector = underperforming.get(i);
                sb.append(formatSectorLine(outperforming.size() + i + 1, sector));
            }
            sb.append("\n");
        }

        sb.append("_RS = Sector/SPY ratio | % = deviation from 50-EMA_");

        return sb.toString();
    }

    /**
     * Formats a single sector line for the summary.
     *
     * @param rank The rank number
     * @param sector The sector data
     * @return Formatted line
     */
    private String formatSectorLine(int rank, SectorRsData sector) {
        if (sector.isComplete()) {
            return String.format(
                    "%d. *%s*: %+.1f%%%n", rank, sector.displayName(), sector.percentageDiff());
        } else {
            return String.format(
                    "%d. *%s*: %+.1f%% (%d days)%n",
                    rank, sector.displayName(), sector.percentageDiff(), sector.dataPoints());
        }
    }
}
