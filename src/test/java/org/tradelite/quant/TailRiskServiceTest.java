package org.tradelite.quant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.service.DailyPriceProvider;
import org.tradelite.service.model.DailyPrice;

@ExtendWith(MockitoExtension.class)
class TailRiskServiceTest {

    /**
     * Window used by most data-shape tests. {@code minDataPoints=20} matches the size of the
     * hand-rolled return series (20–25 points) used throughout this file. The fetch window is
     * generous enough to cover all test inputs without rejection by the new size guard.
     */
    private static final TailRiskWindow TEST_WINDOW = new TailRiskWindow(40, 20);

    @Mock private DailyPriceProvider dailyPriceProvider;

    private TailRiskService tailRiskService;

    @BeforeEach
    void setUp() {
        tailRiskService = new TailRiskService(dailyPriceProvider);
    }

    @Test
    void analyzeTailRisk_returnsEmptyWhenInsufficientData() {
        // Provider returns minDataPoints - 1 prices → guard returns empty.
        TailRiskWindow window = new TailRiskWindow(50, 25);
        List<DailyPrice> prices =
                changesToPrices(generateNormalReturns(window.minDataPoints() - 2));
        when(dailyPriceProvider.findDailyClosingPrices("SPY", window.lookbackCalendarDays()))
                .thenReturn(prices);

        Optional<TailRiskAnalysis> result =
                tailRiskService.analyzeTailRisk("SPY", "S&P 500", window);

        assertThat(result).isEmpty();
    }

    @Test
    void analyzeTailRisk_succeedsAtBoundaryWhenExactlyEnoughData() {
        // Provider returns exactly minDataPoints prices → analysis succeeds with minDataPoints-1
        // returns.
        TailRiskWindow window = new TailRiskWindow(50, 25);
        List<DailyPrice> prices =
                changesToPrices(generateNormalReturns(window.minDataPoints() - 1));
        assertThat(prices).hasSize(window.minDataPoints());
        when(dailyPriceProvider.findDailyClosingPrices("SPY", window.lookbackCalendarDays()))
                .thenReturn(prices);

        Optional<TailRiskAnalysis> result =
                tailRiskService.analyzeTailRisk("SPY", "S&P 500", window);

        assertThat(result).isPresent();
        assertThat(result.get().dataPoints()).isEqualTo(window.minDataPoints() - 1);
    }

    /**
     * Regression test for #472. The bug: caller passed a single value used as both the calendar
     * fetch argument and the minimum row threshold. A 35-calendar-day window only yields ~24
     * trading-day rows, so {@code rows < 35} always rejected. After the fix, {@code
     * lookbackCalendarDays} (fetch) and {@code minDataPoints} (validation) are independent — the
     * provider returning fewer rows than the calendar window is normal and must be accepted as long
     * as the row count meets {@code minDataPoints}.
     */
    @Test
    void analyzeTailRisk_acceptsRowCountBelowCalendarLookback_regressionForIssue472() {
        // Window: fetch 50 calendar days, require 25 rows. Provider returns 30 rows (typical for
        // a 50-calendar-day query: 50 × 5/7 ≈ 35, minus holidays ≈ 30). 30 ≥ 25 → must accept.
        TailRiskWindow window = new TailRiskWindow(50, 25);
        List<DailyPrice> prices = changesToPrices(generateNormalReturns(29));
        assertThat(prices).hasSize(30);
        assertThat(prices.size()).isLessThan(window.lookbackCalendarDays());
        assertThat(prices.size()).isGreaterThanOrEqualTo(window.minDataPoints());
        when(dailyPriceProvider.findDailyClosingPrices("SPY", window.lookbackCalendarDays()))
                .thenReturn(prices);

        Optional<TailRiskAnalysis> result =
                tailRiskService.analyzeTailRisk("SPY", "S&P 500", window);

        assertThat(result).isPresent();
        // Sample is truncated to exactly minDataPoints rows → minDataPoints-1 returns.
        assertThat(result.get().dataPoints()).isEqualTo(window.minDataPoints() - 1);
    }

