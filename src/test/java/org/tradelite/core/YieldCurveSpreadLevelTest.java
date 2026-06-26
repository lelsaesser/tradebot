package org.tradelite.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class YieldCurveSpreadLevelTest {

    @ParameterizedTest
    @CsvSource({
        // Deeply inverted: spread < -0.5
        "-2.0, DEEPLY_INVERTED",
        "-0.51, DEEPLY_INVERTED",
        // Inverted: -0.5 <= spread < 0
        "-0.5, INVERTED",
        "-0.25, INVERTED",
        "-0.01, INVERTED",
        // Flat: 0 <= spread < 0.5
        "0.0, FLAT",
        "0.25, FLAT",
        "0.49, FLAT",
        // Normal: 0.5 <= spread < 1.5
        "0.5, NORMAL",
        "1.0, NORMAL",
        "1.49, NORMAL",
        // Steep: spread >= 1.5
        "1.5, STEEP",
        "3.0, STEEP",
    })
    void fromSpread_classifiesBoundariesCorrectly(double spread, YieldCurveSpreadLevel expected) {
        assertThat(YieldCurveSpreadLevel.fromSpread(spread)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "DEEPLY_INVERTED, true",
        "INVERTED, true",
        "FLAT, false",
        "NORMAL, false",
        "STEEP, false",
    })
    void isInverted_returnsTrueOnlyForInvertedBands(YieldCurveSpreadLevel level, boolean expected) {
        assertThat(level.isInverted()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "DEEPLY_INVERTED, 🔴",
        "INVERTED, 🟠",
        "FLAT, 🟡",
        "NORMAL, 🟢",
        "STEEP, 🟢",
    })
    void emoji_matchesBand(YieldCurveSpreadLevel level, String expected) {
        assertThat(level.getEmoji()).isEqualTo(expected);
    }
}
