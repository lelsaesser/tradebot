package org.tradelite.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TermPremiumLevelTest {

    @ParameterizedTest
    @CsvSource({
        // Deeply negative: p < -0.5
        "-1.5, DEEPLY_NEGATIVE",
        "-0.51, DEEPLY_NEGATIVE",
        // Negative: -0.5 <= p < 0
        "-0.5, NEGATIVE",
        "-0.25, NEGATIVE",
        "-0.01, NEGATIVE",
        // Normalizing: 0 <= p < 0.5
        "0.0, NORMALIZING",
        "0.25, NORMALIZING",
        "0.49, NORMALIZING",
        // Normal: 0.5 <= p < 1.0
        "0.5, NORMAL",
        "0.75, NORMAL",
        "0.99, NORMAL",
        // Elevated: p >= 1.0
        "1.0, ELEVATED",
        "2.0, ELEVATED",
    })
    void fromPremium_classifiesBoundariesCorrectly(double premium, TermPremiumLevel expected) {
        assertThat(TermPremiumLevel.fromPremium(premium)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "DEEPLY_NEGATIVE, 🔴",
        "NEGATIVE, 🟠",
        "NORMALIZING, 🟡",
        "NORMAL, 🟢",
        "ELEVATED, 🟠",
    })
    void emoji_matchesBand(TermPremiumLevel level, String expected) {
        assertThat(level.getEmoji()).isEqualTo(expected);
    }
}
