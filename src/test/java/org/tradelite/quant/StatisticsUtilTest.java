package org.tradelite.quant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatisticsUtilTest {

    // ========== mean(List) ==========

    @Test
    void mean_returnsZeroForNullList() {
        assertThat(StatisticsUtil.mean(null)).isZero();
    }

    @Test
    void mean_returnsZeroForEmptyList() {
        assertThat(StatisticsUtil.mean(Collections.emptyList())).isZero();
    }

    @Test
    void mean_calculatesCorrectly() {
        List<Double> values = Arrays.asList(2.0, 4.0, 6.0, 8.0, 10.0);
        assertThat(StatisticsUtil.mean(values)).isCloseTo(6.0, within(0.001));
    }

    @Test
    void mean_singleValue() {
        assertThat(StatisticsUtil.mean(List.of(42.0))).isCloseTo(42.0, within(0.001));
    }

    // ========== mean(List, from, to) ==========

    @Test
    void meanSubrange_calculatesCorrectly() {
        List<Double> values = Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0);
        // Mean of [2.0, 3.0, 4.0] = 3.0
        assertThat(StatisticsUtil.mean(values, 1, 4)).isCloseTo(3.0, within(0.001));
    }

    @Test
    void meanSubrange_fullRange() {
        List<Double> values = Arrays.asList(10.0, 20.0, 30.0);
        assertThat(StatisticsUtil.mean(values, 0, 3)).isCloseTo(20.0, within(0.001));
    }

    // ========== populationStdDev(List, mean) ==========

    @Test
    void populationStdDev_returnsZeroForNull() {
        assertThat(StatisticsUtil.populationStdDev(null, 0)).isZero();
    }

    @Test
    void populationStdDev_returnsZeroForEmpty() {
        assertThat(StatisticsUtil.populationStdDev(Collections.emptyList(), 0)).isZero();
    }

    @Test
    void populationStdDev_returnsZeroForConstantValues() {
        List<Double> values = Arrays.asList(5.0, 5.0, 5.0, 5.0);
        assertThat(StatisticsUtil.populationStdDev(values, 5.0)).isZero();
    }

    @Test
    void populationStdDev_calculatesCorrectly() {
        // Values: 2, 4, 4, 4, 5, 5, 7, 9 -> mean = 5, population stddev = 2.0
        List<Double> values = Arrays.asList(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0);
        assertThat(StatisticsUtil.populationStdDev(values, 5.0)).isCloseTo(2.0, within(0.001));
    }

    // ========== populationStdDev(List, from, to, mean) ==========

    @Test
    void populationStdDevSubrange_calculatesCorrectly() {
        List<Double> values = Arrays.asList(1.0, 2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0, 99.0);
        // Subrange [2..9) = {4,4,4,5,5,7,9}, mean of subrange = 5.43...
        // Use indices 2..9
        double subMean = StatisticsUtil.mean(values, 2, 9);
        double stdDev = StatisticsUtil.populationStdDev(values, 2, 9, subMean);
        assertThat(stdDev).isGreaterThan(0);
    }

    // ========== sampleStdDev ==========

    @Test
    void sampleStdDev_returnsZeroForNull() {
        assertThat(StatisticsUtil.sampleStdDev(null, 0)).isZero();
    }

    @Test
    void sampleStdDev_returnsZeroForSingleValue() {
        assertThat(StatisticsUtil.sampleStdDev(List.of(5.0), 5.0)).isZero();
    }

    @Test
    void sampleStdDev_calculatesWithBesselsCorrection() {
        // Values: 2, 4, 4, 4, 5, 5, 7, 9 -> mean = 5
        // Population var = 4.0, Sample var = 4.0 * 8/7 = 4.571...
        // Sample stddev = sqrt(4.571...) ≈ 2.138
        List<Double> values = Arrays.asList(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0);
        double sampleStd = StatisticsUtil.sampleStdDev(values, 5.0);
        double populationStd = StatisticsUtil.populationStdDev(values, 5.0);
        // Sample stddev should be larger than population stddev
        assertThat(sampleStd).isGreaterThan(populationStd);
        assertThat(sampleStd).isCloseTo(2.138, within(0.01));
    }

    // ========== zScore ==========

    @Test
    void zScore_returnsZeroWhenStdDevIsZero() {
        assertThat(StatisticsUtil.zScore(10.0, 5.0, 0)).isZero();
    }

    @Test
    void zScore_calculatesCorrectly() {
        // z = (10 - 5) / 2.5 = 2.0
        assertThat(StatisticsUtil.zScore(10.0, 5.0, 2.5)).isCloseTo(2.0, within(0.001));
    }

    @Test
    void zScore_negativeResult() {
        // z = (3 - 5) / 2.5 = -0.8
        assertThat(StatisticsUtil.zScore(3.0, 5.0, 2.5)).isCloseTo(-0.8, within(0.001));
    }

    // ========== percentile ==========

    @Test
    void percentile_returnsDefaultForNull() {
        assertThat(StatisticsUtil.percentile(null, 5.0)).isEqualTo(50.0);
    }

    @Test
    void percentile_returnsDefaultForEmpty() {
        assertThat(StatisticsUtil.percentile(Collections.emptyList(), 5.0)).isEqualTo(50.0);
    }

    @Test
    void percentile_lowestValue() {
        List<Double> values = Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0);
        // 0 values below 1.0 → 0%
        assertThat(StatisticsUtil.percentile(values, 1.0)).isCloseTo(0.0, within(0.001));
    }

    @Test
    void percentile_highestValue() {
        List<Double> values = Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0);
        // All 5 values below 6.0 → 100%
        assertThat(StatisticsUtil.percentile(values, 6.0)).isCloseTo(100.0, within(0.001));
    }

    @Test
    void percentile_middleValue() {
        List<Double> values = Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0);
        // 2 values below 3.0 → 40%
        assertThat(StatisticsUtil.percentile(values, 3.0)).isCloseTo(40.0, within(0.001));
    }
}
