package org.tradelite.quant;

import lombok.Getter;

/**
 * Classification of skewness levels for return distributions.
 *
 * <p>Skewness measures the asymmetry of a probability distribution:
 *
 * <ul>
 *   <li>Negative skew: Left tail is longer/fatter → more crash risk
 *   <li>Positive skew: Right tail is longer/fatter → more rally potential
 *   <li>Near-zero skew: Symmetric distribution → balanced risk
 * </ul>
 */
@Getter
public enum SkewnessLevel {

    /** Skewness < -1.0: Strong crash bias, left tail significantly fatter */
    HIGHLY_NEGATIVE("Highly Negative", "⬇️⬇️", "Strong crash bias"),

    /** Skewness -1.0 to -0.5: Moderate downside skew */
    NEGATIVE("Negative", "⬇️", "Moderate downside skew"),

    /** Skewness -0.5 to +0.5: Roughly symmetric distribution */
    NEUTRAL("Neutral", "↔️", "No directional bias"),

    /** Skewness +0.5 to +1.0: Moderate upside skew */
    POSITIVE("Positive", "⬆️", "Moderate upside skew"),

    /** Skewness > +1.0: Strong rally bias, right tail significantly fatter */
    HIGHLY_POSITIVE("Highly Positive", "⬆️⬆️", "Strong rally bias");

    private final String displayName;
    private final String emoji;
    private final String description;

    SkewnessLevel(String displayName, String emoji, String description) {
        this.displayName = displayName;
        this.emoji = emoji;
        this.description = description;
    }

    /**
     * Determines the skewness level from a skewness value.
     *
     * @param skewness The calculated skewness value
     * @return The corresponding SkewnessLevel
     */
    public static SkewnessLevel fromSkewness(double skewness) {
        if (skewness < -1.0) {
            return HIGHLY_NEGATIVE;
        } else if (skewness < -0.5) {
            return NEGATIVE;
        } else if (skewness <= 0.5) {
            return NEUTRAL;
        } else if (skewness <= 1.0) {
            return POSITIVE;
        } else {
            return HIGHLY_POSITIVE;
        }
    }

    /**
     * Returns true if the skewness indicates downside risk bias.
     *
     * @return true for HIGHLY_NEGATIVE and NEGATIVE levels
     */
    public boolean isNegative() {
        return this == HIGHLY_NEGATIVE || this == NEGATIVE;
    }

    /**
     * Returns true if the skewness indicates upside potential bias.
     *
     * @return true for HIGHLY_POSITIVE and POSITIVE levels
     */
    public boolean isPositive() {
        return this == HIGHLY_POSITIVE || this == POSITIVE;
    }
}
