package org.tradelite.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class TreasuryReportNarrativeTest {

    // ========== compositeRegime ==========

    @Test
    void compositeRegime_deeplyInverted_returnsElevatedRecessionRisk() {
        for (RealYieldLevel y : RealYieldLevel.values()) {
            String regime =
                    TreasuryReportNarrative.compositeRegime(
                            YieldCurveSpreadLevel.DEEPLY_INVERTED, y);
            assertThat(regime).isEqualTo("Elevated recession risk per the NY Fed model.");
        }
    }

    @Test
    void compositeRegime_inverted_returnsRecessionWarning() {
        for (RealYieldLevel y : RealYieldLevel.values()) {
            String regime =
                    TreasuryReportNarrative.compositeRegime(YieldCurveSpreadLevel.INVERTED, y);
            assertThat(regime).contains("Recession warning regime").contains("6–18 month");
        }
    }

    @Test
    void compositeRegime_flat_returnsLateCycleCaution() {
        for (RealYieldLevel y : RealYieldLevel.values()) {
            String regime = TreasuryReportNarrative.compositeRegime(YieldCurveSpreadLevel.FLAT, y);
            assertThat(regime).contains("Late-cycle caution");
        }
    }

    @ParameterizedTest
    @CsvSource({
        "NORMAL, DEEPLY_NEGATIVE",
        "NORMAL, LOW",
        "STEEP, DEEPLY_NEGATIVE",
        "STEEP, LOW",
    })
    void compositeRegime_healthyCurveAndEasyMoney_returnsEarlyCycle(
            YieldCurveSpreadLevel curve, RealYieldLevel realYield) {
        String regime = TreasuryReportNarrative.compositeRegime(curve, realYield);
        assertThat(regime)
                .contains("Early-cycle expansion")
                .contains("most bullish backdrop for risk assets");
    }

    @ParameterizedTest
    @CsvSource({"NORMAL, NEUTRAL", "STEEP, NEUTRAL"})
    void compositeRegime_healthyCurveAndNeutralYields_returnsMidCycle(
            YieldCurveSpreadLevel curve, RealYieldLevel realYield) {
        String regime = TreasuryReportNarrative.compositeRegime(curve, realYield);
        assertThat(regime).isEqualTo("Mid-cycle expansion — balanced policy, healthy curve.");
    }

    @ParameterizedTest
    @CsvSource({
        "NORMAL, RESTRICTIVE",
        "NORMAL, DEEPLY_RESTRICTIVE",
        "STEEP, RESTRICTIVE",
        "STEEP, DEEPLY_RESTRICTIVE",
    })
    void compositeRegime_healthyCurveAndRestrictiveYields_returnsMidToLateCycle(
            YieldCurveSpreadLevel curve, RealYieldLevel realYield) {
        String regime = TreasuryReportNarrative.compositeRegime(curve, realYield);
        assertThat(regime).isEqualTo("Mid-to-late cycle expansion under tight monetary policy.");
    }

    // ========== curveReading ==========

    @ParameterizedTest
    @EnumSource(YieldCurveSpreadLevel.class)
    void curveReading_returnsNonEmptySentenceForAllBands(YieldCurveSpreadLevel band) {
        String sentence = TreasuryReportNarrative.curveReading(band);
        assertThat(sentence).isNotBlank();
        // All curve readings should mention "curve" for readability.
        assertThat(sentence).containsIgnoringCase("curve");
    }

    @Test
    void curveReading_steep_mentionsUpwardSlopingAndFutureGrowth() {
        String sentence = TreasuryReportNarrative.curveReading(YieldCurveSpreadLevel.STEEP);
        assertThat(sentence).contains("strongly upward-sloping").contains("future growth");
    }

    @Test
    void curveReading_normal_mentionsNoRecessionRisk() {
        String sentence = TreasuryReportNarrative.curveReading(YieldCurveSpreadLevel.NORMAL);
        assertThat(sentence)
                .isEqualTo(
                        "The yield curve is normal and the bond market sees no near-term"
                                + " recession risk.");
    }

    @Test
    void curveReading_flat_mentionsLateCycleWarning() {
        String sentence = TreasuryReportNarrative.curveReading(YieldCurveSpreadLevel.FLAT);
        assertThat(sentence).contains("flattened").contains("late-cycle warning");
    }

    @Test
    void curveReading_inverted_mentionsFedCutsAndLeadTime() {
        String sentence = TreasuryReportNarrative.curveReading(YieldCurveSpreadLevel.INVERTED);
        assertThat(sentence)
                .contains("inverted")
                .contains("future Fed rate cuts")
                .contains("6–18 month");
    }

    @Test
    void curveReading_deeplyInverted_mentionsHighConviction() {
        String sentence =
                TreasuryReportNarrative.curveReading(YieldCurveSpreadLevel.DEEPLY_INVERTED);
        assertThat(sentence).contains("deeply inverted").contains("high-conviction");
    }

    // ========== macroContextReading ==========

    @ParameterizedTest
    @EnumSource(TermPremiumLevel.class)
    void macroContextReading_deeplyNegative_cheapMoneyRegimeRegardlessOfTermPremium(
            TermPremiumLevel tp) {
        String sentence =
                TreasuryReportNarrative.macroContextReading(RealYieldLevel.DEEPLY_NEGATIVE, tp);
        assertThat(sentence)
                .contains("deeply negative")
                .contains("cheap-money regime")
                .contains("highly favorable for risk assets");
    }

    @ParameterizedTest
    @EnumSource(TermPremiumLevel.class)
    void macroContextReading_low_accommodativeBelowNaturalRate(TermPremiumLevel tp) {
        String sentence = TreasuryReportNarrative.macroContextReading(RealYieldLevel.LOW, tp);
        assertThat(sentence).contains("accommodative").contains("below the natural rate");
    }

    @ParameterizedTest
    @EnumSource(TermPremiumLevel.class)
    void macroContextReading_neutral_aroundNaturalRate(TermPremiumLevel tp) {
        String sentence = TreasuryReportNarrative.macroContextReading(RealYieldLevel.NEUTRAL, tp);
        assertThat(sentence).contains("around the natural rate").contains("roughly balanced");
    }

    @Test
    void macroContextReading_restrictiveAndTpNotElevated_multiQuarterHeadwind() {
        for (TermPremiumLevel tp : TermPremiumLevel.values()) {
            if (tp == TermPremiumLevel.ELEVATED) {
                continue;
            }
            String sentence =
                    TreasuryReportNarrative.macroContextReading(RealYieldLevel.RESTRICTIVE, tp);
            assertThat(sentence)
                    .contains("restrictive")
                    .contains("multi-quarter headwind on equity multiples")
                    .doesNotContain("AND term premium is elevated");
        }
    }

    @Test
    void macroContextReading_restrictiveAndTpElevated_bondMarketStress() {
        String sentence =
                TreasuryReportNarrative.macroContextReading(
                        RealYieldLevel.RESTRICTIVE, TermPremiumLevel.ELEVATED);
        assertThat(sentence)
                .contains("Real yields are restrictive AND term premium is elevated")
                .contains("bond-market stress around supply or inflation");
    }

    @ParameterizedTest
    @EnumSource(TermPremiumLevel.class)
    void macroContextReading_deeplyRestrictive_sharpValuationPressure(TermPremiumLevel tp) {
        String sentence =
                TreasuryReportNarrative.macroContextReading(RealYieldLevel.DEEPLY_RESTRICTIVE, tp);
        assertThat(sentence)
                .contains("deeply restrictive")
                .contains("sharp valuation pressure on growth assets");
    }

    // ========== Sanity: every band combination produces SOME sentence ==========

    @Test
    void compositeRegime_everyBandCombination_returnsNonBlankSentence() {
        for (YieldCurveSpreadLevel c : YieldCurveSpreadLevel.values()) {
            for (RealYieldLevel y : RealYieldLevel.values()) {
                assertThat(TreasuryReportNarrative.compositeRegime(c, y))
                        .as("curve=%s yield=%s", c, y)
                        .isNotBlank();
            }
        }
    }

    @Test
    void macroContextReading_everyBandCombination_returnsNonBlankSentence() {
        for (RealYieldLevel y : RealYieldLevel.values()) {
            for (TermPremiumLevel tp : TermPremiumLevel.values()) {
                assertThat(TreasuryReportNarrative.macroContextReading(y, tp))
                        .as("yield=%s tp=%s", y, tp)
                        .isNotBlank();
            }
        }
    }
}
