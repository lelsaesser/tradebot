package org.tradelite.core;

/**
 * Generates the human-readable "Reading" section of the daily Treasury macro report. Sentences are
 * generated deterministically from the same band classifications that drive the colored emojis, via
 * three small lookup methods:
 *
 * <ul>
 *   <li>{@link #compositeRegime} — the punchline; one regime descriptor based on {@code
 *       YieldCurveSpreadLevel × RealYieldLevel}. Rendered first in the report.
 *   <li>{@link #curveReading} — what the yield curve specifically is saying. Keyed off the primary
 *       spread (T10Y3M).
 *   <li>{@link #macroContextReading} — what the macro-context signals (DFII10, TP10) add on top.
 * </ul>
 *
 * <p>Two principles drove the wording:
 *
 * <ol>
 *   <li>Non-prescriptive — sentences describe what the bond market is signaling, not what the
 *       operator should do. No "buy / sell / reduce" language.
 *   <li>Deterministic — every reading derives from the locked band thresholds. No free-form
 *       analysis; new sentences require a code change and test update.
 * </ol>
 *
 * <p>Added in #516. See PR discussion for the literature anchors behind each phrasing.
 */
public final class TreasuryReportNarrative {

    private TreasuryReportNarrative() {}

    /**
     * The TL;DR regime label — rendered first in the *Reading* section. Once the curve flattens or
     * inverts, the curve dominates and {@code realYield} no longer changes the regime label.
     */
    public static String compositeRegime(YieldCurveSpreadLevel curve, RealYieldLevel realYield) {
        switch (curve) {
            case DEEPLY_INVERTED:
                return "Elevated recession risk per the NY Fed model.";
            case INVERTED:
                return "Recession warning regime — the bond market is pricing forced Fed cuts."
                        + " Historically a 6–18 month lead.";
            case FLAT:
                return "Late-cycle caution — the curve is signaling the end of the cycle is"
                        + " approaching, but no recession warning yet.";
            case NORMAL:
            case STEEP:
                return healthyCurveRegime(realYield);
        }
        throw new IllegalStateException("Unhandled YieldCurveSpreadLevel: " + curve);
    }

    private static String healthyCurveRegime(RealYieldLevel realYield) {
        switch (realYield) {
            case DEEPLY_NEGATIVE:
            case LOW:
                return "Early-cycle expansion — easy money and a healthy curve; historically the"
                        + " most bullish backdrop for risk assets.";
            case NEUTRAL:
                return "Mid-cycle expansion — balanced policy, healthy curve.";
            case RESTRICTIVE:
            case DEEPLY_RESTRICTIVE:
                return "Mid-to-late cycle expansion under tight monetary policy.";
        }
        throw new IllegalStateException("Unhandled RealYieldLevel: " + realYield);
    }

    /** What the yield curve specifically is saying (Layer 1). Keyed off the primary spread. */
    public static String curveReading(YieldCurveSpreadLevel curve) {
        switch (curve) {
            case STEEP:
                return "The yield curve is strongly upward-sloping — the bond market sees no"
                        + " near-term recession risk and is pricing future growth.";
            case NORMAL:
                return "The yield curve is normal and the bond market sees no near-term recession"
                        + " risk.";
            case FLAT:
                return "The yield curve has flattened — a late-cycle warning, though not yet an"
                        + " inversion signal.";
            case INVERTED:
                return "The yield curve is inverted — the bond market is pricing future Fed rate"
                        + " cuts, historically a 6–18 month recession-warning signal.";
            case DEEPLY_INVERTED:
                return "The yield curve is deeply inverted — high-conviction recession warning"
                        + " per the NY Fed model.";
        }
        throw new IllegalStateException("Unhandled YieldCurveSpreadLevel: " + curve);
    }

    /**
     * What the macro-context signals (DFII10 real yield + THREEFYTP10 term premium) add on top
     * (Layer 2). DFII10 drives the primary phrasing; TP10 modifies it when ELEVATED.
     */
    public static String macroContextReading(
            RealYieldLevel realYield, TermPremiumLevel termPremium) {
        switch (realYield) {
            case DEEPLY_NEGATIVE:
                return "Real yields are deeply negative — a cheap-money regime that's historically"
                        + " been highly favorable for risk assets.";
            case LOW:
                return "Real yields are accommodative, below the natural rate.";
            case NEUTRAL:
                return "Real yields sit around the natural rate; monetary policy is roughly"
                        + " balanced.";
            case RESTRICTIVE:
                if (termPremium == TermPremiumLevel.ELEVATED) {
                    return "Real yields are restrictive AND term premium is elevated, indicating"
                            + " bond-market stress around supply or inflation.";
                }
                return "Real yields are restrictive — a multi-quarter headwind on equity multiples,"
                        + " but not a recession signal in isolation.";
            case DEEPLY_RESTRICTIVE:
                return "Real yields are deeply restrictive — sharp valuation pressure on growth"
                        + " assets.";
        }
        throw new IllegalStateException("Unhandled RealYieldLevel: " + realYield);
    }
}
