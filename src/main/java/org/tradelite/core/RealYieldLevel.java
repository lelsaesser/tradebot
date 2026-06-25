package org.tradelite.core;

import lombok.Getter;

/**
 * Classification of the 10-year Treasury real yield ({@code DFII10} / 10Y TIPS yield), in
 * percentage points. Report-only — no alerts are emitted on band transitions.
 *
 * <p>Thresholds are anchored to the NY Fed Holston-Laubach-Williams r* estimates (~0.7–1.0%
 * currently). Values at or below r* are accommodative; meaningfully above r* is restrictive
 * monetary policy. Calibration is for the post-GFC monetary regime; pre-2008 baselines were
 * structurally higher.
 *
 * <p>Reference: <a href="https://www.newyorkfed.org/research/policy/rstar">NY Fed HLW r* page</a>;
 * Holston, Laubach, Williams (2017) "Measuring the natural rate of interest: International trends
 * and determinants," JIE.
 */
@Getter
public enum RealYieldLevel {
    /** Yield &lt; 0. Cheap-money / QE-era regime; risk-asset tailwind. */
    DEEPLY_NEGATIVE("🟢"),

    /** 0 ≤ y &lt; 1%. Below current r* estimates; accommodative policy. */
    LOW("🟢"),

    /** 1% ≤ y &lt; 1.5%. Around r*; policy roughly neutral. */
    NEUTRAL("🟡"),

    /** 1.5% ≤ y &lt; 2.5%. Materially above r*; equity-multiple headwind. */
    RESTRICTIVE("🟠"),

    /** y ≥ 2.5%. Historically tight by post-GFC standards; sharp valuation pressure. */
    DEEPLY_RESTRICTIVE("🔴");

    private final String emoji;

    RealYieldLevel(String emoji) {
        this.emoji = emoji;
    }

    /**
     * Classify a 10-year real yield value (in percentage points) into the appropriate band.
     *
     * @param yield the yield value, e.g. {@code 2.28} for a 2.28% real yield
     * @return the matching {@code RealYieldLevel}
     */
    public static RealYieldLevel fromYield(double yield) {
        if (yield < 0) {
            return DEEPLY_NEGATIVE;
        } else if (yield < 1.0) {
            return LOW;
        } else if (yield < 1.5) {
            return NEUTRAL;
        } else if (yield < 2.5) {
            return RESTRICTIVE;
        } else {
            return DEEPLY_RESTRICTIVE;
        }
    }
}
