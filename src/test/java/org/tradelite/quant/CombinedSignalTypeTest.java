package org.tradelite.quant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CombinedSignalTypeTest {

    @Test
    void classify_bothPositive_returnsGreen() {
        assertThat(CombinedSignalType.classify(true, true)).isEqualTo(CombinedSignalType.GREEN);
    }

    @Test
    void classify_rsPositiveVfiNegative_returnsYellow() {
        assertThat(CombinedSignalType.classify(true, false)).isEqualTo(CombinedSignalType.YELLOW);
    }

    @Test
    void classify_rsNegativeVfiPositive_returnsYellow() {
        assertThat(CombinedSignalType.classify(false, true)).isEqualTo(CombinedSignalType.YELLOW);
    }

    @Test
    void classify_bothNegative_returnsRed() {
        assertThat(CombinedSignalType.classify(false, false)).isEqualTo(CombinedSignalType.RED);
    }
}
