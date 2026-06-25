package org.tradelite.core;

import lombok.Getter;

/**
 * Classification of yield-curve spread values (in percentage points). Shared between {@code T10Y3M}
 * and {@code T10Y2Y} since the two spreads track each other closely and the academic framing uses
 * identical bucket boundaries.
 *
 * <p>The {@code INVERTED} and {@code DEEPLY_INVERTED} bands are the recession-warning signal: the
 * 10Y−3M spread inverting has preceded every US recession since 1955 with one false positive (NY
 * Fed recession-probability model uses exactly this series). Threshold boundaries are the
 * conventional macro-finance buckets.
 *
 * <p>{@code TreasuryTracker} alerts on transitions <em>into or out of</em> {@code INVERTED} /
 * {@code DEEPLY_INVERTED} only. Transitions among {@code FLAT}, {@code NORMAL}, {@code STEEP} are
 * routine yield-curve fluctuations and don't merit a separate alert.
 *
 * <p>Reference: <a href="https://www.newyorkfed.org/research/capital_markets/ycfaq">NY Fed Yield
 * Curve and Predicted GDP Growth</a>; Estrella & Mishkin (1996).
 */
@Getter
public enum YieldCurveSpreadLevel {
    /** Spread &lt; −0.5%. Deep inversion; recession probability &gt; 50% within 12 months. */
    DEEPLY_INVERTED("🔴"),

    /** −0.5% ≤ spread &lt; 0. Curve is inverted; elevated recession warning. */
    INVERTED("🟠"),

    /** 0 ≤ spread &lt; 0.5%. Curve is at or near inversion; caution zone. */
    FLAT("🟡"),

    /** 0.5% ≤ spread &lt; 1.5%. Healthy upward-sloping curve. */
    NORMAL("🟢"),

    /** Spread ≥ 1.5%. Strongly upward-sloping curve; expansion or sharp easing expectations. */
    STEEP("🟢");

    private final String emoji;

    YieldCurveSpreadLevel(String emoji) {
        this.emoji = emoji;
    }

    /**
     * Classify a yield-curve spread value (in percentage points) into the appropriate band.
     *
     * @param spread the spread value, e.g. {@code 0.65} for a 65 bps positive spread
     * @return the matching {@code YieldCurveSpreadLevel}
     */
    public static YieldCurveSpreadLevel fromSpread(double spread) {
        if (spread < -0.5) {
            return DEEPLY_INVERTED;
        } else if (spread < 0) {
            return INVERTED;
        } else if (spread < 0.5) {
            return FLAT;
        } else if (spread < 1.5) {
            return NORMAL;
        } else {
            return STEEP;
        }
    }

    /** Returns true if this band represents an inverted curve (alerting threshold). */
    public boolean isInverted() {
        return this == INVERTED || this == DEEPLY_INVERTED;
    }
}
