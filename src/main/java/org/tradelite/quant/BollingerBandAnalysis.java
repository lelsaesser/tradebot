package org.tradelite.quant;

import java.util.List;

/**
 * Result of Bollinger Band analysis for a single symbol.
 *
 * <p>Contains the full Bollinger Band state and any detected signals.
 *
 * <p>Key metrics:
 *
 * <ul>
 *   <li><b>%B</b>: Position within bands (0.0 = lower band, 0.5 = SMA, 1.0 = upper band)
 *   <li><b>Bandwidth</b>: (Upper - Lower) / SMA — measures volatility spread
 *   <li><b>Bandwidth Percentile</b>: Current bandwidth rank vs. recent history (0-100)
 * </ul>
 *
 * @param symbol The stock or ETF ticker symbol
 * @param displayName Human-readable name for the symbol
 * @param currentPrice Latest closing price
 * @param sma Simple moving average (middle band)
 * @param upperBand Upper Bollinger Band (SMA + 2σ)
 * @param lowerBand Lower Bollinger Band (SMA - 2σ)
 * @param percentB Position within bands (0.0 = lower, 1.0 = upper)
 * @param bandwidth Normalized bandwidth: (upper - lower) / SMA
 * @param bandwidthPercentile Current bandwidth percentile vs. recent history (0-100)
 * @param signals Detected signal types (may be empty if no actionable signals)
 * @param dataPoints Number of daily prices used in calculation
 */
public record BollingerBandAnalysis(
        String symbol,
        String displayName,
        double currentPrice,
        double sma,
        double upperBand,
        double lowerBand,
        double percentB,
        double bandwidth,
        double bandwidthPercentile,
        List<BollingerSignalType> signals,
        int dataPoints) {

    /** Minimum data points needed for a 20-day SMA calculation. */
    public static final int MIN_DATA_POINTS = 20;

    /**
     * Minimum data points needed for reliable bandwidth percentile history (20 SMA + 20 history).
     */
    public static final int BANDWIDTH_HISTORY_MIN_DATA_POINTS = 40;

    /** Absolute bandwidth threshold below which a squeeze is detected (4% of SMA). */
    public static final double SQUEEZE_BANDWIDTH_THRESHOLD = 0.04;

    /** Bandwidth percentile threshold below which a historical squeeze is detected. */
    public static final double SQUEEZE_PERCENTILE_THRESHOLD = 10.0;

    /** Returns true if sufficient data was available for basic Bollinger Band calculation. */
    public boolean hasReliableData() {
        return dataPoints >= MIN_DATA_POINTS;
    }

    /** Returns true if sufficient data was available for bandwidth percentile history. */
    public boolean hasBandwidthHistory() {
        return dataPoints >= BANDWIDTH_HISTORY_MIN_DATA_POINTS;
    }

    /** Returns true if the price is at or above the upper band. */
    public boolean isOverextended() {
        return signals.contains(BollingerSignalType.UPPER_BAND_TOUCH);
    }

    /** Returns true if the price is at or below the lower band. */
    public boolean isUnderextended() {
        return signals.contains(BollingerSignalType.LOWER_BAND_TOUCH);
    }

    /** Returns true if a volatility squeeze has been detected (absolute bandwidth). */
    public boolean isSqueeze() {
        return signals.contains(BollingerSignalType.SQUEEZE);
    }

    /** Returns true if a historical squeeze has been detected (bandwidth percentile). */
    public boolean isHistoricalSqueeze() {
        return signals.contains(BollingerSignalType.HISTORICAL_SQUEEZE);
    }

    /** Returns true if any actionable signals were detected. */
    public boolean hasSignals() {
        return !signals.isEmpty();
    }

    /**
     * Formats the analysis as a single-line summary for Telegram messages.
     *
     * @return Formatted string like "• *Energy* (XLE): %B 1.05 | BW 4.2% (P15) ⬆️"
     */
    public String toSummaryLine() {
        String signalEmojis =
                signals.isEmpty()
                        ? "✅"
                        : signals.stream()
                                .map(BollingerSignalType::getEmoji)
                                .reduce("", (a, b) -> a + b);

        return String.format(
                "• *%s* (%s): %%B %.2f | BW %.1f%% (P%.0f) %s",
                displayName, symbol, percentB, bandwidth * 100, bandwidthPercentile, signalEmojis);
    }

    /**
     * Formats the analysis as a compact line showing only symbol and signal state.
     *
     * @return Formatted string like "⬆️ XLE" or "✅ XLE"
     */
    public String toCompactLine() {
        String signalEmojis =
                signals.isEmpty()
                        ? "✅"
                        : signals.stream()
                                .map(BollingerSignalType::getEmoji)
                                .reduce("", (a, b) -> a + b);

        return String.format("%s %s", signalEmojis, symbol);
    }
}
