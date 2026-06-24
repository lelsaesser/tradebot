package org.tradelite.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RealYieldLevelTest {

    @ParameterizedTest
    @CsvSource({
        // Deeply negative: yield < 0
        "-1.5, DEEPLY_NEGATIVE",
        "-0.01, DEEPLY_NEGATIVE",
        // Low: 0 <= y < 1.0
        "0.0, LOW",
        "0.5, LOW",
        "0.99, LOW",
        // Neutral: 1.0 <= y < 1.5
        "1.0, NEUTRAL",
        "1.25, NEUTRAL",
        "1.49, NEUTRAL",
        // Restrictive: 1.5 <= y < 2.5
        "1.5, RESTRICTIVE",
        "2.0, RESTRICTIVE",
        "2.28, RESTRICTIVE",
        "2.49, RESTRICTIVE",
        // Deeply restrictive: y >= 2.5
        "2.5, DEEPLY_RESTRICTIVE",
        "4.0, DEEPLY_RESTRICTIVE",
    })
    void fromYield_classifiesBoundariesCorrectly(double yield, RealYieldLevel expected) {
        assertThat(RealYieldLevel.fromYield(yield)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "DEEPLY_NEGATIVE, 🔴",
        "LOW, 🟡",
        "NEUTRAL, 🟢",
        "RESTRICTIVE, 🟠",
        "DEEPLY_RESTRICTIVE, 🔴",
    })
    void emoji_matchesBand(RealYieldLevel level, String expected) {
        assertThat(level.getEmoji()).isEqualTo(expected);
    }
}
