package org.tradelite.quant;

import lombok.Getter;

/**
 * Classification of tail risk levels based on excess kurtosis.
 *
 * <p>Kurtosis measures the "tailedness" of a distribution. Normal distribution has kurtosis = 3.
 * Excess kurtosis = kurtosis - 3, so values > 0 indicate fatter tails than normal.
 *
 * <p>Fat tails mean extreme price moves (crashes or rallies) occur more frequently than a normal
 * distribution would predict.
 */
@Getter
public enum TailRiskLevel {
    /** Near-normal distribution. Extreme moves are rare. Business as usual. */
    LOW("🟢"),

    /** Slightly fat tails. Elevated probability of larger-than-expected moves. */
    MODERATE("🟡"),

    /** Significant tail risk. High probability of extreme moves. Consider reducing exposure. */
    HIGH("🟠"),

    /**
     * Very fat tails. Crash or rally risk significantly elevated. Defensive posture recommended.
     */
    EXTREME("🔴");

    private final String emoji;

    TailRiskLevel(String emoji) {
        this.emoji = emoji;
    }

    /**
     * Determines tail risk level from excess kurtosis.
     *
     * @param excessKurtosis kurtosis - 3 (normal distribution baseline)
     * @return the corresponding risk level
     */
    public static TailRiskLevel fromExcessKurtosis(double excessKurtosis) {
        if (excessKurtosis < 1.0) {
            return LOW;
        } else if (excessKurtosis < 3.0) {
            return MODERATE;
        } else if (excessKurtosis < 6.0) {
            return HIGH;
        } else {
            return EXTREME;
        }
    }
}
