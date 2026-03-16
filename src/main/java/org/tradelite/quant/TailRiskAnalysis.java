package org.tradelite.quant;

/**
 * Result of tail risk analysis for a single symbol.
 *
 * <p>Contains kurtosis metrics and risk classification to help assess the probability of extreme
 * price moves.
 *
 * @param symbol The stock or ETF ticker symbol
 * @param displayName Human-readable name for the symbol
 * @param kurtosis The calculated kurtosis (normal distribution = 3.0)
 * @param excessKurtosis Kurtosis minus 3 (>0 means fatter tails than normal)
 * @param riskLevel Classification based on excess kurtosis
 * @param dataPoints Number of daily returns used in calculation
 */
public record TailRiskAnalysis(
        String symbol,
        String displayName,
        double kurtosis,
        double excessKurtosis,
        TailRiskLevel riskLevel,
        int dataPoints) {

    /**
     * Returns true if sufficient data was available for reliable kurtosis calculation. Kurtosis
     * requires at least 20 data points for meaningful calculation.
     */
    public boolean hasReliableData() {
        return dataPoints >= 20;
    }

    /**
     * Formats the analysis as a single-line summary for Telegram messages.
     *
     * @return Formatted string like "XLE Energy: 🟡 MODERATE (kurtosis=5.2)"
     */
    public String toSummaryLine() {
        return String.format(
                "%s %s: %s %s (kurtosis=%.1f)",
                symbol, displayName, riskLevel.getEmoji(), riskLevel.name(), kurtosis);
    }

    /**
     * Formats the analysis as a compact line showing only symbol and risk level.
     *
     * @return Formatted string like "🟡 XLE"
     */
    public String toCompactLine() {
        return String.format("%s %s", riskLevel.getEmoji(), symbol);
    }
}
