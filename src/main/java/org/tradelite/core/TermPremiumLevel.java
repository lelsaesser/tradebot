package org.tradelite.core;

import lombok.Getter;

/**
 * Classification of the 10-year term premium ({@code THREEFYTP10} via the NY Fed
 * Adrian-Crump-Moench ACM model), in percentage points. Term premium measures compensation
 * investors demand for holding long-duration debt over rolling short-duration. Report-only — no
 * alerts.
 *
 * <p>Thresholds are calibrated against the post-2008 ACM mean (~0.0–0.3%); pre-2008 the long-run
 * average was ~1.5–1.7%, but tradebot monitors the current monetary regime. Both extremes carry
 * meaning: deeply negative reflects QE / safe-haven distortion; elevated suggests inflation premium
 * / supply concerns.
 *
 * <p>Reference: <a href="https://www.newyorkfed.org/research/data_indicators/term-premia-tabs">NY
 * Fed ACM term-premium page</a>; Adrian, Crump, Moench (2013) "Pricing the Term Structure with
 * Linear Regressions," JFE.
 */
@Getter
public enum TermPremiumLevel {
    /** Premium &lt; −0.5%. Investors paying for duration; QE / safe-haven regime. */
    DEEPLY_NEGATIVE("🔴"),

    /** −0.5% ≤ p &lt; 0. Compressed term premium; 2014–2021 prevailing regime. */
    NEGATIVE("🟠"),

    /** 0 ≤ p &lt; 0.5%. Returning toward historical norm. */
    NORMALIZING("🟡"),

    /** 0.5% ≤ p &lt; 1.0%. Healthy term-risk compensation by post-GFC standards. */
    NORMAL("🟢"),

    /** p ≥ 1.0%. Stress / inflation premium / supply concerns. */
    ELEVATED("🟠");

    private final String emoji;

    TermPremiumLevel(String emoji) {
        this.emoji = emoji;
    }

    /**
     * Classify a 10-year term premium value (in percentage points) into the appropriate band.
     *
     * @param premium the term premium value, e.g. {@code 0.75} for 75 bps
     * @return the matching {@code TermPremiumLevel}
     */
    public static TermPremiumLevel fromPremium(double premium) {
        if (premium < -0.5) {
            return DEEPLY_NEGATIVE;
        } else if (premium < 0) {
            return NEGATIVE;
        } else if (premium < 0.5) {
            return NORMALIZING;
        } else if (premium < 1.0) {
            return NORMAL;
        } else {
            return ELEVATED;
        }
    }
}