    @Test
    void analyzeTailRisk_truncatesToMinDataPointsForStableSampleSize() {
        // Provider returns more rows than minDataPoints → analysis must use exactly minDataPoints
        // (the latest ones). This keeps the kurtosis/skewness sample size stable across runs.
        TailRiskWindow window = new TailRiskWindow(400, 25);
        List<DailyPrice> prices = changesToPrices(generateNormalReturns(150));
        when(dailyPriceProvider.findDailyClosingPrices("SPY", window.lookbackCalendarDays()))
                .thenReturn(prices);

        Optional<TailRiskAnalysis> result =
                tailRiskService.analyzeTailRisk("SPY", "S&P 500", window);

        assertThat(result).isPresent();
        // Sample is exactly minDataPoints rows → minDataPoints-1 returns regardless of input size.
        assertThat(result.get().dataPoints()).isEqualTo(window.minDataPoints() - 1);
    }

    @Test
    void analyzeTailRisk_usesCalendarLookbackWhenQueryingProvider() {
        TailRiskWindow window = new TailRiskWindow(400, 252);
        List<DailyPrice> prices =
                changesToPrices(generateNormalReturns(window.minDataPoints() - 1));
        when(dailyPriceProvider.findDailyClosingPrices("SPY", window.lookbackCalendarDays()))
                .thenReturn(prices);

        tailRiskService.analyzeTailRisk("SPY", "S&P 500", window);

        verify(dailyPriceProvider).findDailyClosingPrices("SPY", window.lookbackCalendarDays());
    }

    @Test
    void analyzeTailRisk_returnsAnalysisWithSufficientData() {
        List<DailyPrice> prices =
                changesToPrices(generateNormalReturns(TEST_WINDOW.minDataPoints() - 1));
        when(dailyPriceProvider.findDailyClosingPrices("XLK", TEST_WINDOW.lookbackCalendarDays()))
                .thenReturn(prices);

        Optional<TailRiskAnalysis> result =
                tailRiskService.analyzeTailRisk("XLK", "Technology", TEST_WINDOW);

        assertThat(result).isPresent();
        TailRiskAnalysis analysis = result.get();
        assertThat(analysis.symbol()).isEqualTo("XLK");
        assertThat(analysis.displayName()).isEqualTo("Technology");
        assertThat(analysis.dataPoints()).isEqualTo(TEST_WINDOW.minDataPoints() - 1);
    }

    @Test
    void analyzeTailRisk_classifiesNormalDistributionAsLowRisk() {
        List<Double> normalReturns =
                Arrays.asList(
                        -2.0, -1.5, -1.0, -0.5, 0.0, 0.5, 1.0, 1.5, 2.0, -1.8, 1.8, -0.8, 0.8, -1.2,
                        1.2, -0.3, 0.3, -0.1, 0.1);
        List<DailyPrice> prices = changesToPrices(normalReturns);
        when(dailyPriceProvider.findDailyClosingPrices("SPY", TEST_WINDOW.lookbackCalendarDays()))
                .thenReturn(prices);

        Optional<TailRiskAnalysis> result =
                tailRiskService.analyzeTailRisk("SPY", "S&P 500", TEST_WINDOW);

        assertThat(result).isPresent();
        assertThat(result.get().riskLevel()).isEqualTo(TailRiskLevel.LOW);
    }

