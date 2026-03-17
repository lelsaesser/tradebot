package org.tradelite.quant;

/**
 * Result of tail risk analysis for a single symbol.
 *
 * <p>Contains kurtosis and skewness metrics with risk classification to help assess the probability
 * and direction of extreme price moves.
 *
 * <p>Interpretation:
 *
 * <ul>
 *   <li><b>Kurtosis</b>: Measures "tailedness" - higher values mean more extreme moves likely
 *   <li><b>Skewness</b>: Measures asymmetry - negative means crash risk, positive means rally
 *       potential
 * </ul>
 *
 * @param symbol The stock or ETF ticker symbol
 * @param displayName Human-readable name for the symbol
 * @param kurtosis The calculated kurtosis (normal distribution = 3.0)
 * @param excessKurtosis Kurtosis minus 3 (>0 means fatter tails than normal)
 * @param riskLevel Classification based on excess kurtosis
 * @param skewness The calculated skewness (0 = symmetric, negative = left tail, positive = right
 *     tail)
 * @param skewnessLevel Classification based on skewness value
 * @param dataPoints Number of daily returns used in calculation
 */
public record TailRiskAnalysis(
        String symbol,
        String displayName,
        double kurtosis,
        double excessKurtosis,
        TailRiskLevel riskLevel,
        double skewness,
        SkewnessLevel skewnessLevel,
        int dataPoints) {

    /**
     * Returns true if sufficient data was available for reliable kurtosis calculation. Kurtosis
     * requires at least 20 data points for meaningful calculation.
     */
    public boolean hasReliableData() {
        return dataPoints >= 20;
    }

    /**
     * Returns true if the analysis indicates elevated crash risk. This is when we have fat tails
     * (high kurtosis) combined with negative skewness.
     */
    public boolean hasCrashRisk() {
        return riskLevel.isElevated() && skewnessLevel.isNegative();
    }

    /**
     * Returns true if the analysis indicates rally potential. This is when we have fat tails (high
     * kurtosis) combined with positive skewness.
     */
    public boolean hasRallyPotential() {
        return riskLevel.isElevated() && skewnessLevel.isPositive();
    }

    /**
     * Formats the analysis as a single-line summary for Telegram messages.
     *
     * @return Formatted string like "• *Energy* (XLE): Kurtosis 5.2 | Skew -0.8 ⬇️"
     */
    public String toSummaryLine() {
        return String.format(
                "• *%s* (%s): Kurtosis %.1f | Skew %+.2f %s",
                displayName, symbol, kurtosis, skewness, skewnessLevel.getEmoji());
    }

    /**
     * Formats the analysis as a compact line showing only symbol and risk level.
     *
     * @return Formatted string like "🟡 XLE ⬇️"
     */
    public String toCompactLine() {
        return String.format("%s %s %s", riskLevel.getEmoji(), symbol, skewnessLevel.getEmoji());
    }

    /**
     * Returns a description of the combined risk interpretation.
     *
     * @return Human-readable risk interpretation
     */
    public String getRiskInterpretation() {
        if (!riskLevel.isElevated()) {
            return "Normal tail risk";
        }

        return switch (skewnessLevel) {
            case HIGHLY_NEGATIVE -> "Fat tails with strong crash bias";
            case NEGATIVE -> "Fat tails with moderate downside skew";
            case NEUTRAL -> "Fat tails with no directional bias - high uncertainty";
            case POSITIVE -> "Fat tails with moderate upside skew";
            case HIGHLY_POSITIVE -> "Fat tails with strong rally bias";
        };
    }
}
