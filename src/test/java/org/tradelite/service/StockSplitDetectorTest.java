package org.tradelite.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tradelite.service.StockSplitDetector.SplitResult;
import org.tradelite.service.StockSplitDetector.SplitResult.Direction;

class StockSplitDetectorTest {

    private StockSplitDetector detector;

    @BeforeEach
    void setUp() {
        detector = new StockSplitDetector();
    }

    @Test
    void detectSplit_exactTwoToOneForwardSplit_returnsForwardTwo() {
        Optional<SplitResult> result = detector.detectSplit(200.0, 100.0);

        assertThat(result.isPresent(), is(true));
        assertThat(result.get().factor(), is(2));
        assertThat(result.get().direction(), is(Direction.FORWARD));
    }

    @Test
    void detectSplit_exactThreeToOneForwardSplit_returnsForwardThree() {
        Optional<SplitResult> result = detector.detectSplit(300.0, 100.0);

        assertThat(result.isPresent(), is(true));
        assertThat(result.get().factor(), is(3));
        assertThat(result.get().direction(), is(Direction.FORWARD));
    }

    @Test
    void detectSplit_exactTenToOneForwardSplit_returnsForwardTen() {
        Optional<SplitResult> result = detector.detectSplit(900.0, 90.0);

        assertThat(result.isPresent(), is(true));
        assertThat(result.get().factor(), is(10));
        assertThat(result.get().direction(), is(Direction.FORWARD));
    }

    @Test
    void detectSplit_exactFiftyToOneForwardSplit_returnsForwardFifty() {
        Optional<SplitResult> result = detector.detectSplit(5000.0, 100.0);

        assertThat(result.isPresent(), is(true));
        assertThat(result.get().factor(), is(50));
        assertThat(result.get().direction(), is(Direction.FORWARD));
    }

    @Test
    void detectSplit_twoToOneWithinTolerance_returnsForwardTwo() {
        // ratio = 200.0 / 103.0 ≈ 1.942, within 5% of 2
        Optional<SplitResult> result = detector.detectSplit(200.0, 103.0);

        assertThat(result.isPresent(), is(true));
        assertThat(result.get().factor(), is(2));
        assertThat(result.get().direction(), is(Direction.FORWARD));
    }

    @Test
    void detectSplit_twoToOneOutsideTolerance_returnsEmpty() {
        // ratio = 200.0 / 90.0 ≈ 2.22, more than 5% off from 2
        Optional<SplitResult> result = detector.detectSplit(200.0, 90.0);

        assertTrue(result.isEmpty());
    }

    @Test
    void detectSplit_normalPriceMovement_returnsEmpty() {
        // 2% daily change — not a split
        Optional<SplitResult> result = detector.detectSplit(100.0, 98.0);

        assertTrue(result.isEmpty());
    }

    @Test
    void detectSplit_largePriceMovementNoMatchingFactor_returnsEmpty() {
        // ratio ≈ 1.5 — doesn't match any common factor
        Optional<SplitResult> result = detector.detectSplit(150.0, 100.0);

        assertTrue(result.isEmpty());
    }

    @Test
    void detectSplit_oneToTwoReverseSplit_returnsReverseTwo() {
        Optional<SplitResult> result = detector.detectSplit(100.0, 200.0);

        assertThat(result.isPresent(), is(true));
        assertThat(result.get().factor(), is(2));
        assertThat(result.get().direction(), is(Direction.REVERSE));
    }

    @Test
    void detectSplit_oneToTenReverseSplit_returnsReverseTen() {
        Optional<SplitResult> result = detector.detectSplit(90.0, 900.0);

        assertThat(result.isPresent(), is(true));
        assertThat(result.get().factor(), is(10));
        assertThat(result.get().direction(), is(Direction.REVERSE));
    }

    @Test
    void detectSplit_zeroStoredClose_returnsEmpty() {
        assertTrue(detector.detectSplit(0.0, 100.0).isEmpty());
    }

    @Test
    void detectSplit_zeroFetchedClose_returnsEmpty() {
        assertTrue(detector.detectSplit(100.0, 0.0).isEmpty());
    }

    @Test
    void detectSplit_negativeStoredClose_returnsEmpty() {
        assertTrue(detector.detectSplit(-100.0, 50.0).isEmpty());
    }

    @Test
    void detectSplit_negativeFetchedClose_returnsEmpty() {
        assertTrue(detector.detectSplit(100.0, -50.0).isEmpty());
    }

    @Test
    void detectSplit_allCommonForwardFactors_detected() {
        for (int factor : StockSplitDetector.COMMON_SPLIT_FACTORS) {
            double stored = factor * 100.0;
            double fetched = 100.0;
            Optional<SplitResult> result = detector.detectSplit(stored, fetched);

            assertThat(
                    "Forward split factor " + factor + " should be detected",
                    result.isPresent(),
                    is(true));
            assertThat(result.get().factor(), is(factor));
            assertThat(result.get().direction(), is(Direction.FORWARD));
        }
    }

    @Test
    void detectSplit_allCommonReverseFactors_detected() {
        for (int factor : StockSplitDetector.COMMON_SPLIT_FACTORS) {
            double stored = 100.0;
            double fetched = factor * 100.0;
            Optional<SplitResult> result = detector.detectSplit(stored, fetched);

            assertThat(
                    "Reverse split factor " + factor + " should be detected",
                    result.isPresent(),
                    is(true));
            assertThat(result.get().factor(), is(factor));
            assertThat(result.get().direction(), is(Direction.REVERSE));
        }
    }
}