    @Test
    void analyzeTailRisk_classifiesFatTailsAsHighRisk() {
        List<Double> fatTailReturns =
                Arrays.asList(
                        0.1, 0.2, -0.1, 0.0, 0.1, -0.2, 0.1, 0.0, -0.1, 0.2, 0.1, -0.1, 0.0, 0.1,
                        -0.1, 0.0, 0.1, -0.2, 8.0, -7.0);
        List<DailyPrice> prices = changesToPrices(fatTailReturns);
        TailRiskWindow window = new TailRiskWindow(40, prices.size());
        when(dailyPriceProvider.findDailyClosingPrices("XLE", window.lookbackCalendarDays()))
                .thenReturn(prices);

        Optional<TailRiskAnalysis> result =
                tailRiskService.analyzeTailRisk("XLE", "Energy", window);

        assertThat(result).isPresent();
        TailRiskLevel level = result.get().riskLevel();
        assertThat(level).isIn(TailRiskLevel.HIGH, TailRiskLevel.EXTREME);
    }

    @Test
    void calculateKurtosis_returnsNormalKurtosisForUniformData() {
        List<Double> uniformReturns = Arrays.asList(1.0, 1.0, 1.0, 1.0, 1.0);

        double kurtosis = tailRiskService.calculateKurtosis(uniformReturns);

        assertThat(kurtosis).isEqualTo(3.0);
    }

    @Test
    void calculateKurtosis_returnsHigherValueForFatTailData() {
        List<Double> fatTailData =
                Arrays.asList(
                        0.1, 0.2, -0.1, 0.0, 0.1, -0.2, 0.1, 0.0, -0.1, 0.2, 0.1, -0.1, 0.0, 0.1,
                        -0.1, 0.0, 0.1, -0.2, 10.0, -10.0);

        double kurtosis = tailRiskService.calculateKurtosis(fatTailData);

        assertThat(kurtosis).isGreaterThan(3.0);
    }

    @Test
    void calculateKurtosis_returnsDefaultForInsufficientData() {
        List<Double> tooFewValues = Arrays.asList(1.0, 2.0, 3.0);

        double kurtosis = tailRiskService.calculateKurtosis(tooFewValues);

        assertThat(kurtosis).isEqualTo(3.0);
    }

    @Test
    void calculateExcessKurtosis_returnsCorrectValue() {
        List<Double> data = Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0);

        double excessKurtosis = tailRiskService.calculateExcessKurtosis(data);
        double kurtosis = tailRiskService.calculateKurtosis(data);

        assertThat(excessKurtosis).isCloseTo(kurtosis - 3.0, within(0.001));
    }

    // ========== Skewness Tests ==========

    @Test
    void calculateSkewness_returnsZeroForSymmetricData() {
        List<Double> symmetricData =
                Arrays.asList(
                        -3.0, -2.0, -1.0, 0.0, 1.0, 2.0, 3.0, -3.0, -2.0, -1.0, 0.0, 1.0, 2.0, 3.0,
                        -3.0, -2.0, -1.0, 0.0, 1.0, 2.0);

        double skewness = tailRiskService.calculateSkewness(symmetricData);

        assertThat(skewness).isCloseTo(0.0, within(0.1));
    }

    @Test
    void calculateSkewness_returnsNegativeForLeftSkewedData() {
        List<Double> leftSkewedData =
                Arrays.asList(
                        0.5, 0.3, 0.4, 0.2, 0.1, 0.3, 0.4, 0.2, 0.5, 0.3, 0.4, 0.2, 0.1, 0.3, 0.4,
                        0.2, -5.0, -6.0, -4.5, 0.1);

        double skewness = tailRiskService.calculateSkewness(leftSkewedData);

        assertThat(skewness).isLessThan(0);
    }

    @Test
    void calculateSkewness_returnsPositiveForRightSkewedData() {
        List<Double> rightSkewedData =
                Arrays.asList(
                        -0.5, -0.3, -0.4, -0.2, -0.1, -0.3, -0.4, -0.2, -0.5, -0.3, -0.4, -0.2,
                        -0.1, -0.3, -0.4, -0.2, 5.0, 6.0, 4.5, -0.1);

        double skewness = tailRiskService.calculateSkewness(rightSkewedData);

        assertThat(skewness).isGreaterThan(0);
    }

    @Test
    void calculateSkewness_returnsZeroForInsufficientData() {
        List<Double> tooFewValues = Arrays.asList(1.0, 2.0);

        double skewness = tailRiskService.calculateSkewness(tooFewValues);

        assertThat(skewness).isEqualTo(0.0);
    }

    @Test
    void calculateSkewness_returnsZeroForZeroVariance() {
        List<Double> constantValues = Arrays.asList(5.0, 5.0, 5.0, 5.0, 5.0);

        double skewness = tailRiskService.calculateSkewness(constantValues);

        assertThat(skewness).isEqualTo(0.0);
    }

    @Test
    void analyzeTailRisk_includesSkewnessInResult() {
        List<DailyPrice> prices =
                changesToPrices(generateNormalReturns(TEST_WINDOW.minDataPoints() - 1));
        when(dailyPriceProvider.findDailyClosingPrices("XLK", TEST_WINDOW.lookbackCalendarDays()))
                .thenReturn(prices);

        Optional<TailRiskAnalysis> result =
                tailRiskService.analyzeTailRisk("XLK", "Technology", TEST_WINDOW);

        assertThat(result).isPresent();
        TailRiskAnalysis analysis = result.get();
        assertThat(analysis.skewnessLevel()).isNotNull();
        assertThat(analysis.skewness()).isBetween(-5.0, 5.0);
    }

    @Test
    void analyzeTailRisk_classifiesNegativeSkewnessCorrectly() {
        List<Double> leftSkewedData =
                Arrays.asList(
                        0.5, 0.3, 0.4, 0.2, 0.1, 0.3, 0.4, 0.2, 0.5, 0.3, 0.4, 0.2, 0.1, 0.3, 0.4,
                        0.2, -5.0, -6.0, -4.5, 0.1);
        List<DailyPrice> prices = changesToPrices(leftSkewedData);
        TailRiskWindow window = new TailRiskWindow(40, prices.size());
        when(dailyPriceProvider.findDailyClosingPrices("XLE", window.lookbackCalendarDays()))
                .thenReturn(prices);

        Optional<TailRiskAnalysis> result =
                tailRiskService.analyzeTailRisk("XLE", "Energy", window);

        assertThat(result).isPresent();
        assertThat(result.get().skewness()).isLessThan(0);
        assertThat(result.get().skewnessLevel().isNegative()).isTrue();
    }

    @Test
    void analyzeTailRisk_classifiesPositiveSkewnessCorrectly() {
        List<Double> rightSkewedData =
                Arrays.asList(
                        -0.5, -0.3, -0.4, -0.2, -0.1, -0.3, -0.4, -0.2, -0.5, -0.3, -0.4, -0.2,
                        -0.1, -0.3, -0.4, -0.2, 5.0, 6.0, 4.5, -0.1);
        List<DailyPrice> prices = changesToPrices(rightSkewedData);
        TailRiskWindow window = new TailRiskWindow(40, prices.size());
        when(dailyPriceProvider.findDailyClosingPrices("XLF", window.lookbackCalendarDays()))
                .thenReturn(prices);

        Optional<TailRiskAnalysis> result =
                tailRiskService.analyzeTailRisk("XLF", "Financials", window);

        assertThat(result).isPresent();
        assertThat(result.get().skewness()).isGreaterThan(0);
        assertThat(result.get().skewnessLevel().isPositive()).isTrue();
    }

    @Test
    void tailRiskAnalysis_formatsSummaryLineCorrectly() {
        List<DailyPrice> prices =
                changesToPrices(generateNormalReturns(TEST_WINDOW.minDataPoints() - 1));
        when(dailyPriceProvider.findDailyClosingPrices("XLI", TEST_WINDOW.lookbackCalendarDays()))
                .thenReturn(prices);

        Optional<TailRiskAnalysis> result =
                tailRiskService.analyzeTailRisk("XLI", "Industrials", TEST_WINDOW);

        assertThat(result).isPresent();
        String summaryLine = result.get().toSummaryLine();
        assertThat(summaryLine).contains("XLI").contains("Industrials").contains("Kurtosis");
    }

    @Test
    void tailRiskAnalysis_formatsCompactLineCorrectly() {
        List<DailyPrice> prices =
                changesToPrices(generateNormalReturns(TEST_WINDOW.minDataPoints() - 1));
        when(dailyPriceProvider.findDailyClosingPrices("XLV", TEST_WINDOW.lookbackCalendarDays()))
                .thenReturn(prices);

        Optional<TailRiskAnalysis> result =
                tailRiskService.analyzeTailRisk("XLV", "Healthcare", TEST_WINDOW);

        assertThat(result).isPresent();
        String compactLine = result.get().toCompactLine();
        assertThat(compactLine).contains("XLV");
    }

    // ========== Price-to-change-percent conversion tests ==========

    @Test
    void toDailyChangePercents_computesCorrectValues() {
        List<DailyPrice> prices = toDailyPrices(100.0, 102.0, 101.0, 105.05);

        List<Double> changePercents = tailRiskService.toDailyChangePercents(prices);

        assertThat(changePercents).hasSize(3);
        assertThat(changePercents.get(0)).isCloseTo(2.0, within(0.001));
        assertThat(changePercents.get(1)).isCloseTo(-0.9804, within(0.001));
        assertThat(changePercents.get(2)).isCloseTo(4.0099, within(0.001));
    }

    @Test
    void toDailyChangePercents_returnsEmptyForSinglePrice() {
        List<DailyPrice> prices = toDailyPrices(100.0);

        List<Double> changePercents = tailRiskService.toDailyChangePercents(prices);

        assertThat(changePercents).isEmpty();
    }

    @Test
    void toDailyChangePercents_returnsEmptyForEmptyList() {
        List<Double> changePercents = tailRiskService.toDailyChangePercents(List.of());

        assertThat(changePercents).isEmpty();
    }

    @Test
    void toDailyChangePercents_skipsZeroPreviousPrice() {
        List<DailyPrice> prices = toDailyPrices(0.0, 100.0, 102.0);

        List<Double> changePercents = tailRiskService.toDailyChangePercents(prices);

        // First change (0 → 100) skipped, only 100 → 102 remains
        assertThat(changePercents).hasSize(1);
        assertThat(changePercents.getFirst()).isCloseTo(2.0, within(0.001));
    }

    // ========== Helpers ==========

    private List<Double> generateNormalReturns(int count) {
        Double[] returns = new Double[count];
        for (int i = 0; i < count; i++) {
            returns[i] = (i % 2 == 0 ? 1 : -1) * (0.1 + (i * 0.1) % 1.5);
        }
        return Arrays.asList(returns);
    }

    /** Creates a DailyPrice list from raw closing prices, with sequential dates. */
    private List<DailyPrice> toDailyPrices(double... closingPrices) {
        List<DailyPrice> prices = new ArrayList<>();
        LocalDate date = LocalDate.of(2025, 1, 1);
        for (double price : closingPrices) {
            prices.add(new DailyPrice(date, price));
            date = date.plusDays(1);
        }
        return prices;
    }

    /**
     * Converts a list of daily change percents into a DailyPrice list by building a price series
     * starting at 100.0. This allows reusing existing change-percent test data.
     */
    private List<DailyPrice> changesToPrices(List<Double> changePercents) {
        List<DailyPrice> prices = new ArrayList<>();
        LocalDate date = LocalDate.of(2025, 1, 1);
        double price = 100.0;
        prices.add(new DailyPrice(date, price));
        for (Double changePct : changePercents) {
            date = date.plusDays(1);
            price = price * (1.0 + changePct / 100.0);
            prices.add(new DailyPrice(date, price));
        }
        return prices;
    }
}
